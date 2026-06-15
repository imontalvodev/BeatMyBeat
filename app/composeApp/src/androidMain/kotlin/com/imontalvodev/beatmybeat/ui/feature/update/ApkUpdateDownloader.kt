package com.imontalvodev.beatmybeat.ui.feature.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.network.GitHubReleaseInfo
import com.imontalvodev.beatmybeat.ui.storage.UpdatePrefs

object ApkUpdateDownloader {

    private var downloadReceiver: BroadcastReceiver? = null

    fun startDownload(context: Context, release: GitHubReleaseInfo) {
        val apkUrl = release.apkDownloadUrl
        if (!apkUrl.isNullOrBlank()) {
            enqueueApkDownload(context, apkUrl, release.version)
            return
        }
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(release.releasePageUrl)),
        )
    }

    private fun enqueueApkDownload(context: Context, apkUrl: String, version: String) {
        val appContext = context.applicationContext
        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = "BeatMyBeat-$version.apk"
        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle(appContext.getString(R.string.app_name))
            setDescription(appContext.getString(R.string.update_download_description, version))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType("application/vnd.android.package-archive")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val downloadId = downloadManager.enqueue(request)
        UpdatePrefs.setPendingApkDownloadId(appContext, downloadId)
        registerDownloadReceiver(appContext, downloadId)
        Toast.makeText(
            context,
            context.getString(R.string.update_download_started),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun registerDownloadReceiver(context: Context, downloadId: Long) {
        unregisterDownloadReceiver(context)
        val receiver = ApkUpdateDownloadReceiver(downloadId)
        downloadReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun unregisterDownloadReceiver(context: Context) {
        downloadReceiver?.let { receiver ->
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
            downloadReceiver = null
        }
    }
}
