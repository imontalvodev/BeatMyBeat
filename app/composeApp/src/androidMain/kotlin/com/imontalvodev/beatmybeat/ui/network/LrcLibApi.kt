package com.imontalvodev.beatmybeat.ui.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Cliente para [LRCLIB](https://lrclib.net/docs).
 * Devuelve letra plana y/o LRC sincronizado para preparar karaoke futuro.
 */
object LrcLibApi {

    private const val BASE_URL = "https://lrclib.net/api"
    private const val USER_AGENT = "BeatMyBeat/1.0 (Android; https://github.com/imontalvodev/beatmybeat)"
    private const val TIMEOUT_SEC = 20L
    private const val DURATION_TOLERANCE_SEC = 3

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    /**
     * Intenta obtener letras: primero caché interna LRCLIB, luego búsqueda en fuentes externas.
     */
    fun fetchLyrics(
        trackName: String,
        artistName: String,
        albumName: String,
        durationSeconds: Int,
    ): LyricsResponse {
        val safeTrack = trackName.trim()
        val safeArtist = cleanArtistForLyrics(artistName).trim()
        val safeAlbum = albumName.trim().ifBlank { UNKNOWN_ALBUM }
        if (safeTrack.isBlank() || safeArtist.isBlank()) {
            return failure("MissingFields", "Título o artista vacío")
        }

        fetchFromEndpoint("get-cached", safeTrack, safeArtist, safeAlbum, durationSeconds)
            ?.let { return it }

        if (durationSeconds > 0) {
            fetchFromEndpoint("get", safeTrack, safeArtist, safeAlbum, durationSeconds)
                ?.let { return it }
        }

        searchAndFetch(safeTrack, safeArtist, durationSeconds)
            ?.let { return it }

        return failure("NotFound", null)
    }

    private fun fetchFromEndpoint(
        path: String,
        trackName: String,
        artistName: String,
        albumName: String,
        durationSeconds: Int,
    ): LyricsResponse? {
        val url = "$BASE_URL/$path".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", trackName)
            .addQueryParameter("artist_name", artistName)
            .addQueryParameter("album_name", albumName)
            .apply {
                if (durationSeconds > 0) {
                    addQueryParameter("duration", durationSeconds.toString())
                }
            }
            .build()

        val json = executeGet(url.toString()) ?: return null
        return parseLyricsRecord(json, source = "lrclib", sourceUrl = url.toString())
    }

    private fun searchAndFetch(
        trackName: String,
        artistName: String,
        durationSeconds: Int,
    ): LyricsResponse? {
        val url = "$BASE_URL/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", trackName)
            .addQueryParameter("artist_name", artistName)
            .build()

        val body = executeGetRaw(url.toString()) ?: return null
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return null
        if (arr.length() == 0) return null

        var best: JSONObject? = null
        var bestDelta = Int.MAX_VALUE
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (durationSeconds > 0) {
                val dur = item.optInt("duration", -1)
                if (dur > 0) {
                    val delta = abs(dur - durationSeconds)
                    if (delta <= DURATION_TOLERANCE_SEC && delta < bestDelta) {
                        bestDelta = delta
                        best = item
                    }
                }
            } else if (best == null) {
                best = item
            }
        }
        if (best == null && durationSeconds <= 0 && arr.length() > 0) {
            best = arr.optJSONObject(0)
        }
        if (best == null && arr.length() > 0) {
            best = arr.optJSONObject(0)
        }
        val record = best ?: return null

        val id = record.optLong("id", 0L)
        if (id > 0L) {
            val byIdUrl = "$BASE_URL/get/$id"
            executeGet(byIdUrl)?.let { parsed ->
                return parseLyricsRecord(parsed, source = "lrclib", sourceUrl = byIdUrl)
            }
        }
        return parseLyricsRecord(record, source = "lrclib", sourceUrl = url.toString())
    }

    private fun parseLyricsRecord(
        json: JSONObject,
        source: String,
        sourceUrl: String,
    ): LyricsResponse? {
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() }
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
        val displayPlain = plain ?: synced?.let { LrcParser.toPlainText(it) }
        if (displayPlain.isNullOrBlank()) return null

        val lrclibId = json.optLong("id", 0L).takeIf { it > 0L }
        return LyricsResponse(
            success = true,
            lyrics = displayPlain,
            syncedLrc = synced,
            lrclibId = lrclibId,
            source = source,
            sourceUrl = sourceUrl,
            error = null,
            message = null,
        )
    }

    private fun executeGet(url: String): JSONObject? {
        val body = executeGetRaw(url) ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun executeGetRaw(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) return null
                body.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun failure(error: String, message: String?): LyricsResponse =
        LyricsResponse(
            success = false,
            lyrics = "",
            syncedLrc = null,
            lrclibId = null,
            source = null,
            sourceUrl = null,
            error = error,
            message = message,
        )

    private const val UNKNOWN_ALBUM = "Unknown Album"
}
