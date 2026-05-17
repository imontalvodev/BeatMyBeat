package com.imontalvodev.beatmybeat.ui.network

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Caché de carátulas locales por bytes (no por número de entradas).
 */
object ArtworkCache {
    private const val MAX_BYTES = 12 * 1024 * 1024 // ~12 MiB

    private val cache = object : LruCache<Long, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: Long, value: Bitmap): Int = BitmapDecoding.byteCount(value)
    }

    fun get(trackId: Long): Bitmap? = cache.get(trackId)

    fun put(trackId: Long, bitmap: Bitmap) {
        cache.put(trackId, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}
