package com.imontalvodev.savetune.ui.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class YouTubeSearchResult(
    val videoId: String,
    val title: String,
    val channel: String,
    val durationText: String,
    val thumbnailUrl: String,
)

object YouTubeSearchClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val INNERTUBE_URL =
        "https://www.youtube.com/youtubei/v1/search?key=$INNERTUBE_KEY&prettyPrint=false"

    fun search(query: String, limit: Int = 10): List<YouTubeSearchResult> {
        val body = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240101.00.00")
                    put("hl", "es")
                    put("gl", "ES")
                })
            })
            put("query", "$query official audio")
        }.toString()

        val request = Request.Builder()
            .url(INNERTUBE_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("X-YouTube-Client-Name", "1")
            .header("X-YouTube-Client-Version", "2.20240101.00.00")
            .header("Content-Type", "application/json")
            .header("Accept-Language", "es-ES,es;q=0.9")
            .build()

        val responseBody = client.newCall(request).execute().use { res ->
            if (!res.isSuccessful) {
                val errorBody = res.body?.string()?.take(200) ?: ""
                throw Exception("YouTube search HTTP ${res.code}: $errorBody")
            }
            res.body?.string() ?: throw Exception("Empty response from YouTube")
        }

        return parseResults(responseBody, limit)
    }

    private fun parseResults(json: String, limit: Int): List<YouTubeSearchResult> {
        val results = mutableListOf<YouTubeSearchResult>()
        try {
            val root = JSONObject(json)
            val contents = root
                .optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?: root
                    .optJSONObject("contents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
                ?: return emptyList()

            for (i in 0 until contents.length()) {
                val section = contents.optJSONObject(i)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents")
                    ?: continue

                for (j in 0 until section.length()) {
                    if (results.size >= limit) break
                    val videoRenderer = section.optJSONObject(j)
                        ?.optJSONObject("videoRenderer")
                        ?: continue

                    val videoId = videoRenderer.optString("videoId", "")
                    if (videoId.length != 11) continue

                    val title = videoRenderer
                        .optJSONObject("title")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text", "") ?: ""

                    val channel = videoRenderer
                        .optJSONObject("ownerText")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text", "") ?: ""

                    val duration = videoRenderer
                        .optJSONObject("lengthText")
                        ?.optString("simpleText", "") ?: ""

                    val thumbnail = videoRenderer
                        .optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                        ?.let { thumbs ->
                            var best = ""
                            for (t in 0 until thumbs.length()) {
                                best = thumbs.optJSONObject(t)?.optString("url", "") ?: best
                            }
                            best
                        } ?: ""

                    if (title.isNotBlank()) {
                        results.add(
                            YouTubeSearchResult(
                                videoId = videoId,
                                title = title,
                                channel = channel,
                                durationText = duration,
                                thumbnailUrl = thumbnail,
                            )
                        )
                    }
                }
                if (results.size >= limit) break
            }
        } catch (_: Exception) {}
        return results
    }
}
