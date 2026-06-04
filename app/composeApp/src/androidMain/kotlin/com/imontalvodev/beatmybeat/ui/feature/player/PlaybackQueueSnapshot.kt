package com.imontalvodev.beatmybeat.ui.feature.player

import com.imontalvodev.beatmybeat.ui.data.DeviceTrack
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cola de reproducción persistida: canción actual + pendientes, posición y shuffle.
 */
data class PlaybackQueueSnapshot(
    val orderUris: List<String>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleOn: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isEmpty: Boolean get() = orderUris.isEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("orderUris", JSONArray(orderUris))
        put("currentIndex", currentIndex)
        put("positionMs", positionMs)
        put("shuffleOn", shuffleOn)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(raw: String): PlaybackQueueSnapshot? = runCatching {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("orderUris") ?: JSONArray()
            val uris = (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).takeIf { it.isNotBlank() }
            }
            PlaybackQueueSnapshot(
                orderUris = uris,
                currentIndex = o.optInt("currentIndex", 0),
                positionMs = o.optLong("positionMs", 0L).coerceAtLeast(0L),
                shuffleOn = o.optBoolean("shuffleOn", false),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }.getOrNull()
    }
}

/** Resultado de resolver URIs del snapshot contra la biblioteca del dispositivo. */
data class ResolvedPlaybackQueue(
    val tracks: List<DeviceTrack>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleOn: Boolean,
)

/**
 * Resuelve URIs a pistas existentes. Las que ya no existen se omiten sin vaciar la cola.
 * Ajusta [currentIndex] si la pista actual desapareció (avanza a la siguiente existente).
 */
fun resolvePlaybackQueueSnapshot(
    snapshot: PlaybackQueueSnapshot,
    tracksByUri: Map<String, DeviceTrack>,
): ResolvedPlaybackQueue? {
    if (snapshot.orderUris.isEmpty()) return null
    val originalIdx = snapshot.currentIndex.coerceIn(0, snapshot.orderUris.lastIndex)
    val resolved = snapshot.orderUris.mapNotNull { tracksByUri[it] }
    if (resolved.isEmpty()) return null

    val startUri = snapshot.orderUris.subList(originalIdx, snapshot.orderUris.size)
        .firstOrNull { tracksByUri.containsKey(it) }
        ?: snapshot.orderUris.subList(0, originalIdx + 1).lastOrNull { tracksByUri.containsKey(it) }
    val currentIndex = startUri?.let { uri ->
        resolved.indexOfFirst { it.uri == uri }
    }?.coerceAtLeast(0) ?: 0

    return ResolvedPlaybackQueue(
        tracks = resolved,
        currentIndex = currentIndex.coerceIn(0, resolved.lastIndex),
        positionMs = snapshot.positionMs,
        shuffleOn = snapshot.shuffleOn,
    )
}

fun List<DeviceTrack>.toQueueJsonArray(): JSONArray {
    val arr = JSONArray()
    forEach { t ->
        arr.put(
            JSONObject().apply {
                put("id", t.uri)
                put("uri", t.uri)
                put("title", t.title)
                put("artist", t.artist)
            },
        )
    }
    return arr
}
