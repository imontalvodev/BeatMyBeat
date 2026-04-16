package com.imontalvodev.beatmybeat.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.notifications.BeatMyBeatNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper

/**
 * Servicio de reproducción basado en Media3 ExoPlayer.
 *
 * Expone un [Binder] para que [PlayerScreen] acceda al [ExoPlayer] directamente.
 * Esto garantiza que player.seekTo(ms) sea una llamada síncrona al reproductor,
 * sin pasar por el sistema de intents (que era la causa del bug de seek).
 */
class PlaybackService : Service() {

    inner class LocalBinder : Binder() {
        val service: PlaybackService get() = this@PlaybackService
    }

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val currentTitle: String = "",
        val currentArtist: String = "",
    )

    private val binder = LocalBinder()
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSession

    /** Referencia pública al player para seek directo desde la UI. */
    val player: ExoPlayer get() = exoPlayer

    override fun onCreate() {
        super.onCreate()
        BeatMyBeatNotification.ensureChannels(this)

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, exoPlayer).build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = pushState()
            override fun onPlaybackStateChanged(state: Int) = pushState()
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                pushState()
                updateNotification()
            }
            override fun onPositionDiscontinuity(
                old: Player.PositionInfo,
                new: Player.PositionInfo,
                reason: Int,
            ) = pushState()
        })

        // Tick de posición mientras reproduce (solo actualiza posición, no causa recomposición total)
        android.os.Handler(mainLooper).also { h ->
            val tick = object : Runnable {
                override fun run() {
                    if (exoPlayer.isPlaying) pushState()
                    h.postDelayed(this, 200)
                }
            }
            h.post(tick)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> exoPlayer.play()
            ACTION_PAUSE -> exoPlayer.pause()
            ACTION_NEXT -> exoPlayer.seekToNextMediaItem()
            ACTION_PREV -> exoPlayer.seekToPreviousMediaItem()
            ACTION_STOP -> {
                exoPlayer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_SHUFFLE -> {
                exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled
                updateNotification()
            }
            ACTION_CYCLE_REPEAT -> {
                exoPlayer.repeatMode = when (exoPlayer.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                updateNotification()
            }
        }
        pushState()
        return START_STICKY
    }

    /**
     * Carga la cola y empieza a reproducir.
     * Llamado directamente desde la UI tras ligar el servicio.
     */
    fun loadQueue(queueJson: String, startIndex: Int) {
        val items = parseQueue(queueJson)
        if (items.isEmpty()) return
        exoPlayer.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        exoPlayer.prepare()
        exoPlayer.play()
        startForeground(BeatMyBeatNotification.PLAYBACK_NOTIFICATION_ID, buildNotification())
        pushState()
    }

    /**
     * Seek directo. Sin intents, sin delays, sin posibilidad de que Android lo descarte.
     * Preserva el estado play/pause: si estaba reproduciendo, sigue reproduciendo.
     */
    fun seekTo(positionMs: Long) {
        val dur = exoPlayer.duration.let { if (it == C.TIME_UNSET) 0L else it }
        if (dur <= 0L) return
        val safe = positionMs.coerceIn(0L, (dur - 500L).coerceAtLeast(0L))
        android.util.Log.d("BeatMyBeatSeek", "seekTo target=${positionMs}ms safe=${safe}ms dur=${dur}ms")
        val wasPlaying = exoPlayer.isPlaying || exoPlayer.playWhenReady
        exoPlayer.seekTo(safe)
        // Garantizar que sigue reproduciendo si lo estaba antes del seek.
        if (wasPlaying) exoPlayer.play()
        pushState()
    }

    private fun pushState() {
        val dur = exoPlayer.duration.let { if (it == C.TIME_UNSET) 0L else it }
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val meta = exoPlayer.currentMediaItem?.mediaMetadata
        _state.value = PlaybackState(
            isPlaying = exoPlayer.isPlaying,
            positionMs = pos,
            durationMs = dur,
            currentTitle = meta?.title?.toString() ?: "",
            currentArtist = meta?.artist?.toString() ?: "",
        )
    }

    private fun updateNotification() {
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        nm.notify(BeatMyBeatNotification.PLAYBACK_NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val meta = exoPlayer.currentMediaItem?.mediaMetadata
        val title = meta?.title?.toString()?.ifBlank { "BeatMyBeat" } ?: "BeatMyBeat"
        val artist = meta?.artist?.toString() ?: ""
        val isPlaying = exoPlayer.isPlaying

        val piFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        fun pi(action: String, reqCode: Int) = android.app.PendingIntent.getService(
            this, reqCode,
            Intent(this, PlaybackService::class.java).setAction(action), piFlags,
        )

        return NotificationCompat.Builder(this, "beatmybeat_playback")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Prev", pi(ACTION_PREV, 1))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                pi(if (isPlaying) ACTION_PAUSE else ACTION_PLAY, 2),
            )
            .addAction(android.R.drawable.ic_media_next, "Next", pi(ACTION_NEXT, 3))
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun parseQueue(json: String): List<MediaItem> {
        if (json.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o: JSONObject = arr.optJSONObject(i) ?: return@mapNotNull null
            val uri = o.optString("uri", "")
            if (uri.isBlank()) return@mapNotNull null
            MediaItem.Builder()
                .setUri(android.net.Uri.parse(uri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(o.optString("title", ""))
                        .setArtist(o.optString("artist", ""))
                        .build(),
                )
                .build()
        }
    }

    override fun onDestroy() {
        mediaSession.release()
        exoPlayer.release()
        _state.value = PlaybackState()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY           = "com.imontalvodev.beatmybeat.action.PLAY"
        const val ACTION_PAUSE          = "com.imontalvodev.beatmybeat.action.PAUSE"
        const val ACTION_NEXT           = "com.imontalvodev.beatmybeat.action.NEXT"
        const val ACTION_PREV           = "com.imontalvodev.beatmybeat.action.PREV"
        const val ACTION_STOP           = "com.imontalvodev.beatmybeat.action.STOP"
        const val ACTION_TOGGLE_SHUFFLE = "com.imontalvodev.beatmybeat.action.TOGGLE_SHUFFLE"
        const val ACTION_CYCLE_REPEAT   = "com.imontalvodev.beatmybeat.action.CYCLE_REPEAT"

        private val _state = MutableStateFlow(PlaybackState())
        val state: StateFlow<PlaybackState> = _state.asStateFlow()
    }
}
