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
import com.imontalvodev.beatmybeat.core.Logger
import com.imontalvodev.beatmybeat.download.UpdateDownloadProgressBus
import com.imontalvodev.beatmybeat.ui.network.GitHubReleaseInfo
import com.imontalvodev.beatmybeat.ui.storage.UpdatePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object ApkUpdateDownloader {

    private const val LOG_TAG = "ApkUpdateDownloader"
    private const val POLL_INTERVAL_MS = 400L

    private var downloadReceiver: BroadcastReceiver? = null
    private val pollScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressPollJob: Job? = null

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

        // Descarga/APK de una versión anterior pendiente de instalar: fuera, para dejar sitio a la
        // nueva. DownloadManager.remove() borra tanto el registro como el archivo en disco.
        val previousId = UpdatePrefs.getPendingApkDownloadId(appContext)
        if (previousId >= 0L) {
            runCatching { downloadManager.remove(previousId) }
            Logger.d(LOG_TAG, "Descarga de actualización anterior (id=$previousId) eliminada")
        }
        ApkUpdateInstaller.clearCachedApk(appContext)

        val fileName = "BeatMyBeat-$version.apk"
        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle(appContext.getString(R.string.app_name))
            setDescription(appContext.getString(R.string.update_download_description, version))
            // VISIBLE (no VISIBLE_NOTIFY_COMPLETED): el aviso de "descarga completa, toca para
            // instalar" nativo del sistema abriría el APK sin pasar por la verificación de
            // packageName de ApkUpdateInstaller. La notificación de "listo para instalar" la
            // gestionamos nosotros mismos tras verificar.
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setMimeType("application/vnd.android.package-archive")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val downloadId = downloadManager.enqueue(request)
        UpdatePrefs.setPendingApkDownloadId(appContext, downloadId)
        UpdatePrefs.setPendingApkVersion(appContext, version)
        registerDownloadReceiver(appContext, downloadId)
        UpdateDownloadProgressBus.setDownloading(0L, 0L)
        startProgressPolling(appContext, downloadManager, downloadId)
        Toast.makeText(
            context,
            context.getString(R.string.update_download_started),
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** Sondea [DownloadManager] (no hay callback push de progreso) para alimentar el seguimiento gráfico. */
    private fun startProgressPolling(context: Context, downloadManager: DownloadManager, downloadId: Long) {
        progressPollJob?.cancel()
        progressPollJob = pollScope.launch {
            while (isActive) {
                var terminal = false
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = runCatching { downloadManager.query(query) }.getOrNull()
                if (cursor == null) {
                    terminal = true
                } else {
                    cursor.use {
                        if (!it.moveToFirst()) {
                            terminal = true
                            return@use
                        }
                        val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = if (statusIdx >= 0) it.getInt(statusIdx) else -1
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            terminal = true
                            return@use
                        }
                        val downloadedIdx = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIdx = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val downloaded = if (downloadedIdx >= 0) it.getLong(downloadedIdx) else 0L
                        val total = if (totalIdx >= 0) it.getLong(totalIdx) else -1L
                        UpdateDownloadProgressBus.setDownloading(downloaded, total.coerceAtLeast(0L))
                    }
                }
                if (terminal) break
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopProgressPolling() {
        progressPollJob?.cancel()
        progressPollJob = null
    }

    /** Cancela la descarga en curso (botón "Cancelar" del diálogo de progreso). */
    fun cancelDownload(context: Context) {
        val appContext = context.applicationContext
        val id = UpdatePrefs.getPendingApkDownloadId(appContext)
        if (id >= 0L) {
            val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            runCatching { downloadManager.remove(id) }
        }
        stopProgressPolling()
        UpdatePrefs.clearPendingApkDownloadId(appContext)
        UpdatePrefs.clearPendingApkVersion(appContext)
        unregisterDownloadReceiver(appContext)
        ApkUpdateInstaller.clearCachedApk(appContext)
        UpdateDownloadProgressBus.clear()
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
