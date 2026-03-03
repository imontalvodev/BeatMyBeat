package com.imontalvodev.savetune

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class PlaylistInfo(
    val name: String,
    val totalTracks: Int,
    val songs: List<DownloadSong>
)

data class YoutubeVideo(
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String?
)

object ApiClient {
    // El scraper con Selenium tarda bastante; ampliamos timeouts
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS) // sin límite global; manda readTimeout
        .build()

    fun fetchPlaylist(spotifyUrl: String): PlaylistInfo {
        val encodedUrl = URLEncoder.encode(spotifyUrl, "UTF-8")
        val url = "${ApiConfig.BASE_URL}/playlist?url=$encodedUrl"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Error ${response.code}: ${response.message}")
            }

            val bodyString = response.body?.string()
                ?: throw IllegalStateException("Respuesta vacía del servidor")

            Log.d("ApiClient", "Respuesta playlist: $bodyString")
            val json = JSONObject(bodyString)

            val playlistObj = json.getJSONObject("playlist")
            val name = playlistObj.getString("name")
            val totalTracks = playlistObj.getInt("totalTracks")

            val songsJson = json.getJSONArray("songs")
            val songs = mutableListOf<DownloadSong>()
            for (i in 0 until songsJson.length()) {
                val s = songsJson.getJSONObject(i)
                songs.add(
                    DownloadSong(
                        id = s.optString("id", null),
                        title = s.optString("title"),
                        artist = s.optString("artist"),
                        album = s.optString("album", ""),
                        durationSeconds = s.optInt("duration", 0),
                        thumbnailUrl = s.optString("imageUrl", null)
                    )
                )
            }

            return PlaylistInfo(
                name = name,
                totalTracks = totalTracks,
                songs = songs
            )
        }
    }

    fun searchYoutube(query: String): YoutubeVideo? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "${ApiConfig.BASE_URL}/search-youtube?query=$encodedQuery"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Error ${response.code}: ${response.message}")
            }

            val bodyString = response.body?.string()
                ?: throw IllegalStateException("Respuesta vacía del servidor")

            Log.d("ApiClient", "Respuesta search-youtube: $bodyString")
            val json = JSONObject(bodyString)

            val success = json.optBoolean("success", false)
            val videoObj = json.optJSONObject("video") ?: return null
            val id = videoObj.optString("id", "")
            if (!success || id.isBlank()) {
                return null
            }

            return YoutubeVideo(
                id = id,
                title = videoObj.optString("title", ""),
                url = videoObj.optString("url", ""),
                thumbnail = videoObj.optString("thumbnail", null)
            )
        }
    }
}

