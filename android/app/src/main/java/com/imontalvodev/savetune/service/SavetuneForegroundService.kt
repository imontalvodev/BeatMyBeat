package com.imontalvodev.savetune.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.imontalvodev.savetune.notifications.SavetuneNotification

class SavetuneForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    private var downloadRunning: Boolean = false
    private var playbackRunning: Boolean = false

    private var downloadTitle: String = "Descargando..."
    private var downloadSubtitle: String = ""

    private var playbackTitle: String = "Reproduciendo"
    private var playbackSubtitle: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_DOWNLOAD -> {
                downloadRunning = true
                downloadTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Descargando..." }
                downloadSubtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty()
                updateForeground()
            }

            ACTION_STOP_DOWNLOAD -> {
                downloadRunning = false
                stopIfNoWork()
            }

            ACTION_START_PLAYBACK -> {
                playbackRunning = true
                playbackTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Reproduciendo" }
                playbackSubtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty()
                updateForeground()
            }

            ACTION_STOP_PLAYBACK -> {
                playbackRunning = false
                stopIfNoWork()
            }
        }

        return START_STICKY
    }

    private fun updateForeground() {
        SavetuneNotification.ensureChannels(this)

        try {
            if (playbackRunning) {
                startForeground(
                    SavetuneNotification.PLAYBACK_NOTIFICATION_ID,
                    SavetuneNotification.buildPlaybackNotification(
                        context = this,
                        title = playbackTitle,
                        subtitle = playbackSubtitle,
                    ),
                )
                return
            }

            if (downloadRunning) {
                startForeground(
                    SavetuneNotification.DOWNLOAD_NOTIFICATION_ID,
                    SavetuneNotification.buildDownloadInProgressNotification(
                        context = this,
                        title = downloadTitle,
                        subtitle = downloadSubtitle,
                    ),
                )
            }
        } catch (_: Exception) {
            // En algunos estados (Android 12+) puede bloquear el startForeground;
            // evitamos crash y dejamos la app seguir.
            stopSelf()
        }
    }

    private fun stopIfNoWork() {
        if (!downloadRunning && !playbackRunning) {
            // Detach so completion notifications posted with the same ID remain visible.
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        } else {
            // If both are active, keep foreground notification as the “primary” one.
            updateForeground()
        }
    }

    companion object {
        const val ACTION_START_DOWNLOAD = "com.imontalvodev.savetune.action.START_DOWNLOAD"
        const val ACTION_STOP_DOWNLOAD = "com.imontalvodev.savetune.action.STOP_DOWNLOAD"
        const val ACTION_START_PLAYBACK = "com.imontalvodev.savetune.action.START_PLAYBACK"
        const val ACTION_STOP_PLAYBACK = "com.imontalvodev.savetune.action.STOP_PLAYBACK"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"

        fun startDownload(context: android.content.Context, title: String, subtitle: String = "") {
            val intent = Intent(context, SavetuneForegroundService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stopDownload(context: android.content.Context) {
            val intent = Intent(context, SavetuneForegroundService::class.java).apply {
                action = ACTION_STOP_DOWNLOAD
            }
            context.startService(intent)
        }

        fun startPlayback(context: android.content.Context, title: String, subtitle: String = "") {
            val intent = Intent(context, SavetuneForegroundService::class.java).apply {
                action = ACTION_START_PLAYBACK
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stopPlayback(context: android.content.Context) {
            val intent = Intent(context, SavetuneForegroundService::class.java).apply {
                action = ACTION_STOP_PLAYBACK
            }
            context.startService(intent)
        }
    }
}

