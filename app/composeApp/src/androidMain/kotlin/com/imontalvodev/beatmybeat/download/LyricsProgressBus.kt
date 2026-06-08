package com.imontalvodev.beatmybeat.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Progreso observable del lote de descarga de letras del dispositivo. */
data class ActiveLyricsBatchUiState(
    val done: Int,
    val total: Int,
    val found: Int,
    val notFound: Int,
    val skipped: Int,
    val currentTitle: String,
    val phase: String,
)

object LyricsProgressBus {
    private val _state = MutableStateFlow<ActiveLyricsBatchUiState?>(null)
    val state: StateFlow<ActiveLyricsBatchUiState?> = _state.asStateFlow()

    fun update(
        done: Int,
        total: Int,
        found: Int,
        notFound: Int,
        skipped: Int,
        currentTitle: String,
        phase: String,
    ) {
        _state.value = ActiveLyricsBatchUiState(
            done = done,
            total = total,
            found = found,
            notFound = notFound,
            skipped = skipped,
            currentTitle = currentTitle,
            phase = phase,
        )
    }

    fun clear() {
        _state.value = null
    }
}
