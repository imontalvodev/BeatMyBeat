package com.imontalvodev.beatmybeat.ui.network

import android.os.SystemClock
import com.imontalvodev.beatmybeat.core.Logger
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cliente para [LRCLIB](https://lrclib.net/docs).
 * Devuelve letra plana y/o LRC sincronizado para preparar karaoke futuro.
 *
 * Las búsquedas exigen **título + artista**; no se usa fallback solo por título (homónimos).
 * Las variantes y peticiones HTTP están acotadas para no bloquear el dispositivo.
 */
object LrcLibApi {

    private const val BASE_URL = "https://lrclib.net/api"
    private const val USER_AGENT = "BeatMyBeat/1.0 (Android; https://github.com/imontalvodev/beatmybeat)"
    private const val STRONG_MATCH_SCORE = 75
    private const val LOG_TAG = "LrcLibApi"
    private const val MAX_TITLE_VARIANTS = 2
    private const val MAX_ARTIST_VARIANTS = 2

    /**
     * Tope acumulado para toda la búsqueda de una pista (todas las combinaciones
     * título×artista). Sin esto, el peor caso encadena hasta ~4 combinaciones × varias
     * llamadas HTTP cada una (con timeouts individuales de hasta 25s) y puede bloquear
     * el lote de letras varios minutos por una sola pista lenta.
     */
    private const val MAX_FETCH_BUDGET_MS = 20_000L

    /** /api/get puede ir a fuentes externas: timeout largo pero acotado. */
    private val getClient = AppHttpClient.withTimeouts(
        connectSeconds = 15,
        readSeconds = 20,
        callSeconds = 25,
    )
    /** Caché y búsqueda: fallar rápido si no hay match. */
    private val fastClient = AppHttpClient.withTimeouts(
        connectSeconds = 8,
        readSeconds = 10,
        callSeconds = 12,
    )

    fun fetchLyrics(
        trackName: String,
        artistName: String,
        albumName: String,
        durationSeconds: Int,
        titleCandidates: List<String> = emptyList(),
        artistCandidates: List<String> = emptyList(),
    ): LyricsResponse {
        val titles = (listOf(trackName.trim()) + titleCandidates)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_TITLE_VARIANTS)
        val artists = buildLyricsArtistCandidates(artistName, artistCandidates)
            .take(MAX_ARTIST_VARIANTS)
        val album = albumName.trim().ifBlank { UNKNOWN_ALBUM }

        if (titles.isEmpty() || artists.isEmpty()) {
            return failure("MissingFields", "Título o artista vacío")
        }

        Logger.d(LOG_TAG, "fetch title='${titles.first()}' artist='${artists.first()}' dur=${durationSeconds}s")

        val deadline = SystemClock.elapsedRealtime() + MAX_FETCH_BUDGET_MS

        for ((titleIndex, title) in titles.withIndex()) {
            for ((artistIndex, artist) in artists.withIndex()) {
                if (SystemClock.elapsedRealtime() >= deadline) {
                    Logger.w(LOG_TAG, "fetchLyrics: presupuesto de tiempo agotado, abortando búsqueda")
                    return failure("Timeout", null)
                }
                if (durationSeconds > 0) {
                    fetchFromEndpoint(fastClient, "get-cached", title, artist, album, durationSeconds)
                        ?.let { return it }
                    if (album != UNKNOWN_ALBUM) {
                        fetchFromEndpoint(fastClient, "get-cached", title, artist, UNKNOWN_ALBUM, durationSeconds)
                            ?.let { return it }
                    }
                }

                searchAndFetch(title, artist, durationSeconds, deadline)?.let { return it }

                if (SystemClock.elapsedRealtime() >= deadline) {
                    Logger.w(LOG_TAG, "fetchLyrics: presupuesto de tiempo agotado, abortando búsqueda")
                    return failure("Timeout", null)
                }

                // /api/get es lento: solo una vez con la combinación principal.
                if (titleIndex == 0 && artistIndex == 0 && durationSeconds > 0) {
                    fetchFromEndpoint(getClient, "get", title, artist, album, durationSeconds)
                        ?.let { return it }
                }
            }
        }

        Logger.d(LOG_TAG, "not found for '${titles.first()}' / '${artists.first()}'")
        return failure("NotFound", null)
    }

    private fun fetchFromEndpoint(
        client: OkHttpClient,
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

        val json = executeGet(client, url.toString()) ?: return null
        return parseLyricsRecord(json, source = "lrclib", sourceUrl = url.toString())
    }

    private fun searchAndFetch(
        trackName: String,
        artistName: String,
        durationSeconds: Int,
        deadline: Long,
    ): LyricsResponse? {
        if (artistName.isBlank()) return null

        val attempts = listOf(
            mapOf("track_name" to trackName, "artist_name" to artistName),
            mapOf("q" to "$artistName $trackName"),
        )

        var bestRecord: JSONObject? = null
        var bestScore = Int.MIN_VALUE
        var bestUrl: String? = null

        for (params in attempts) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                // Un intento previo (p. ej. un timeout de red) ya agotó el presupuesto: no merece
                // la pena lanzar otra petición /search igual de lenta, mejor dejar hueco para
                // /api/get o la siguiente variante de título/artista.
                break
            }
            val url = "$BASE_URL/search".toHttpUrl().newBuilder().apply {
                params.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()

            val body = executeGetRaw(fastClient, url.toString()) ?: continue
            val arr = runCatching { JSONArray(body) }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val score = scoreSearchResult(item, trackName, artistName, durationSeconds)
                if (score > bestScore) {
                    bestScore = score
                    bestRecord = item
                    bestUrl = url.toString()
                }
            }
            if (bestScore >= STRONG_MATCH_SCORE) break
        }

        val record = bestRecord ?: return null
        if (bestScore == Int.MIN_VALUE || !jsonMatchesSearchResult(record, trackName, artistName, durationSeconds)) {
            return null
        }

        val id = record.optLong("id", 0L)
        if (id > 0L) {
            val byIdUrl = "$BASE_URL/get/$id"
            executeGet(fastClient, byIdUrl)?.let { parsed ->
                if (!jsonMatchesSearchResult(parsed, trackName, artistName, durationSeconds)) return null
                return parseLyricsRecord(parsed, source = "lrclib", sourceUrl = byIdUrl)
            }
        }
        return parseLyricsRecord(record, source = "lrclib", sourceUrl = bestUrl.orEmpty())
    }

    private fun jsonMatchesSearchResult(
        json: JSONObject,
        trackName: String,
        artistName: String,
        durationSeconds: Int,
    ): Boolean = isAcceptableLrcCandidate(
        expectedTitle = trackName,
        expectedArtist = artistName,
        expectedDurationSec = durationSeconds,
        candidate = json.toLyricsCandidate(),
    )

    private fun scoreSearchResult(
        item: JSONObject,
        trackName: String,
        artistName: String,
        durationSeconds: Int,
    ): Int = scoreLrcCandidate(
        expectedTitle = trackName,
        expectedArtist = artistName,
        expectedDurationSec = durationSeconds,
        candidate = item.toLyricsCandidate(),
    )

    private fun JSONObject.toLyricsCandidate(): LrcLyricsCandidate = LrcLyricsCandidate(
        trackName = readField(this, "trackName", "track_name"),
        artistName = readField(this, "artistName", "artist_name"),
        durationSeconds = optInt("duration", -1),
        hasSyncedLyrics = readField(this, "syncedLyrics", "synced_lyrics").isNotBlank(),
        hasPlainLyrics = readField(this, "plainLyrics", "plain_lyrics").isNotBlank(),
    )

    private fun readField(json: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = json.optString(key).trim()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun parseLyricsRecord(
        json: JSONObject,
        source: String,
        sourceUrl: String,
    ): LyricsResponse? {
        val plain = readField(json, "plainLyrics", "plain_lyrics").takeIf { it.isNotBlank() }
        val synced = readField(json, "syncedLyrics", "synced_lyrics").takeIf { it.isNotBlank() }
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

    private fun executeGet(client: OkHttpClient, url: String): JSONObject? {
        val body = executeGetRaw(client, url) ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun executeGetRaw(client: OkHttpClient, url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    Logger.w(LOG_TAG, "HTTP ${res.code} for $url")
                    return null
                }
                body.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "request failed for $url", e)
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
