package com.imontalvodev.savetune.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.imontalvodev.savetune.R
import com.imontalvodev.savetune.notifications.SavetuneNotification
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class PlaybackService : Service() {

    data class QueueItem(
        val uri: String,
        val title: String,
        val artist: String,
    )

    private lateinit var session: MediaSessionCompat
    private val mediaPlayer: MediaPlayer by lazy { MediaPlayer() }
    private var audioManager: AudioManager? = null

    private var queue: List<QueueItem> = emptyList()
    private var index: Int = -1
    private var shuffleOn: Boolean = false
    private var repeatMode: RepeatMode = RepeatMode.OFF
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private enum class RepeatMode { OFF, LIST, ONE }

    override fun onCreate() {
        super.onCreate()
        SavetuneNotification.ensureChannels(this)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        session = MediaSessionCompat(this, "SavetunePlayback").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = handlePlay()
                override fun onPause() = handlePause()
                override fun onSkipToNext() = handleNext()
                override fun onSkipToPrevious() = handlePrev()
                override fun onStop() = handleStop()
                override fun onSeekTo(pos: Long) {
                    runCatching { mediaPlayer.seekTo(pos.toInt()) }
                    updatePlaybackState()
                    updateNotification()
                }
            })
            isActive = true
        }

        mediaPlayer.setOnCompletionListener {
            if (repeatMode == RepeatMode.ONE) {
                handlePlay()
            } else {
                handleNext()
            }
        }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            handleStop()
            true
        }

        scope.launch {
            while (isActive) {
                publishSnapshot()
                delay(300)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WITH_QUEUE -> {
                val json = intent.getStringExtra(EXTRA_QUEUE_JSON).orEmpty()
                val startAt = intent.getIntExtra(EXTRA_INDEX, -1)
                queue = parseQueue(json)
                index = startAt.coerceIn(queue.indices)
                shuffleOn = intent.getBooleanExtra(EXTRA_SHUFFLE, false)
                repeatMode = intent.getStringExtra(EXTRA_REPEAT)?.let { parseRepeat(it) } ?: RepeatMode.OFF
                startForeground(
                    SavetuneNotification.PLAYBACK_NOTIFICATION_ID,
                    buildNotification(),
                )
                playCurrent(reset = true)
            }

            ACTION_PLAY -> handlePlay()
            ACTION_PAUSE -> handlePause()
            ACTION_NEXT -> handleNext()
            ACTION_PREV -> handlePrev()
            ACTION_TOGGLE_SHUFFLE -> {
                shuffleOn = !shuffleOn
                updatePlaybackState()
                updateNotification()
            }
            ACTION_CYCLE_REPEAT -> {
                repeatMode = when (repeatMode) {
                    RepeatMode.OFF -> RepeatMode.LIST
                    RepeatMode.LIST -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                }
                updatePlaybackState()
                updateNotification()
            }
            ACTION_STOP -> handleStop()
            ACTION_SEEK -> {
                val ms = intent.getIntExtra(EXTRA_SEEK_MS, -1)
                if (ms >= 0) {
                    runCatching { mediaPlayer.seekTo(ms) }
                    updatePlaybackState()
                    updateNotification()
                }
            }
            Intent.ACTION_MEDIA_BUTTON -> MediaButtonReceiver.handleIntent(session, intent)
        }

        return START_STICKY
    }

    private fun handlePlay() {
        if (index !in queue.indices) return
        if (!mediaPlayer.isPlaying) {
            runCatching { mediaPlayer.start() }
        }
        updatePlaybackState()
        updateNotification()
    }

    private fun handlePause() {
        if (mediaPlayer.isPlaying) {
            runCatching { mediaPlayer.pause() }
        }
        updatePlaybackState()
        updateNotification()
    }

    private fun handleNext() {
        if (queue.isEmpty()) return
        if (repeatMode == RepeatMode.ONE) {
            playCurrent(reset = true)
            return
        }

        index = when {
            shuffleOn && queue.size > 1 -> {
                // Simple shuffle: pick a different index.
                val next = (0 until queue.size).filter { it != index }.random()
                next
            }
            index + 1 <= queue.lastIndex -> index + 1
            repeatMode == RepeatMode.LIST -> 0
            else -> {
                handlePause()
                return
            }
        }
        playCurrent(reset = true)
    }

    private fun handlePrev() {
        if (queue.isEmpty()) return
        if (repeatMode == RepeatMode.ONE) {
            playCurrent(reset = true)
            return
        }
        index = when {
            shuffleOn && queue.size > 1 -> {
                val prev = (0 until queue.size).filter { it != index }.random()
                prev
            }
            index - 1 >= 0 -> index - 1
            repeatMode == RepeatMode.LIST -> queue.lastIndex
            else -> 0
        }
        playCurrent(reset = true)
    }

    private fun handleStop() {
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.reset() }
        updatePlaybackState(stopped = true)
        publishSnapshot()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun playCurrent(reset: Boolean) {
        val item = queue.getOrNull(index) ?: return
        runCatching {
            if (reset) mediaPlayer.reset()
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            mediaPlayer.setDataSource(this, android.net.Uri.parse(item.uri))
            mediaPlayer.prepare()
            mediaPlayer.start()
        }.onFailure {
            handleStop()
            return
        }

        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.artist)
                .build(),
        )

        updatePlaybackState()
        publishSnapshot()
        updateNotification()
    }

    private fun updatePlaybackState(stopped: Boolean = false) {
        val playing = !stopped && runCatching { mediaPlayer.isPlaying }.getOrDefault(false)
        val actions =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        val state = when {
            stopped -> PlaybackStateCompat.STATE_STOPPED
            playing -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }

        val pos = runCatching { mediaPlayer.currentPosition.toLong() }.getOrDefault(0L)
        val rate = if (playing) 1f else 0f

        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, max(0L, pos), rate)
                .build(),
        )
        publishSnapshot()
    }

    private fun updateNotification() {
        val nm = NotificationManagerCompat.from(this)
        nm.notify(SavetuneNotification.PLAYBACK_NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val item = queue.getOrNull(index)
        val title = item?.title?.ifBlank { "Savetune" } ?: "Savetune"
        val artist = item?.artist.orEmpty()
        val isPlaying = runCatching { mediaPlayer.isPlaying }.getOrDefault(false)

        val prevIntent = Intent(this, PlaybackService::class.java).setAction(ACTION_PREV)
        val nextIntent = Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT)
        val playPauseIntent = Intent(this, PlaybackService::class.java).setAction(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
        val shuffleIntent = Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE_SHUFFLE)
        val repeatIntent = Intent(this, PlaybackService::class.java).setAction(ACTION_CYCLE_REPEAT)

        val piFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        val prevPI = android.app.PendingIntent.getService(this, 1, prevIntent, piFlags)
        val playPausePI = android.app.PendingIntent.getService(this, 2, playPauseIntent, piFlags)
        val nextPI = android.app.PendingIntent.getService(this, 3, nextIntent, piFlags)
        val shufflePI = android.app.PendingIntent.getService(this, 4, shuffleIntent, piFlags)
        val repeatPI = android.app.PendingIntent.getService(this, 5, repeatIntent, piFlags)

        val playIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playLabel = if (isPlaying) "Pause" else "Play"

        val style = MediaNotificationCompat.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(1, 2) // play/pause + next

        return NotificationCompat.Builder(this, "savetune_playback")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // lockscreen
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevPI)
            .addAction(playIcon, playLabel, playPausePI)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPI)
            .addAction(android.R.drawable.ic_menu_sort_by_size, if (shuffleOn) "Shuffle ON" else "Shuffle OFF", shufflePI)
            .addAction(android.R.drawable.ic_menu_rotate, repeatLabel(), repeatPI)
            .setStyle(style)
            .build()
    }

    private fun repeatLabel(): String = when (repeatMode) {
        RepeatMode.OFF -> "Repeat OFF"
        RepeatMode.LIST -> "Repeat LIST"
        RepeatMode.ONE -> "Repeat ONE"
    }

    private fun parseRepeat(s: String): RepeatMode = when (s.lowercase()) {
        "one" -> RepeatMode.ONE
        "list" -> RepeatMode.LIST
        else -> RepeatMode.OFF
    }

    private fun parseQueue(json: String): List<QueueItem> {
        if (json.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val out = ArrayList<QueueItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o: JSONObject = arr.optJSONObject(i) ?: continue
            val uri = o.optString("uri", "")
            if (uri.isBlank()) continue
            out.add(
                QueueItem(
                    uri = uri,
                    title = o.optString("title", ""),
                    artist = o.optString("artist", ""),
                ),
            )
        }
        return out
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { mediaPlayer.reset() }
        runCatching { mediaPlayer.release() }
        runCatching { session.release() }
        isPlayingSnapshot = false
        positionSnapshotMs = 0
        durationSnapshotMs = 0
        super.onDestroy()
    }

    private fun publishSnapshot() {
        isPlayingSnapshot = runCatching { mediaPlayer.isPlaying }.getOrDefault(false)
        positionSnapshotMs = runCatching { mediaPlayer.currentPosition }.getOrDefault(0)
        durationSnapshotMs = runCatching { mediaPlayer.duration }.getOrDefault(0).coerceAtLeast(0)
    }

    companion object {
        const val ACTION_START_WITH_QUEUE = "com.imontalvodev.savetune.action.PLAYBACK_START_WITH_QUEUE"
        const val ACTION_PLAY = "com.imontalvodev.savetune.action.PLAYBACK_PLAY"
        const val ACTION_PAUSE = "com.imontalvodev.savetune.action.PLAYBACK_PAUSE"
        const val ACTION_NEXT = "com.imontalvodev.savetune.action.PLAYBACK_NEXT"
        const val ACTION_PREV = "com.imontalvodev.savetune.action.PLAYBACK_PREV"
        const val ACTION_STOP = "com.imontalvodev.savetune.action.PLAYBACK_STOP"
        const val ACTION_SEEK = "com.imontalvodev.savetune.action.PLAYBACK_SEEK"
        const val ACTION_TOGGLE_SHUFFLE = "com.imontalvodev.savetune.action.PLAYBACK_TOGGLE_SHUFFLE"
        const val ACTION_CYCLE_REPEAT = "com.imontalvodev.savetune.action.PLAYBACK_CYCLE_REPEAT"

        const val EXTRA_QUEUE_JSON = "extra_queue_json"
        const val EXTRA_INDEX = "extra_index"
        const val EXTRA_SHUFFLE = "extra_shuffle"
        const val EXTRA_REPEAT = "extra_repeat"
        const val EXTRA_SEEK_MS = "extra_seek_ms"

        @Volatile private var isPlayingSnapshot: Boolean = false
        @Volatile private var positionSnapshotMs: Int = 0
        @Volatile private var durationSnapshotMs: Int = 0

        data class Snapshot(
            val isPlaying: Boolean,
            val positionMs: Int,
            val durationMs: Int,
        )

        fun getSnapshot(): Snapshot = Snapshot(
            isPlaying = isPlayingSnapshot,
            positionMs = positionSnapshotMs,
            durationMs = durationSnapshotMs,
        )

        fun startWithQueue(
            context: android.content.Context,
            queueJson: String,
            index: Int,
            shuffle: Boolean,
            repeat: String,
        ) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_START_WITH_QUEUE
                putExtra(EXTRA_QUEUE_JSON, queueJson)
                putExtra(EXTRA_INDEX, index)
                putExtra(EXTRA_SHUFFLE, shuffle)
                putExtra(EXTRA_REPEAT, repeat)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun seekTo(context: android.content.Context, positionMs: Int) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_SEEK
                putExtra(EXTRA_SEEK_MS, positionMs)
            }
            context.startService(intent)
        }
    }
}

