package com.imontalvodev.beatmybeat.ui.feature.update

import android.app.DownloadManager
import android.app.PendingIntent
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
import com.imontalvodev.beatmybeat.download.UpdateDownloadProgressBus
import com.imontalvodev.beatmybeat.notifications.BeatMyBeatNotification
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
                    ApkUpdateDownloader.stopProgressPolling()
                    val verifiedApkUri = copyAndVerifyDownloadedApk(context, downloadManager, downloadId)
                    if (verifiedApkUri == null) {
                        val message = context.getString(R.string.update_install_failed)
                        clearPending(context)
                        UpdateDownloadProgressBus.setFailed(message)
                        showToast(context, R.string.update_install_failed)
                        return
                    }
                    // No se instala automáticamente: se pregunta al usuario (diálogo en la app +
                    // acción en la notificación) para que decida cuándo interrumpir lo que esté
                    // haciendo. El pending id se mantiene hasta que instale o descarte.
                    val version = UpdatePrefs.getPendingApkVersion(context).ifBlank { "" }
                    UpdateDownloadProgressBus.setReadyToInstall(verifiedApkUri, version)
                    showReadyToInstallNotification(context, verifiedApkUri, version)
                }
                DownloadManager.STATUS_FAILED -> {
                    ApkUpdateDownloader.stopProgressPolling()
                    val message = context.getString(R.string.update_download_failed)
                    clearPending(context)
                    UpdateDownloadProgressBus.setFailed(message)
                    showToast(context, R.string.update_download_failed)
                }
                DownloadManager.STATUS_PAUSED -> {
                    // La mayoría de pausas (esperando wifi/red/reintento) las resuelve el propio
                    // DownloadManager solo; no tocar el pending id o se pierde el progreso. Solo
                    // PAUSED_UNKNOWN no tiene reintento automático esperable: limpiar en vez de
                    // dejar el pending id y el receiver enganchados para siempre.
                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                    if (reason == DownloadManager.PAUSED_UNKNOWN) {
                        ApkUpdateDownloader.stopProgressPolling()
                        runCatching { downloadManager.remove(downloadId) }
                        val message = context.getString(R.string.update_download_failed)
                        clearPending(context)
                        UpdateDownloadProgressBus.setFailed(message)
                        showToast(context, R.string.update_download_failed)
                    }
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
        val destFile = cachedApkFile(context)
        destFile.parentFile?.mkdirs()

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

    private fun cachedApkFile(context: Context): File =
        File(File(context.cacheDir, UPDATES_CACHE_DIR), PENDING_APK_FILENAME)

    /** Borra la copia verificada residual de una actualización anterior (cancelada o ya instalada). */
    fun clearCachedApk(context: Context) {
        runCatching { cachedApkFile(context).delete() }
    }

    private fun showReadyToInstallNotification(context: Context, apkUri: Uri, version: String) {
        val installIntent = Intent(context, ApkUpdateInstallActionReceiver::class.java).apply {
            action = ApkUpdateInstallActionReceiver.ACTION_INSTALL
            data = apkUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        BeatMyBeatNotification.showUpdateReadyToInstall(
            context = context,
            title = context.getString(R.string.update_ready_title),
            subtitle = context.getString(R.string.update_ready_message, version),
            installActionLabel = context.getString(R.string.update_install_apk),
            pendingIntent = pendingIntent,
        )
    }

    /** Llamado desde el diálogo en la app o desde la acción de la notificación. */
    fun installNow(context: Context, apkUri: Uri) {
        if (launchPackageInstaller(context, apkUri)) {
            clearPending(context)
            UpdateDownloadProgressBus.clear()
        }
    }

    /** El usuario pospone la instalación: se oculta el diálogo pero se mantiene el pending id, así
     * [tryCompletePendingInstall] lo vuelve a ofrecer en el próximo arranque de la app. */
    fun dismissReadyToInstall() {
        UpdateDownloadProgressBus.clear()
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
        UpdatePrefs.clearPendingApkVersion(context)
        ApkUpdateDownloader.unregisterDownloadReceiver(context)
    }

    private fun showToast(context: Context, messageResId: Int) {
        Toast.makeText(context, context.getString(messageResId), Toast.LENGTH_SHORT).show()
    }
}
