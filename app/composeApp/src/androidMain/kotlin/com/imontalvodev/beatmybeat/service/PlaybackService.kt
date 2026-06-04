@file:OptIn(UnstableApi::class)

package com.imontalvodev.beatmybeat.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.imontalvodev.beatmybeat.MainActivity
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.core.Logger
import com.imontalvodev.beatmybeat.notifications.BeatMyBeatNotification
import com.imontalvodev.beatmybeat.ui.network.BitmapDecoding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.OptIn
import androidx.annotation.OptIn as AndroidOptIn
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper

/**
 * Servicio de reproducción basado en Media3 ExoPlayer.
 *
 * Expone un [Binder] para que la UI del reproductor acceda al [ExoPlayer] directamente.
 * Esto garantiza que player.seekTo(ms) sea una llamada síncrona al reproductor,
 * sin pasar por el sistema de intents (que era la causa del bug de seek).
 */
@AndroidOptIn(markerClass = [UnstableApi::class])
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
        val currentMediaId: String = "",
    )

    private val binder = LocalBinder()
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val mainHandler = Handler(Looper.getMainLooper())
    private val artworkExecutor = Executors.newSingleThreadExecutor()
    private var notificationArtwork: Bitmap? = null
    private var notificationArtworkMediaId: String? = null
    private var appLogoBitmap: Bitmap? = null
    private var lastStatePushElapsed = 0L

    private val positionTick = object : Runnable {
        override fun run() {
            if (!::exoPlayer.isInitialized) return
            if (exoPlayer.isPlaying) {
                pushState(force = false)
                mainHandler.postDelayed(this, POSITION_TICK_MS)
            }
        }
    }

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
        exoPlayer.setWakeMode(C.WAKE_MODE_LOCAL)

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(buildContentIntent())
            .build()

        artworkExecutor.execute { appLogoBitmap() }

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    mainHandler.post(positionTick)
                } else {
                    mainHandler.removeCallbacks(positionTick)
                }
                pushState(force = true)
            }

            override fun onPlaybackStateChanged(state: Int) = pushState(force = true)

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                pushState(force = true)
                recycleNotificationArtwork()
                updateNotification()
                scheduleNotificationArtwork()
            }

            override fun onPositionDiscontinuity(
                old: Player.PositionInfo,
                new: Player.PositionInfo,
                reason: Int,
            ) = pushState(force = true)
        })
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Notificación persistente si ya hay cola (p. ej. acciones desde segundo plano / notificación).
        if (exoPlayer.mediaItemCount > 0) {
            promoteToForegroundIfAllowed()
        }
        when (intent?.action) {
            ACTION_PLAY -> exoPlayer.play()
            ACTION_PAUSE -> exoPlayer.pause()
            ACTION_NEXT -> skipToNextSafe()
            ACTION_PREV -> skipToPreviousSafe()
            ACTION_STOP -> {
                exoPlayer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_SHUFFLE -> {
                // Shuffle lo resuelve la UI con cola JSON preordenada; no activar shuffle de Media3.
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
        pushState(force = true)
        return START_STICKY
    }

    private fun skipToNextSafe() {
        if (exoPlayer.mediaItemCount <= 0) return
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            return
        }
        // Evitar bloqueo al final de lista cuando repeat está OFF.
        exoPlayer.seekToDefaultPosition(0)
        if (exoPlayer.playWhenReady || exoPlayer.isPlaying) exoPlayer.play()
    }

    private fun skipToPreviousSafe() {
        if (exoPlayer.mediaItemCount <= 0) return
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
            return
        }
        // Si estamos en la primera, volvemos a la última para navegación circular.
        exoPlayer.seekToDefaultPosition(exoPlayer.mediaItemCount - 1)
        if (exoPlayer.playWhenReady || exoPlayer.isPlaying) exoPlayer.play()
    }

    /**
     * Carga la cola y empieza a reproducir.
     * Llamado directamente desde la UI tras ligar el servicio.
     */
    /**
     * @param shuffleEnabled ignorado: la cola JSON ya llega en el orden definitivo (incluido “shuffle” de la UI).
     * Activar [Player.shuffleModeEnabled] encima de esa cola hace que Media3 reordene de nuevo y rompe
     * [Player.hasNextMediaItem] / avance desde la notificación o el reproductor del sistema.
     */
    fun loadQueue(
        queueJson: String,
        startIndex: Int,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true,
        @Suppress("UNUSED_PARAMETER") shuffleEnabled: Boolean = false,
    ) {
        val items = parseQueue(queueJson)
        if (items.isEmpty()) return
        exoPlayer.shuffleModeEnabled = false
        val idx = startIndex.coerceIn(0, items.lastIndex)
        exoPlayer.setMediaItems(items, idx, startPositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = autoPlay
        if (autoPlay) {
            exoPlayer.play()
            promoteToForegroundIfAllowed()
        }
        pushState(force = true)
        scheduleNotificationArtwork()
    }

    /**
     * Reemplaza los ítems pendientes (después del índice actual) con la nueva cola.
     * La canción en curso no se interrumpe. Permite que la notificación (Next/Prev)
     * navegue por la misma cola que ve la UI.
     *
     * Usa replaceMediaItems() — operación atómica de Media3 que evita N remove + N add
     * en bucle sobre el hilo principal (causa de ANR con listas grandes).
     */
    fun syncNextItems(queueJson: String) {
        val newItems = parseQueue(queueJson)
        val total = exoPlayer.mediaItemCount
        // Sin cola en ExoPlayer (p. ej. usuario cambia de pestaña antes de reproducir):
        // replaceMediaItems(current+1, 0, …) viola fromIndex <= toIndex y lanza IllegalArgumentException.
        if (total == 0) return
        val rawIdx = exoPlayer.currentMediaItemIndex
        val currentIdx = if (rawIdx in 0 until total) rawIdx else 0
        val fromIndex = currentIdx + 1
        if (fromIndex > total) return
        exoPlayer.replaceMediaItems(
            /* fromIndex = */ fromIndex,
            /* toIndex   = */ total,
            /* mediaItems= */ newItems,
        )
        updateNotification()
    }

    /** Carátula solo para la notificación (no se duplica en cada MediaItem de la cola). */
    private fun scheduleNotificationArtwork() {
        val item = exoPlayer.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        val mediaId = item.mediaId
        val uriStr = uri.toString()
        recycleNotificationArtwork()
        artworkExecutor.execute {
            val bytes = PlaybackArtworkHelper.resolveArtworkBytes(this@PlaybackService, uriStr)
            mainHandler.post {
                if (exoPlayer.currentMediaItem?.mediaId != mediaId) return@post
                if (bytes == null || bytes.isEmpty()) {
                    recycleNotificationArtwork()
                    updateNotification()
                    return@post
                }
                val bitmap = BitmapDecoding.decodeSampled(bytes, NOTIFICATION_ARTWORK_PX)
                if (bitmap == null) {
                    recycleNotificationArtwork()
                    updateNotification()
                    return@post
                }
                notificationArtwork = bitmap
                notificationArtworkMediaId = mediaId
                updateNotification()
            }
        }
    }

    private fun recycleNotificationArtwork() {
        notificationArtwork?.recycle()
        notificationArtwork = null
        notificationArtworkMediaId = null
    }

    private fun appLogoBitmap(): Bitmap? {
        val cached = appLogoBitmap
        if (cached != null && !cached.isRecycled) return cached
        val decoded = BitmapDecoding.decodeResource(
            resources,
            R.drawable.logo,
            NOTIFICATION_ARTWORK_PX,
        )
        appLogoBitmap = decoded
        return decoded
    }

    private fun recycleAppLogoBitmap() {
        appLogoBitmap?.recycle()
        appLogoBitmap = null
    }

    /**
     * Seek directo. Sin intents, sin delays, sin posibilidad de que Android lo descarte.
     * Preserva el estado play/pause: si estaba reproduciendo, sigue reproduciendo.
     */
    fun seekTo(positionMs: Long) {
        val dur = exoPlayer.duration.let { if (it == C.TIME_UNSET) 0L else it }
        if (dur <= 0L) return
        val safe = positionMs.coerceIn(0L, (dur - 500L).coerceAtLeast(0L))
        Logger.d("BeatMyBeatSeek", "seekTo target=${positionMs}ms safe=${safe}ms dur=${dur}ms")
        val wasPlaying = exoPlayer.isPlaying || exoPlayer.playWhenReady
        exoPlayer.seekTo(safe)
        // Garantizar que sigue reproduciendo si lo estaba antes del seek.
        if (wasPlaying) exoPlayer.play()
        pushState(force = true)
    }

    private fun pushState(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && exoPlayer.isPlaying) {
            if (now - lastStatePushElapsed < STATE_PUSH_INTERVAL_MS) return
        }
        lastStatePushElapsed = now
        val dur = exoPlayer.duration.let { if (it == C.TIME_UNSET) 0L else it }
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val meta = exoPlayer.currentMediaItem?.mediaMetadata
        _state.value = PlaybackState(
            isPlaying = exoPlayer.isPlaying,
            positionMs = pos,
            durationMs = dur,
            currentTitle = meta?.title?.toString() ?: "",
            currentArtist = meta?.artist?.toString() ?: "",
            currentMediaId = exoPlayer.currentMediaItem?.mediaId ?: "",
        )
    }

    @SuppressLint("MissingPermission")
    private fun promoteToForegroundIfAllowed() {
        if (!BeatMyBeatNotification.canPostNotifications(this)) return
        runCatching {
            startForeground(BeatMyBeatNotification.PLAYBACK_NOTIFICATION_ID, buildNotification())
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        if (!BeatMyBeatNotification.canPostNotifications(this)) return
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(this)
                .notify(BeatMyBeatNotification.PLAYBACK_NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val meta = exoPlayer.currentMediaItem?.mediaMetadata
        val title = meta?.title?.toString()?.ifBlank { "BeatMyBeat" } ?: "BeatMyBeat"
        val artist = meta?.artist?.toString() ?: ""
        val isPlaying = exoPlayer.isPlaying

        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        fun pi(action: String, reqCode: Int): PendingIntent {
            val i = Intent(this, PlaybackService::class.java).setAction(action)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(this, reqCode, i, piFlags)
            } else {
                PendingIntent.getService(this, reqCode, i, piFlags)
            }
        }

        val builder = NotificationCompat.Builder(this, "beatmybeat_playback")
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(buildContentIntent())
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

        val albumArt = notificationArtwork?.takeIf { !it.isRecycled }
        val largeIcon = albumArt ?: appLogoBitmap()
        if (largeIcon != null && !largeIcon.isRecycled) {
            builder.setLargeIcon(largeIcon)
        }

        return builder.build()
    }

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 1000, intent, flags)
    }

    private fun parseQueue(json: String): List<MediaItem> {
        if (json.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o: JSONObject = arr.optJSONObject(i) ?: return@mapNotNull null
            val uri = o.optString("uri", "")
            if (uri.isBlank()) return@mapNotNull null
            MediaItem.Builder()
                .setMediaId(o.optString("id", ""))
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
        mainHandler.removeCallbacks(positionTick)
        artworkExecutor.shutdownNow()
        recycleNotificationArtwork()
        recycleAppLogoBitmap()
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

        private const val POSITION_TICK_MS = 500L
        private const val STATE_PUSH_INTERVAL_MS = 500L
        private const val NOTIFICATION_ARTWORK_PX = 256

        private val _state = MutableStateFlow(PlaybackState())
        val state: StateFlow<PlaybackState> = _state.asStateFlow()
    }
}
