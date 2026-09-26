package com.imontalvodev.beatmybeat.ui.network

import com.imontalvodev.beatmybeat.shared.youtube.extractYouTubeVideoId
import com.imontalvodev.beatmybeat.shared.youtube.parseYouTubeMusicSubtitle
import com.imontalvodev.beatmybeat.shared.youtube.parseYouTubeVideoTitle
import okhttp3.MediaType.Companion.toMediaType
import org.schabi.newpipe.extractor.ServiceList
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class YouTubeSearchSource {
    YOUTUBE_MUSIC,
    YOUTUBE,
}

data class YouTubeSearchResult(
    val videoId: String,
    val title: String,
    val channel: String,
    val durationText: String,
    val thumbnailUrl: String,
    val source: YouTubeSearchSource,
    val album: String = "",
)

object YouTubeSearchClient {

    private val client = AppHttpClient.withTimeouts(
        connectSeconds = 15,
        readSeconds = 20,
        callSeconds = 30,
    )

    private const val INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val YOUTUBE_SEARCH_URL =
        "https://www.youtube.com/youtubei/v1/search?key=$INNERTUBE_KEY&prettyPrint=false"
    private const val YOUTUBE_MUSIC_SEARCH_URL =
        "https://music.youtube.com/youtubei/v1/search?key=$INNERTUBE_KEY&prettyPrint=false"

    private const val YOUTUBE_CLIENT_VERSION = "2.20240101.00.00"
    private const val YOUTUBE_MUSIC_CLIENT_VERSION = "1.20260121.03.00"
    /** Filtro InnerTube: solo canciones (WEB_REMIX). */
    private const val YTMUSIC_SONGS_FILTER = "EgWKAQIIAWoMEA4QChADEAQQCRAF"

    /**
     * Busca primero en YouTube Music y después en YouTube.
     * Los resultados de YT Music aparecen al inicio de la lista.
     */
    fun search(query: String, limit: Int = 10): List<YouTubeSearchResult> {
        val safeQuery = query.trim()
        if (safeQuery.isBlank()) return emptyList()

        val musicResults = runCatching {
            searchYouTubeMusic(safeQuery, limit)
        }.getOrElse { emptyList() }

        val seenIds = musicResults.map { it.videoId }.toMutableSet()
        val webResults = runCatching {
            searchYouTubeWeb(safeQuery, limit)
        }.getOrElse { emptyList() }
            .filter { seenIds.add(it.videoId) }

        return (musicResults + webResults).take(limit)
    }

