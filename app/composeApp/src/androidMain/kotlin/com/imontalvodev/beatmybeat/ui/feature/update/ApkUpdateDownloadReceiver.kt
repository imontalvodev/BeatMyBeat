package com.imontalvodev.beatmybeat.ui.feature.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ApkUpdateDownloadReceiver(
    private val expectedDownloadId: Long,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId != expectedDownloadId) return
        ApkUpdateInstaller.handleDownloadFinished(context.applicationContext, downloadId)
    }
}
