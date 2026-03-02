package com.imontalvodev.savetune

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "savetune_music"
        const val NOTIF_ID = 1

        const val ACTION_START = "com.imontalvodev.savetune.action.START"
        const val ACTION_PLAY_PAUSE = "com.imontalvodev.savetune.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.imontalvodev.savetune.action.NEXT"
        const val ACTION_PREV = "com.imontalvodev.savetune.action.PREV"
        const val ACTION_STOP = "com.imontalvodev.savetune.action.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification())
            }
            ACTION_PLAY_PAUSE -> {
                NowPlayingState.togglePlayPause(this)
                updateNotification()
            }
            ACTION_NEXT -> {
                NowPlayingState.playNext(this)
                updateNotification()
            }
            ACTION_PREV -> {
                NowPlayingState.playPrevious(this)
                updateNotification()
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SaveTune playback",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val song = NowPlayingState.currentSong
        val title = song?.title ?: getString(R.string.app_name)
        val artist = song?.artist ?: "SaveTune"

        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun actionIntent(action: String, requestCode: Int): PendingIntent {
            val i = Intent(this, MusicService::class.java).apply { this.action = action }
            return PendingIntent.getService(
                this, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val isPlaying = NowPlayingState.mediaPlayer?.isPlaying == true
        val playPauseIcon =
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(openPending)
            .setOngoing(isPlaying)
            .addAction(
                android.R.drawable.ic_media_previous,
                "Prev",
                actionIntent(ACTION_PREV, 1)
            )
            .addAction(
                playPauseIcon,
                playPauseTitle,
                actionIntent(ACTION_PLAY_PAUSE, 2)
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                actionIntent(ACTION_NEXT, 3)
            )
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }
}

