package com.imontalvodev.beatmybeat.ui.feature.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.core.Logger
import com.imontalvodev.beatmybeat.ui.storage.UpdatePrefs
import java.io.File

object ApkUpdateInstaller {

    private const val LOG_TAG = "ApkUpdateInstaller"
    private const val UPDATES_CACHE_DIR = "updates"
    private const val PENDING_APK_FILENAME = "pending_update.apk"

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
                    val verifiedApkUri = copyAndVerifyDownloadedApk(context, downloadManager, downloadId)
                    if (verifiedApkUri == null) {
                        clearPending(context)
                        showToast(context, R.string.update_install_failed)
                        return
                    }
                    if (launchPackageInstaller(context, verifiedApkUri)) {
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

    /**
     * Copia el APK descargado (gestionado por [DownloadManager], fuera del control de otras apps)
     * a caché privada de la app y verifica que su `packageName` coincide con el de BeatMyBeat antes
     * de exponerlo al instalador. Evita instalar un APK inesperado (asset de release equivocado,
     * descarga corrupta) y evita depender de URIs `file://` no soportadas en Android moderno.
     */
    private fun copyAndVerifyDownloadedApk(
        context: Context,
        downloadManager: DownloadManager,
        downloadId: Long,
    ): Uri? {
        val cacheDir = File(context.cacheDir, UPDATES_CACHE_DIR).apply { mkdirs() }
        val destFile = File(cacheDir, PENDING_APK_FILENAME)

        val copied = runCatching {
            val pfd = downloadManager.openDownloadedFile(downloadId)
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            true
        }.getOrElse { error ->
            Logger.e(LOG_TAG, "No se pudo copiar el APK descargado a caché", error)
            destFile.delete()
            false
        }
        if (!copied) return null

        val packageInfo = runCatching {
            context.packageManager.getPackageArchiveInfo(destFile.absolutePath, 0)
        }.getOrNull()
        val downloadedPackageName = packageInfo?.packageName
        if (downloadedPackageName != context.packageName) {
            Logger.e(
                LOG_TAG,
                "APK descargado rechazado: packageName='$downloadedPackageName' " +
                    "no coincide con '${context.packageName}'",
            )
            destFile.delete()
            return null
        }

        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
        }.onFailure { error ->
            Logger.e(LOG_TAG, "No se pudo generar la URI del FileProvider para el APK", error)
        }.getOrNull()
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
