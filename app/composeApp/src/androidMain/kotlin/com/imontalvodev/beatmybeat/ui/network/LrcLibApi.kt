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
    private const val DURATION_TOLERANCE_SEC = 5

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

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
        val artists = (listOf(cleanArtistForLyrics(artistName).trim(), artistName.trim()) + artistCandidates)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val albums = listOf(
            albumName.trim(),
            UNKNOWN_ALBUM,
        ).filter { it.isNotBlank() }.distinct()

        if (titles.isEmpty() || artists.isEmpty()) {
            return failure("MissingFields", "Título o artista vacío")
        }

        // 1) Rápido: solo caché interna de LRCLIB (sin fuentes externas).
        for ((title, artist, album) in metadataCombos(titles, artists, albums)) {
            fetchFromEndpoint("get-cached", title, artist, album, durationSeconds)
                ?.let { return it }
        }

        // 2) Búsqueda en base de datos (suele ser rápida).
        for (title in titles) {
            for (artist in artists) {
                searchAndFetch(title, artist, durationSeconds)
                    ?.let { return it }
            }
        }
        for (title in titles) {
            searchAndFetch(title, artistName = "", durationSeconds)
                ?.let { return it }
        }

        // 3) Lento: /api/get puede ir a fuentes externas — una sola vez con los metadatos principales.
        if (durationSeconds > 0) {
            fetchFromEndpoint("get", titles.first(), artists.first(), albums.first(), durationSeconds)
                ?.let { return it }
        }

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
        return parseLyricsRecord(json, source = "lrclib", sourceUrl = url.toString())
    }

    private fun searchAndFetch(
        trackName: String,
        artistName: String,
        durationSeconds: Int,
    ): LyricsResponse? {
        val attempts = buildList {
            if (artistName.isNotBlank()) {
                add(mapOf("track_name" to trackName, "artist_name" to artistName))
                add(mapOf("q" to "$artistName $trackName"))
            }
            add(mapOf("track_name" to trackName))
            if (artistName.isNotBlank()) {
                add(mapOf("q" to "$trackName $artistName"))
            }
        }

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
            // Coincidencia clara: no seguir probando más búsquedas.
            if (bestScore >= 75) break
        }

        val record = bestRecord ?: return null
        val id = record.optLong("id", 0L)
        if (id > 0L) {
            val byIdUrl = "$BASE_URL/get/$id"
            executeGet(byIdUrl)?.let { parsed ->
                return parseLyricsRecord(parsed, source = "lrclib", sourceUrl = byIdUrl)
            }
        }
        return parseLyricsRecord(record, source = "lrclib", sourceUrl = bestUrl.orEmpty())
    }

    private fun scoreSearchResult(
        item: JSONObject,
        trackName: String,
        artistName: String,
        durationSeconds: Int,
    ): Int {
        val resultTrack = readField(item, "trackName", "track_name")
        val resultArtist = readField(item, "artistName", "artist_name")
        var score = 0

        score += titleMatchScore(trackName, resultTrack) * 4
        if (artistName.isNotBlank()) {
            score += titleMatchScore(artistName, resultArtist) * 3
        }

        if (durationSeconds > 0) {
            val dur = item.optInt("duration", -1)
            if (dur > 0) {
                val delta = abs(dur - durationSeconds)
                score += when {
                    delta <= 2 -> 40
                    delta <= DURATION_TOLERANCE_SEC -> 25
                    delta <= 12 -> 8
                    else -> -20
                }
            }
        }

        val hasSynced = readField(item, "syncedLyrics", "synced_lyrics").isNotBlank()
        val hasPlain = readField(item, "plainLyrics", "plain_lyrics").isNotBlank()
        if (hasSynced) score += 15
        if (hasPlain) score += 5

        return score
    }

    private fun titleMatchScore(expected: String, actual: String): Int {
        val a = normalizeForMatch(expected)
        val b = normalizeForMatch(actual)
        if (a.isBlank() || b.isBlank()) return 0
        if (a == b) return 30
        if (b.contains(a) || a.contains(b)) return 20
        val aTokens = a.split(' ').filter { it.length > 2 }
        val bTokens = b.split(' ').filter { it.length > 2 }
        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0
        val overlap = aTokens.count { token -> bTokens.any { it.contains(token) || token.contains(it) } }
        return (overlap * 10).coerceAtMost(25)
    }

    private fun normalizeForMatch(raw: String): String =
        raw.lowercase()
            .replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ")
            .replace(Regex("(?i)\\b(feat\\.?|ft\\.?|featuring|remaster(ed)?|official|audio|video|live|lyrics|prod\\.?|produced)\\b"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

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
