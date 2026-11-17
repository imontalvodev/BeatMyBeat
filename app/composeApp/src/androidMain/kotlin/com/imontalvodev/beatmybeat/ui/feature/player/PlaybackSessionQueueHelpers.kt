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

/**
 * Tamaño del bloque "a continuación" que ya espera al frente de los pendientes.
 *
 * [playNextUris] es el historial (en orden de inserción) de lo añadido con "Reproducir a
 * continuación". Cuenta la racha más larga de la cabeza de [pendingUris] que coincide con un
 * tramo contiguo del historial: si las primeras ya sonaron, el resto del bloque sigue contando;
 * si algo se quitó o se reordenó, la racha se corta sola y no hace falta mantener contadores
 * en cada mutación de la cola.
 */
internal fun playNextBlockSize(
    pendingUris: List<String>,
    playNextUris: List<String>,
): Int {
    var best = 0
    for (start in playNextUris.indices) {
        var k = 0
        while (start + k < playNextUris.size && k < pendingUris.size &&
            pendingUris[k] == playNextUris[start + k]
        ) k++
        if (k > best) best = k
    }
    return best
}

/**
 * Inserta tras la canción actual y tras el bloque "a continuación" previo ([blockOffset]):
 * orden FIFO, lo que se añade antes suena antes.
 */
internal fun insertTracksPlayNextInSession(
    state: SessionQueueState,
    trackUris: List<String>,
    blockOffset: Int = 0,
): SessionQueueState {
    if (trackUris.isEmpty()) return state
    val filtered = trackUris.filter { it.isNotBlank() }
    if (filtered.isEmpty()) return state
    val mutable = state.orderUris.toMutableList()
    var insertAt = (state.currentIndex + 1 + blockOffset.coerceAtLeast(0)).coerceAtMost(mutable.size)
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

/**
 * Sustituye lo pendiente (todo lo posterior a la pista actual) por [pendingUris], manteniendo lo
 * ya reproducido y la pista actual. Se usa tras reordenar o quitar en la pantalla de cola: sin
 * esto la lista de sesión conservaba el orden anterior y lo deshacía al añadir, avanzar o
 * reiniciar la app.
 */
internal fun replacePendingInSession(
    state: SessionQueueState,
    pendingUris: List<String>,
): SessionQueueState {
    val head = state.orderUris.take(state.currentIndex + 1)
    return state.copy(orderUris = head + pendingUris.filter { it.isNotBlank() })
}
