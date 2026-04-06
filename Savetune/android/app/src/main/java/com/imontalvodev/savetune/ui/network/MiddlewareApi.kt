package com.imontalvodev.savetune.ui.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class LyricsResponse(
    val success: Boolean,
    val lyrics: String,
    val source: String?,
    val sourceUrl: String?,
    val error: String?,
    val message: String?,
)

data class PlaylistMeta(
    val name: String,
    val totalTracks: Int,
)

data class PlaylistSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val imageUrl: String,
    val durationSeconds: Int,
)

data class PlaylistResponse(
    val success: Boolean,
    val playlist: PlaylistMeta?,
    val songs: List<PlaylistSong>,
    val error: String?,
    val message: String?,
)

data class SongSuggestion(
    val title: String,
    val artist: String,
)

data class SongSuggestionsResponse(
    val success: Boolean,
    val results: List<SongSuggestion>,
    val error: String?,
    val message: String?,
)

object MiddlewareApi {
    private val client: OkHttpClient = OkHttpClient.Builder()
        // Playlist/lyrics pueden tardar (yt-dlp / letras.com)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    fun fetchLyrics(baseUrl: String, title: String, artist: String): LyricsResponse {
        val url = baseUrl.trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegments("api/lyrics")
            .addQueryParameter("title", title)
            .addQueryParameter("artist", artist)
            .build()

        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (json == null) {
                return LyricsResponse(
                    success = false,
                    lyrics = "",
                    source = null,
                    sourceUrl = null,
                    error = "InvalidJson",
                    message = "Invalid JSON from server",
                )
            }
            return LyricsResponse(
                success = json.optBoolean("success", false),
                lyrics = json.optString("lyrics", ""),
                source = json.opt("source")?.toString(),
                sourceUrl = json.opt("sourceUrl")?.toString(),
                error = json.opt("error")?.toString(),
                message = json.opt("message")?.toString(),
            )
        }
    }

    fun fetchPlaylist(baseUrl: String, playlistUrl: String): PlaylistResponse {
        val url = baseUrl.trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegments("api/playlist")
            .addQueryParameter("url", playlistUrl)
            .build()

        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (json == null) {
                return PlaylistResponse(
                    success = false,
                    playlist = null,
                    songs = emptyList(),
                    error = "InvalidJson",
                    message = "Invalid JSON from server",
                )
            }

            val playlistObj = json.optJSONObject("playlist")
            val playlist = if (playlistObj != null) {
                PlaylistMeta(
                    name = playlistObj.optString("name", ""),
                    totalTracks = playlistObj.optInt("totalTracks", 0),
                )
            } else {
                null
            }

            val songsArray: JSONArray = json.optJSONArray("songs") ?: JSONArray()
            val songs = buildList {
                for (i in 0 until songsArray.length()) {
                    val s = songsArray.optJSONObject(i) ?: continue
                    add(
                        PlaylistSong(
                            id = s.optString("id", ""),
                            title = s.optString("title", ""),
                            artist = s.optString("artist", ""),
                            album = s.optString("album", ""),
                            imageUrl = s.optString("imageUrl", ""),
                            durationSeconds = s.optInt("duration", 0),
                        )
                    )
                }
            }

            return PlaylistResponse(
                success = json.optBoolean("success", false),
                playlist = playlist,
                songs = songs,
                error = json.opt("error")?.toString(),
                message = json.opt("message")?.toString(),
            )
        }
    }

    fun fetchSongSuggestions(baseUrl: String, query: String, limit: Int = 10): SongSuggestionsResponse {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return SongSuggestionsResponse(
                success = false,
                results = emptyList(),
                error = "EmptyQuery",
                message = "Introduce al menos parte del título o del artista.",
            )
        }

        val url =
            "${baseUrl.trimEnd('/')}/api/search-song-suggestions".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("query", trimmedQuery)
                ?.addQueryParameter("limit", limit.toString())
                ?.build()
                ?: return SongSuggestionsResponse(
                    success = false,
                    results = emptyList(),
                    error = "BadUrl",
                    message = "URL del middleware no válida: ${baseUrl.take(80)}",
                )

        return try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                if (json == null) {
                    return SongSuggestionsResponse(
                        success = false,
                        results = emptyList(),
                        error = "InvalidJson",
                        message = "Respuesta no JSON del servidor (HTTP ${res.code})",
                    )
                }

                val arr = json.optJSONArray("results") ?: JSONArray()
                val results = buildList {
                    for (i in 0 until arr.length()) {
                        when (val el = arr.opt(i)) {
                            is JSONObject -> add(
                                SongSuggestion(
                                    title = el.optString("title", "").trim(),
                                    artist = el.optString("artist", "").trim(),
                                ),
                            )
                            is String -> {
                                val line = el.trim()
                                if (line.isNotBlank()) {
                                    val parts = line.split(" - ", limit = 2)
                                    if (parts.size == 2) {
                                        add(SongSuggestion(title = parts[1].trim(), artist = parts[0].trim()))
                                    } else {
                                        add(SongSuggestion(title = line, artist = ""))
                                    }
                                }
                            }
                            else -> continue
                        }
                    }
                }

                SongSuggestionsResponse(
                    success = json.optBoolean("success", false),
                    results = results,
                    error = json.opt("error")?.toString(),
                    message = json.opt("message")?.toString(),
                )
            }
        } catch (e: IOException) {
            SongSuggestionsResponse(
                success = false,
                results = emptyList(),
                error = "NetworkError",
                message = e.message ?: "No hay conexión con el middleware",
            )
        } catch (e: Exception) {
            SongSuggestionsResponse(
                success = false,
                results = emptyList(),
                error = "UnexpectedError",
                message = e.message ?: e.javaClass.simpleName,
            )
        }
    }
}

