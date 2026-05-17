package com.imontalvodev.beatmybeat.ui.network

import android.graphics.Bitmap
import android.util.LruCache

object RemoteArtworkCache {
    private const val MAX_BYTES = 4 * 1024 * 1024 // ~4 MiB

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = BitmapDecoding.byteCount(value)
    }

    fun get(url: String): Bitmap? = cache.get(url)

    fun put(url: String, bitmap: Bitmap) {
        if (url.isBlank()) return
        cache.put(url, bitmap)
    }
}
