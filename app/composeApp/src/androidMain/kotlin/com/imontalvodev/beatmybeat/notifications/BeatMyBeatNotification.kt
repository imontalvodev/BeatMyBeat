package com.imontalvodev.beatmybeat.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.imontalvodev.beatmybeat.MainActivity
import com.imontalvodev.beatmybeat.R

object BeatMyBeatNotification {
    private const val CHANNEL_DOWNLOAD = "beatmybeat_downloads"
    private const val CHANNEL_PLAYBACK = "beatmybeat_playback"

    // IDs fijos para poder “pisar” la notificación en progreso por la de completado.
    const val DOWNLOAD_NOTIFICATION_ID = 1001
    const val PLAYBACK_NOTIFICATION_ID = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOAD,
            "Descargas",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Estado de descargas de música"
        }

        val playbackChannel = NotificationChannel(
            CHANNEL_PLAYBACK,
            "Reproducción",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Control y estado de la reproducción"
        }

        // Safe create/update
        nm.createNotificationChannel(downloadChannel)
        nm.createNotificationChannel(playbackChannel)
    }

    private fun baseContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun showDownloadInProgress(
        context: Context,
        title: String,
        subtitle: String,
        notificationId: Int = DOWNLOAD_NOTIFICATION_ID,
    ) {
        ensureChannels(context)
        val nm = NotificationManagerCompat.from(context)

        val notification = buildDownloadInProgressNotification(
            context = context,
            title = title,
            subtitle = subtitle,
        )

        nm.notify(notificationId, notification)
    }

    fun buildDownloadInProgressNotification(
        context: Context,
        title: String,
        subtitle: String,
    ) = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(subtitle)
        .setContentIntent(baseContentIntent(context))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(0, 0, true) // indeterminate
        .build()

    fun showDownloadCompleted(
        context: Context,
        title: String,
        subtitle: String,
        notificationId: Int = DOWNLOAD_NOTIFICATION_ID,
    ) {
        ensureChannels(context)
        val nm = NotificationManagerCompat.from(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(baseContentIntent(context))
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setProgress(0, 0, false)
            .build()

        nm.notify(notificationId, notification)
    }

    fun showDownloadFailed(
        context: Context,
        title: String,
        subtitle: String,
        notificationId: Int = DOWNLOAD_NOTIFICATION_ID,
    ) {
        ensureChannels(context)
        val nm = NotificationManagerCompat.from(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(baseContentIntent(context))
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setProgress(0, 0, false)
            .build()

        nm.notify(notificationId, notification)
    }

    fun showPlaybackOngoing(
        context: Context,
        title: String,
        subtitle: String,
        notificationId: Int = PLAYBACK_NOTIFICATION_ID,
    ) {
        ensureChannels(context)
        val nm = NotificationManagerCompat.from(context)

        val notification = buildPlaybackNotification(
            context = context,
            title = title,
            subtitle = subtitle,
        )

        nm.notify(notificationId, notification)
    }

    fun buildPlaybackNotification(
        context: Context,
        title: String,
        subtitle: String,
    ) = NotificationCompat.Builder(context, CHANNEL_PLAYBACK)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(subtitle)
        .setContentIntent(baseContentIntent(context))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()
}

