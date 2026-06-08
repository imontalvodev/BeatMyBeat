package com.imontalvodev.beatmybeat.ui.network

import com.imontalvodev.beatmybeat.core.Logger
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cliente para [LRCLIB](https://lrclib.net/docs).
 * Devuelve letra plana y/o LRC sincronizado para preparar karaoke futuro.
 *
 * Las búsquedas exigen **título + artista**; no se usa fallback solo por título (homónimos).
 * La duración refuerza el emparejamiento con tolerancia para variantes de álbum (±unos segundos).
 */
object LrcLibApi {

    private const val BASE_URL = "https://lrclib.net/api"
    private const val USER_AGENT = "BeatMyBeat/1.0 (Android; https://github.com/imontalvodev/beatmybeat)"
    private const val TIMEOUT_SEC = 20L
    private const val STRONG_MATCH_SCORE = 75
    private const val LOG_TAG = "LrcLibApi"

    private val client = AppHttpClient.withTimeouts(
        connectSeconds = TIMEOUT_SEC,
        readSeconds = TIMEOUT_SEC,
        callSeconds = 25,
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
        val artists = buildLyricsArtistCandidates(artistName, artistCandidates)
        val albums = listOf(
            albumName.trim(),
            UNKNOWN_ALBUM,
        ).filter { it.isNotBlank() }.distinct()

        if (titles.isEmpty() || artists.isEmpty()) {
            return failure("MissingFields", "Título o artista vacío")
        }

        Logger.d(LOG_TAG, "fetch title='${titles.first()}' artist='${artists.first()}' dur=${durationSeconds}s")

        // 1) Caché interna LRCLIB (requiere duración según documentación).
        if (durationSeconds > 0) {
            for ((title, artist, album) in metadataCombos(titles, artists, albums)) {
                fetchFromEndpoint("get-cached", title, artist, album, durationSeconds)
                    ?.let { return it }
            }
        }

        // 2) Búsqueda en base de datos (título + artista obligatorios).
        for (title in titles) {
            for (artist in artists) {
                searchAndFetch(title, artist, durationSeconds)
                    ?.let { return it }
            }
        }

        // 3) /api/get (fuentes externas) — requiere duración exacta (±2 s en servidor LRCLIB).
        if (durationSeconds > 0) {
            fetchFromEndpoint("get", titles.first(), artists.first(), albums.first(), durationSeconds)
                ?.let { return it }
        }

        Logger.d(LOG_TAG, "not found for '${titles.first()}' / '${artists.first()}'")
        return failure("NotFound", null)
    }

    /** Prioriza la combinación principal antes de variantes de título/artista/álbum. */
    private fun metadataCombos(
        titles: List<String>,
        artists: List<String>,
        albums: List<String>,
    ): Sequence<Triple<String, String, String>> = sequence {
        val seen = mutableSetOf<String>()
        for (title in titles) {
            for (artist in artists) {
                for (album in albums) {
                    val key = "$title|$artist|$album"
                    if (seen.add(key)) {
                        yield(Triple(title, artist, album))
                    }
                }
            }
        }
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
        // LRCLIB ya emparejó la petición en /get y /get-cached; confiar en 200 OK.
        return parseLyricsRecord(json, source = "lrclib", sourceUrl = url.toString())
    }

    private fun searchAndFetch(
        trackName: String,
        artistName: String,
        durationSeconds: Int,
    ): LyricsResponse? {
        if (artistName.isBlank()) return null

        val attempts = listOf(
            mapOf("track_name" to trackName, "artist_name" to artistName),
            mapOf("q" to "$artistName $trackName"),
            mapOf("q" to "$trackName $artistName"),
        )

        var bestRecord: JSONObject? = null
        var bestScore = Int.MIN_VALUE
        var bestUrl: String? = null

        for (params in attempts) {
            val url = "$BASE_URL/search".toHttpUrl().newBuilder().apply {
                params.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()

            val body = executeGetRaw(url.toString()) ?: continue
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
            executeGet(byIdUrl)?.let { parsed ->
                if (!jsonMatchesSearchResult(parsed, trackName, artistName, durationSeconds)) return null
                return parseLyricsRecord(parsed, source = "lrclib", sourceUrl = byIdUrl)
            }
        }
        return parseLyricsRecord(record, source = "lrclib", sourceUrl = bestUrl.orEmpty())
    }

    /** Validación solo para resultados de /api/search (evitar homónimos). */
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
