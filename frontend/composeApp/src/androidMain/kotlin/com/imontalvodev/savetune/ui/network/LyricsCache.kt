package com.imontalvodev.savetune.ui.network

import android.content.Context
import java.io.File
import java.security.MessageDigest

object LyricsCache {
    private const val DIR_NAME = "lyrics_cache"

    fun get(context: Context, title: String, artist: String): String? {
        val file = fileFor(context, title, artist)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun put(context: Context, title: String, artist: String, lyrics: String) {
        if (lyrics.isBlank()) return
        val file = fileFor(context, title, artist)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(lyrics)
        }
    }

    private fun fileFor(context: Context, title: String, artist: String): File {
        val dir = File(context.filesDir, DIR_NAME)
        val key = (title.trim() + "|" + artist.trim()).lowercase()
        val name = sha1Hex(key) + ".txt"
        return File(dir, name)
    }

    private fun sha1Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append("%02x".format(b))
        return sb.toString()
    }
}

