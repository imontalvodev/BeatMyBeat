package com.imontalvodev.beatmybeat.ui.feature.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.storage.UpdatePrefs

object ApkUpdateInstaller {

    fun tryCompletePendingInstall(context: Context) {
        val downloadId = UpdatePrefs.getPendingApkDownloadId(context)
        if (downloadId < 0L) return
        handleDownloadFinished(context.applicationContext, downloadId)
    }

    fun handleDownloadFinished(context: Context, downloadId: Long) {
        val pendingId = UpdatePrefs.getPendingApkDownloadId(context)
        if (downloadId != pendingId) return

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                clearPending(context)
                return
            }

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val apkUri = resolveDownloadedApkUri(downloadManager, cursor, downloadId)
                    if (apkUri == null) {
                        clearPending(context)
                        showToast(context, R.string.update_install_failed)
                        return
                    }
                    if (launchPackageInstaller(context, apkUri)) {
                        clearPending(context)
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    clearPending(context)
                    showToast(context, R.string.update_download_failed)
                }
            }
        }
    }

    private fun resolveDownloadedApkUri(
        downloadManager: DownloadManager,
        cursor: android.database.Cursor,
        downloadId: Long,
    ): Uri? {
        downloadManager.getUriForDownloadedFile(downloadId)?.let { return it }

        val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        if (localUriIndex >= 0) {
            val localUri = cursor.getString(localUriIndex)?.trim().orEmpty()
            if (localUri.isNotBlank()) {
                return Uri.parse(localUri)
            }
        }
        return null
    }

    private fun launchPackageInstaller(context: Context, apkUri: Uri): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages(context)) {
            showToast(context, R.string.update_install_permission_required)
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(settingsIntent) }
            return false
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(installIntent)
            true
        }.getOrElse {
            showToast(context, R.string.update_install_failed)
            false
        }
    }

    private fun canInstallPackages(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }
            .getOrDefault(false)

    private fun clearPending(context: Context) {
        UpdatePrefs.clearPendingApkDownloadId(context)
        ApkUpdateDownloader.unregisterDownloadReceiver(context)
    }

    private fun showToast(context: Context, messageResId: Int) {
        Toast.makeText(context, context.getString(messageResId), Toast.LENGTH_SHORT).show()
    }
}
