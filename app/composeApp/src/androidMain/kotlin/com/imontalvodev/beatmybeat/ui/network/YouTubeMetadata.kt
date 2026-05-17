package com.imontalvodev.beatmybeat.ui.network

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class YouTubeSongMetadata(
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
)

fun fetchYouTubeSongMetadata(videoId: String): YouTubeSongMetadata {
    val fallback = YouTubeSongMetadata(
        title = "YouTube Track $videoId",
        artist = "Unknown artist",
        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
    )

    return runCatching {
        val targetUrl = "https://www.youtube.com/watch?v=$videoId"
        val oEmbed = "https://www.youtube.com/oembed?url=$targetUrl&format=json"
        val req = Request.Builder().url(oEmbed).get().build()
        OkHttpClient().newCall(req).execute().use { res ->
            if (!res.isSuccessful) return fallback
            val body = res.body.string()
            val json = JSONObject(body)
            val rawArtist = json.optString("author_name", fallback.artist).ifBlank { fallback.artist }
            YouTubeSongMetadata(
                title = json.optString("title", fallback.title).ifBlank { fallback.title },
                artist = cleanArtistForLyrics(rawArtist),
                thumbnailUrl = json.optString("thumbnail_url", fallback.thumbnailUrl).ifBlank { fallback.thumbnailUrl },
            )
        }
    }.getOrElse { fallback }
}
