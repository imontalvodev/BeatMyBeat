package com.imontalvodev.beatmybeat.download

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado observable de la descarga/instalación de la actualización in-app. */
sealed interface UpdateDownloadState {
    data class Downloading(val bytesDownloaded: Long, val bytesTotal: Long) : UpdateDownloadState
    data class ReadyToInstall(val apkUri: Uri, val version: String) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

/**
 * Canal compartido entre [com.imontalvodev.beatmybeat.ui.feature.update.ApkUpdateDownloader]
 * (progreso, vía polling de [android.app.DownloadManager]), [com.imontalvodev.beatmybeat.ui.feature.update.ApkUpdateInstaller]
 * (resultado final) y la UI que muestra el seguimiento gráfico.
 */
object UpdateDownloadProgressBus {
    private val _state = MutableStateFlow<UpdateDownloadState?>(null)
    val state: StateFlow<UpdateDownloadState?> = _state.asStateFlow()

    fun setDownloading(bytesDownloaded: Long, bytesTotal: Long) {
        _state.value = UpdateDownloadState.Downloading(bytesDownloaded, bytesTotal)
    }

    fun setReadyToInstall(apkUri: Uri, version: String) {
        _state.value = UpdateDownloadState.ReadyToInstall(apkUri, version)
    }

    fun setFailed(message: String) {
        _state.value = UpdateDownloadState.Failed(message)
    }

    fun clear() {
        _state.value = null
    }
}
