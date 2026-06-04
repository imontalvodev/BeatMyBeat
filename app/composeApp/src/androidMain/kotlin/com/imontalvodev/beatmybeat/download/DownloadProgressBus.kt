package com.imontalvodev.beatmybeat.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Progreso de una fase de descarga (archivo actual). */
data class DownloadProgressUpdate(
    val phase: String,
    /** Fracción 0..1 del archivo en curso; null si la fase es indeterminada. */
    val fileFraction: Float? = null,
)

/** Estado observable para la UI (canción en curso o lote playlist). */
data class ActiveDownloadUiState(
    val title: String,
    val subtitle: String = "",
    val phase: String,
    val fileFraction: Float? = null,
    val batchDone: Int? = null,
    val batchTotal: Int? = null,
    val batchFailed: Int = 0,
    val isBatch: Boolean = false,
)

/**
 * Canal compartido entre [com.imontalvodev.beatmybeat.service.SongDownloadService],
 * pantallas de descarga y [com.imontalvodev.beatmybeat.ui.network.AudioDownloader].
 */
object DownloadProgressBus {
    private val _state = MutableStateFlow<ActiveDownloadUiState?>(null)
    val state: StateFlow<ActiveDownloadUiState?> = _state.asStateFlow()

    fun setBatch(
        done: Int,
        total: Int,
        failed: Int,
        currentTitle: String,
        phase: String,
        fileFraction: Float?,
    ) {
        _state.value = ActiveDownloadUiState(
            title = currentTitle,
            phase = phase,
            fileFraction = fileFraction,
            batchDone = done,
            batchTotal = total,
            batchFailed = failed,
            isBatch = true,
        )
    }

    fun setSingle(title: String, artist: String, phase: String, fileFraction: Float?) {
        _state.value = ActiveDownloadUiState(
            title = title,
            subtitle = artist,
            phase = phase,
            fileFraction = fileFraction,
            isBatch = false,
        )
    }

    fun clear() {
        _state.value = null
    }
}
