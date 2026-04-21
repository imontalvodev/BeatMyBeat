package com.imontalvodev.beatmybeat.ui.network

import android.graphics.Bitmap
import android.util.LruCache

object RemoteArtworkCache {
    private const val MAX_ITEMS = 24

    private val cache = object : LruCache<String, Bitmap>(MAX_ITEMS) {}

    fun get(url: String): Bitmap? = cache.get(url)

    fun put(url: String, bitmap: Bitmap) {
        if (url.isBlank() || bitmap == null) return
        cache.put(url, bitmap)
    }
}

