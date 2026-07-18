package com.imontalvodev.beatmybeat.ui.network

import android.content.Context
import com.imontalvodev.beatmybeat.core.Logger
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Caché de letras: texto plano + LRC opcional para sincronización futura.
 */
data class LyricsCacheEntry(
    val plain: String,
    val syncedLrc: String? = null,
    val source: String? = null,
    val lrclibId: Long? = null,
    /**
     * `true` si LRCLIB llegó a contestar (con letra o con un "no la tengo"). `false` cuando la
     * entrada se guardó sin haber podido preguntarle — p. ej. letra plana de lyrics.ovh tras un
     * timeout. Sin esta marca, una caída pasajera de red condenaba a la pista a texto plano para
     * siempre: la caché respondía y LRCLIB no se volvía a consultar nunca.
     */
    val lrclibChecked: Boolean = false,
) {
    fun hasAnyLyrics(): Boolean = plain.isNotBlank() || !syncedLrc.isNullOrBlank()

    fun displayPlain(): String {
        if (plain.isNotBlank()) return plain
        return syncedLrc?.let { LrcParser.toPlainText(it) }.orEmpty()
    }

    fun toResponse(): LyricsResponse = LyricsResponse(
        success = true,
        lyrics = displayPlain(),
        syncedLrc = syncedLrc,
        lrclibId = lrclibId,
        source = source,
        sourceUrl = null,
        error = null,
        message = null,
    )

    /** Líneas parseadas listas para karaoke (cuando se implemente en UI). */
    fun parsedSyncedLines(): List<LrcLine> =
        syncedLrc?.let { LrcParser.parse(it) }.orEmpty()
}

object LyricsCache {
    private const val DIR_NAME = "lyrics_cache"

    /** Compatibilidad: solo texto plano. */
    fun get(context: Context, title: String, artist: String): String? =
        getEntry(context, title, artist)?.displayPlain()?.takeIf { it.isNotBlank() }

    fun getSyncedLrc(context: Context, title: String, artist: String): String? =
        getEntry(context, title, artist)?.syncedLrc?.takeIf { it.isNotBlank() }

    fun getEntry(context: Context, title: String, artist: String): LyricsCacheEntry? {
        val jsonFile = jsonFileFor(context, title, artist)
        if (jsonFile.exists()) {
            return runCatching {
                val obj = JSONObject(jsonFile.readText())
                val plain = obj.optString("plain", "")
                val synced = obj.optString("syncedLrc").takeIf { it.isNotBlank() }
                val source = obj.optString("source").takeIf { it.isNotBlank() }
                val id = obj.optLong("lrclibId", 0L).takeIf { it > 0L }
                LyricsCacheEntry(
                    plain = plain,
                    syncedLrc = synced,
                    source = source,
                    lrclibId = id,
                    // Las entradas antiguas no llevan la marca: se tratan como "no consultado",
                    // así que se reintenta LRCLIB una vez y a partir de ahí ya queda marcada.
                    lrclibChecked = obj.optBoolean("lrclibChecked", false),
                ).takeIf { it.hasAnyLyrics() }
            }.getOrNull()
        }
        val legacy = legacyFileFor(context, title, artist)
        if (!legacy.exists()) return null
        return runCatching {
            legacy.readText().takeIf { it.isNotBlank() }?.let { LyricsCacheEntry(plain = it) }
        }.getOrNull()
    }

    fun put(context: Context, title: String, artist: String, lyrics: String) {
        if (lyrics.isBlank()) return
        putEntry(context, title, artist, LyricsCacheEntry(plain = lyrics))
    }

    fun putEntry(context: Context, title: String, artist: String, entry: LyricsCacheEntry) {
        if (!entry.hasAnyLyrics()) return
        val jsonFile = jsonFileFor(context, title, artist)
        runCatching {
            jsonFile.parentFile?.mkdirs()
            val obj = JSONObject().apply {
                put("plain", entry.plain.ifBlank { entry.displayPlain() })
                entry.syncedLrc?.let { put("syncedLrc", it) }
                entry.source?.let { put("source", it) }
                entry.lrclibId?.let { put("lrclibId", it) }
                put("lrclibChecked", entry.lrclibChecked)
            }
            jsonFile.writeText(obj.toString())
            legacyFileFor(context, title, artist).delete()
        }.onFailure { error ->
            Logger.e("LyricsCache", "No se pudo escribir caché de letras para '$title' / '$artist'", error)
        }
    }

    fun putFromResponse(
        context: Context,
        title: String,
        artist: String,
        response: LyricsResponse,
        /** `true` solo si LRCLIB llegó a responder; ver [LyricsCacheEntry.lrclibChecked]. */
        lrclibChecked: Boolean = false,
    ) {
        if (!response.success) return
        putEntry(
            context,
            title,
            artist,
            LyricsCacheEntry(
                plain = response.lyrics,
                syncedLrc = response.syncedLrc,
                source = response.source,
                lrclibId = response.lrclibId,
                lrclibChecked = lrclibChecked,
            ),
        )
    }

    /** Elimina la entrada en caché (JSON y legado .txt) para título + artista. */
    fun remove(context: Context, title: String, artist: String) {
        jsonFileFor(context, title, artist).delete()
        legacyFileFor(context, title, artist).delete()
    }

    fun removeAll(context: Context, titles: Collection<String>, artists: Collection<String>) {
        for (title in titles) {
            for (artist in artists) {
                remove(context, title, artist)
            }
        }
    }

    private fun jsonFileFor(context: Context, title: String, artist: String): File {
        val dir = File(context.filesDir, DIR_NAME)
        return File(dir, sha1Hex(cacheKey(title, artist)) + ".json")
    }

    private fun legacyFileFor(context: Context, title: String, artist: String): File {
        val dir = File(context.filesDir, DIR_NAME)
        return File(dir, sha1Hex(cacheKey(title, artist)) + ".txt")
    }

    private fun cacheKey(title: String, artist: String): String =
        (title.trim() + "|" + artist.trim()).lowercase()

    private fun sha1Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append("%02x".format(b))
        return sb.toString()
    }
}
