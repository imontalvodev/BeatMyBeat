package com.imontalvodev.beatmybeat.ui.network

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Caché de carátulas locales por bytes (no por número de entradas).
 * Indexado por URI del fichero (no por MediaStore id) y por tamaño de decodificación:
 * la miniatura de lista (160 px) y la del reproductor (512 px) no deben pisarse.
 */
object ArtworkCache {
    private const val MAX_BYTES = 12 * 1024 * 1024 // ~12 MiB

    private val cacheByUri = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = BitmapDecoding.byteCount(value)
    }

    fun get(uri: String, maxPx: Int): Bitmap? = cacheByUri.get(key(uri, maxPx))

    fun put(uri: String, maxPx: Int, bitmap: Bitmap) {
        if (uri.isNotBlank()) cacheByUri.put(key(uri, maxPx), bitmap)
    }

    private fun key(uri: String, maxPx: Int) = "$maxPx|$uri"

    fun clear() {
        cacheByUri.evictAll()
    }
}
