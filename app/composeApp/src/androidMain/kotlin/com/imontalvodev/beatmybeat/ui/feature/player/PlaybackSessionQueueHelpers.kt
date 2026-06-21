package com.imontalvodev.beatmybeat.ui.feature.player

import com.imontalvodev.beatmybeat.ui.data.DeviceTrack

internal data class SessionQueueState(
    val orderUris: List<String>,
    val currentIndex: Int,
)

internal fun ensureSessionQueue(
    currentTrackUri: String?,
    pendingQueueUris: List<String>,
    existing: SessionQueueState?,
): SessionQueueState? {
    if (existing != null && existing.orderUris.isNotEmpty()) return existing
    val current = currentTrackUri?.takeIf { it.isNotBlank() } ?: return null
    val pending = pendingQueueUris.filter { it.isNotBlank() && it != current }
    return SessionQueueState(
        orderUris = listOf(current) + pending,
        currentIndex = 0,
    )
}

internal fun pendingUrisFromSession(state: SessionQueueState): List<String> =
    state.orderUris.drop(state.currentIndex + 1)

internal fun resolvePendingTracks(
    pendingUris: List<String>,
    tracksByUri: Map<String, DeviceTrack>,
): List<DeviceTrack> = pendingUris.mapNotNull { tracksByUri[it] }

internal fun appendTracksToSession(
    state: SessionQueueState,
    trackUris: List<String>,
): SessionQueueState {
    if (trackUris.isEmpty()) return state
    val filtered = trackUris.filter { it.isNotBlank() }
    if (filtered.isEmpty()) return state
    return state.copy(orderUris = state.orderUris + filtered)
}

internal fun insertTracksPlayNextInSession(
    state: SessionQueueState,
    trackUris: List<String>,
): SessionQueueState {
    if (trackUris.isEmpty()) return state
    val filtered = trackUris.filter { it.isNotBlank() }
    if (filtered.isEmpty()) return state
    val mutable = state.orderUris.toMutableList()
    var insertAt = (state.currentIndex + 1).coerceAtMost(mutable.size)
    filtered.forEach { uri ->
        mutable.add(insertAt, uri)
        insertAt++
    }
    return state.copy(orderUris = mutable)
}

internal fun advanceSessionToTrack(
    state: SessionQueueState,
    trackUri: String,
): SessionQueueState? {
    val index = state.orderUris.indexOf(trackUri)
    if (index < 0) return null
    return state.copy(currentIndex = index)
}

internal fun removeTrackFromSession(
    state: SessionQueueState,
    trackUri: String,
): SessionQueueState {
    val index = state.orderUris.indexOf(trackUri)
    if (index < 0) return state
    val mutable = state.orderUris.toMutableList()
    mutable.removeAt(index)
    if (mutable.isEmpty()) {
        return SessionQueueState(emptyList(), 0)
    }
    val newIndex = when {
        index < state.currentIndex -> state.currentIndex - 1
        index == state.currentIndex -> state.currentIndex.coerceAtMost(mutable.lastIndex)
        else -> state.currentIndex
    }.coerceIn(0, mutable.lastIndex)
    return SessionQueueState(mutable, newIndex)
}
