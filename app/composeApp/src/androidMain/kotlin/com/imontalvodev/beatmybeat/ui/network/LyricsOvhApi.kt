package com.imontalvodev.beatmybeat.ui.network

import android.net.Uri
import okhttp3.Request
import org.json.JSONObject

object LyricsOvhApi {
    private val client = AppHttpClient.withTimeouts(
        connectSeconds = 20,
        readSeconds = 60,
        writeSeconds = 60,
        callSeconds = 90,
    )

    fun fetch(title: String, artist: String): LyricsResponse {
        val safeArtist = cleanArtistForLyrics(artist)
            .ifBlank {
                return LyricsResponse(false, "", null, null, null, null, "MissingArtist", null)
            }
        val safeTitle = title.trim()
            .ifBlank {
                return LyricsResponse(false, "", null, null, null, null, "MissingTitle", null)
            }
        val encArtist = Uri.encode(safeArtist)
        val encTitle = Uri.encode(safeTitle)
        val url = "https://api.lyrics.ovh/v1/$encArtist/$encTitle"
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "BeatMyBeat/1.0 (Android)")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                if (json == null) {
                    return LyricsResponse(
                        success = false,
                        lyrics = "",
                        syncedLrc = null,
                        lrclibId = null,
                        source = null,
                        sourceUrl = null,
                        error = if (!res.isSuccessful) "Http${res.code}" else "InvalidJson",
                        message = body.take(200).ifBlank { null },
                    )
                }
                if (!res.isSuccessful) {
                    val err = json.optString("error").ifBlank { "HTTP ${res.code}" }
                    return LyricsResponse(false, "", null, null, null, null, "HttpError", err)
                }
                val lyrics = json.optString("lyrics", "")
                if (lyrics.isNotBlank()) {
                    LyricsResponse(true, lyrics, null, null, "lyrics.ovh", url, null, null)
                } else {
                    LyricsResponse(
                        success = false,
                        lyrics = "",
                        syncedLrc = null,
                        lrclibId = null,
                        source = null,
                        sourceUrl = null,
                        error = "NoLyrics",
                        message = json.optString("error").ifBlank { null },
                    )
                }
            }
        } catch (e: Exception) {
            LyricsResponse(false, "", null, null, null, null, "NetworkError", e.message)
        }
    }
}
