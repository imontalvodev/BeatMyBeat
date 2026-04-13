package com.imontalvodev.savetune.ui.network

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Caché simple de carátulas embebidas/locales.
 * Evita recalcular MediaMetadataRetriever para cada recomposición.
 */
object ArtworkCache {
    private const val MAX_ITEMS = 24

    private val cache = object : LruCache<Long, Bitmap>(MAX_ITEMS) {
        // no-op
    }

    fun get(trackId: Long): Bitmap? = cache.get(trackId)

    fun put(trackId: Long, bitmap: Bitmap) {
        if (bitmap == null) return
        cache.put(trackId, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}

