package com.imontalvodev.beatmybeat.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.imontalvodev.beatmybeat.notifications.BeatMyBeatNotification
import com.imontalvodev.beatmybeat.ui.network.AudioDownloader
import com.imontalvodev.beatmybeat.ui.network.MIDDLEWARE_BASE_URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SongDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_DOWNLOAD_SINGLE) return START_NOT_STICKY

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
        val album = intent.getStringExtra(EXTRA_ALBUM).orEmpty()
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        val thumbnailUrl = intent.getStringExtra(EXTRA_THUMBNAIL_URL).orEmpty()
        val format = AudioDownloader.DownloadFormat.fromId(intent.getStringExtra(EXTRA_FORMAT))

        if (BeatMyBeatNotification.canPostNotifications(this)) {
            runCatching {
                startForeground(
                    BeatMyBeatNotification.DOWNLOAD_NOTIFICATION_ID,
                    BeatMyBeatNotification.buildDownloadInProgressNotification(
                        context = this,
                        title = "Descargando canción",
                        subtitle = title.ifBlank { "Procesando..." },
                    ),
                )
            }
        }

        scope.launch {
            val result = runCatching {
                AudioDownloader.downloadAutoToAppMusic(
                    context = this@SongDownloadService,
                    middlewareBaseUrl = MIDDLEWARE_BASE_URL,
                    title = title,
                    artist = artist,
                    album = album,
                    format = format,
                    videoId = videoId,
                    thumbnailUrl = thumbnailUrl,
                )
            }.getOrNull()
            Handler(Looper.getMainLooper()).post {
                val msg = when {
                    result == null -> "Error descargando canción."
                    result.success -> "Descargada (${format.label}): ${result.fileName ?: title}"
                    else -> "No se pudo descargar la canción."
                }
                Toast.makeText(this@SongDownloadService, msg, Toast.LENGTH_SHORT).show()
            }
            runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val ACTION_DOWNLOAD_SINGLE = "com.imontalvodev.beatmybeat.action.DOWNLOAD_SINGLE"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_ARTIST = "extra_artist"
        private const val EXTRA_ALBUM = "extra_album"
        private const val EXTRA_VIDEO_ID = "extra_video_id"
        private const val EXTRA_THUMBNAIL_URL = "extra_thumbnail_url"
        private const val EXTRA_FORMAT = "extra_format"

        fun enqueueDownload(
            context: Context,
            title: String,
            artist: String,
            album: String = "",
            videoId: String = "",
            thumbnailUrl: String = "",
            format: AudioDownloader.DownloadFormat = AudioDownloader.DownloadFormat.MP3,
        ) {
            val intent = Intent(context, SongDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD_SINGLE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_ALBUM, album)
                putExtra(EXTRA_VIDEO_ID, videoId)
                putExtra(EXTRA_THUMBNAIL_URL, thumbnailUrl)
                putExtra(EXTRA_FORMAT, format.id)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