    private fun searchYouTubeWeb(query: String, limit: Int): List<YouTubeSearchResult> {
        val body = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", YOUTUBE_CLIENT_VERSION)
                    put("hl", "es")
                    put("gl", "ES")
                })
            })
            put("query", "$query official audio")
        }.toString()

        val responseBody = postJson(
            url = YOUTUBE_SEARCH_URL,
            body = body,
            clientNameHeader = "1",
            clientVersionHeader = YOUTUBE_CLIENT_VERSION,
            origin = "https://www.youtube.com",
            referer = "https://www.youtube.com/",
        ) ?: return emptyList()

        return parseYouTubeWebResults(responseBody, limit)
    }

    private fun searchYouTubeMusic(query: String, limit: Int): List<YouTubeSearchResult> {
        val body = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", YOUTUBE_MUSIC_CLIENT_VERSION)
                    put("hl", "es")
                    put("gl", "ES")
                })
            })
            put("query", query)
            put("params", YTMUSIC_SONGS_FILTER)
        }.toString()

        val responseBody = postJson(
            url = YOUTUBE_MUSIC_SEARCH_URL,
            body = body,
            clientNameHeader = "67",
            clientVersionHeader = YOUTUBE_MUSIC_CLIENT_VERSION,
            origin = "https://music.youtube.com",
            referer = "https://music.youtube.com/",
        ) ?: return emptyList()

        return parseYouTubeMusicResults(responseBody, limit)
    }

    private fun postJson(
        url: String,
        body: String,
        clientNameHeader: String,
        clientVersionHeader: String,
        origin: String,
        referer: String,
    ): String? {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("X-YouTube-Client-Name", clientNameHeader)
            .header("X-YouTube-Client-Version", clientVersionHeader)
            .header("Content-Type", "application/json")
            .header("Accept-Language", "es-ES,es;q=0.9")
            .header("Origin", origin)
            .header("Referer", referer)
            .build()

        return client.newCall(request).execute().use { res ->
            if (!res.isSuccessful) {
                val errorBody = res.body?.string()?.take(200) ?: ""
                throw Exception("Search HTTP ${res.code}: $errorBody")
            }
            res.body?.string()?.takeIf { it.isNotBlank() }
        }
    }

    data class PlaylistEntry(
        val videoId: String,
        val title: String,
        val artist: String,
        val album: String,
        val thumbnailUrl: String,
    )

    data class PlaylistInfo(
        val title: String,
        val entries: List<PlaylistEntry>,
    )

    /**
     * Playlist o álbum vía NewPipe, con paginación. Antes se sacaban los IDs del HTML con una
     * regex: se colaban vídeos recomendados de la barra lateral y se perdía todo lo que pasara
     * de la primera página (~100 elementos).
     */
    fun fetchPlaylistInfo(listId: String, limit: Int = 200): PlaylistInfo {
        val safeListId = listId.trim()
        if (safeListId.isBlank()) return PlaylistInfo("", emptyList())
        NewPipeStreamExtractor.init()
        val url = "https://www.youtube.com/playlist?list=$safeListId"
        val info = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(ServiceList.YouTube, url)
        val isAlbum = safeListId.startsWith("OLAK5uy")
        val items = info.relatedItems.toMutableList()
        var page = info.nextPage
        while (items.size < limit && page != null) {
            val more = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getMoreItems(ServiceList.YouTube, url, page)
            items += more.items
            page = more.nextPage
        }
        val entries = items.take(limit).mapNotNull { item ->
            val videoId = extractYouTubeVideoId(item.url) ?: return@mapNotNull null
            val identity = parseYouTubeVideoTitle(item.name, item.uploaderName.orEmpty())
            PlaylistEntry(
                videoId = videoId,
                title = identity.title,
                artist = identity.artist,
                album = if (isAlbum) info.name.orEmpty() else "",
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            )
        }
        return PlaylistInfo(info.name.orEmpty(), entries)
    }

    private fun parseYouTubeWebResults(json: String, limit: Int): List<YouTubeSearchResult> {
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

                    val title = extractRunsText(videoRenderer.optJSONObject("title")) ?: continue
                    val channel = extractRunsText(videoRenderer.optJSONObject("ownerText")) ?: ""
                    val duration = videoRenderer
                        .optJSONObject("lengthText")
                        ?.optString("simpleText", "") ?: ""
                    val thumbnail = bestThumbnailUrl(videoRenderer.optJSONObject("thumbnail"))

                    results.add(
                        YouTubeSearchResult(
                            videoId = videoId,
                            title = title,
                            channel = channel,
                            durationText = duration,
                            thumbnailUrl = thumbnail,
                            source = YouTubeSearchSource.YOUTUBE,
                        ),
                    )
                }
                if (results.size >= limit) break
            }
        } catch (_: Exception) {
        }
        return results
    }

    private fun parseYouTubeMusicResults(json: String, limit: Int): List<YouTubeSearchResult> {
        val results = mutableListOf<YouTubeSearchResult>()
        val seen = mutableSetOf<String>()
        try {
            val root = JSONObject(json)
            val renderers = mutableListOf<JSONObject>()
            collectObjectsWithKey(root, "musicResponsiveListItemRenderer", renderers)

            for (renderer in renderers) {
                if (results.size >= limit) break
                parseMusicResponsiveListItem(renderer)?.let { item ->
                    if (seen.add(item.videoId)) {
                        results.add(item)
                    }
                }
            }

        } catch (_: Exception) {
        }
        return results
    }

    private fun parseMusicResponsiveListItem(renderer: JSONObject): YouTubeSearchResult? {
        val videoId = findWatchVideoId(renderer) ?: return null

        val flexTexts = mutableListOf<String>()
        renderer.optJSONArray("flexColumns")?.let { columns ->
            for (i in 0 until columns.length()) {
                val text = columns.optJSONObject(i)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.let { extractRunsText(it.optJSONObject("text")) }
                if (!text.isNullOrBlank()) {
                    flexTexts.add(text.trim())
                }
            }
        }

        val title = flexTexts.firstOrNull() ?: return null
        val identity = parseYouTubeMusicSubtitle(title, flexTexts.getOrNull(1).orEmpty())

        var duration = ""
        renderer.optJSONArray("fixedColumns")?.let { columns ->
            for (i in 0 until columns.length()) {
                val text = columns.optJSONObject(i)
                    ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                    ?.let { extractRunsText(it.optJSONObject("text")) }
                if (!text.isNullOrBlank() && text.contains(':')) {
                    duration = text.trim()
                    break
                }
            }
        }

        val thumbnail = bestThumbnailUrl(renderer.optJSONObject("thumbnail"))
            .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

        return YouTubeSearchResult(
            videoId = videoId,
            title = identity.title,
            channel = identity.artist,
            durationText = duration,
            thumbnailUrl = thumbnail,
            source = YouTubeSearchSource.YOUTUBE_MUSIC,
            album = identity.album,
        )
    }

    private fun collectObjectsWithKey(
        node: Any?,
        key: String,
        out: MutableList<JSONObject>,
    ) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let { out.add(it) }
                node.keys().forEach { childKey ->
                    collectObjectsWithKey(node.opt(childKey), key, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectObjectsWithKey(node.opt(i), key, out)
                }
            }
        }
    }

    private fun findWatchVideoId(node: JSONObject): String? {
        node.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.length == 11 }
            ?.let { return it }

        node.keys().forEach { key ->
            when (val child = node.opt(key)) {
                is JSONObject -> findWatchVideoId(child)?.let { return it }
                is JSONArray -> {
                    for (i in 0 until child.length()) {
                        (child.opt(i) as? JSONObject)?.let { nested ->
                            findWatchVideoId(nested)?.let { return it }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun extractRunsText(textObj: JSONObject?): String? {
        if (textObj == null) return null
        textObj.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = textObj.optJSONArray("runs") ?: return null
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text", "").orEmpty())
        }
        return sb.toString().trim().ifBlank { null }
    }

    private fun bestThumbnailUrl(thumbnailObj: JSONObject?): String {
        val thumbs = thumbnailObj?.optJSONArray("thumbnails") ?: return ""
        var best = ""
        for (i in 0 until thumbs.length()) {
            best = thumbs.optJSONObject(i)?.optString("url", "")?.ifBlank { best } ?: best
        }
        return best
    }

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}
