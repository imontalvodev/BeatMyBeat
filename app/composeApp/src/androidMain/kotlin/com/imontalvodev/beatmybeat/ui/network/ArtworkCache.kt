package com.imontalvodev.beatmybeat.ui.network

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Caché de carátulas locales por bytes (no por número de entradas).
 * Indexado por URI del fichero para no mezclar pistas con el mismo MediaStore id.
 */
object ArtworkCache {
    private const val MAX_BYTES = 12 * 1024 * 1024 // ~12 MiB

    private val cacheByUri = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = BitmapDecoding.byteCount(value)
    }

    fun getUri(uri: String): Bitmap? = cacheByUri.get(uri)

    fun putUri(uri: String, bitmap: Bitmap) {
        if (uri.isNotBlank()) cacheByUri.put(uri, bitmap)
    }

    fun clear() {
        cacheByUri.evictAll()
    }
}
