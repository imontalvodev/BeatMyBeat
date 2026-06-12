package com.imontalvodev.beatmybeat.ui.network

import com.imontalvodev.beatmybeat.BuildConfig
import com.imontalvodev.beatmybeat.core.Logger
import okhttp3.Request
import org.json.JSONObject

data class GitHubReleaseInfo(
    val version: String,
    val title: String,
    val releaseNotesExcerpt: String,
    val releasePageUrl: String,
)

object ReleaseUpdateClient {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest"
    private const val LOG_TAG = "ReleaseUpdateClient"
    private const val NOTES_EXCERPT_MAX = 320

    private val client = AppHttpClient.withTimeouts(
        connectSeconds = 8,
        readSeconds = 12,
        callSeconds = 15,
    )

    fun fetchLatestRelease(): GitHubReleaseInfo? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BeatMyBeat/${BuildConfig.VERSION_NAME} (Android)")
            .get()
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.w(LOG_TAG, "GitHub releases HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return null
                parseRelease(JSONObject(body))
            }
        }.onFailure { error ->
            Logger.w(LOG_TAG, "Failed to fetch latest release: ${error.message}")
        }.getOrNull()
    }

    private fun parseRelease(json: JSONObject): GitHubReleaseInfo? {
        if (json.optBoolean("draft", false)) return null
        val tagName = json.optString("tag_name").trim()
        if (tagName.isBlank()) return null

        val version = tagName.removePrefix("v").removePrefix("V").trim()
        if (version.isBlank()) return null

        val pageUrl = json.optString("html_url").trim()
        if (pageUrl.isBlank()) return null

        val title = json.optString("name").trim().ifBlank { tagName }
        val notes = excerptReleaseNotes(json.optString("body"))

        return GitHubReleaseInfo(
            version = version,
            title = title,
            releaseNotesExcerpt = notes,
            releasePageUrl = pageUrl,
        )
    }

    private fun excerptReleaseNotes(raw: String): String {
        val compact = raw
            .replace("\r\n", "\n")
            .lineSequence()
            .map { line ->
                line.trim()
                    .removePrefix("##")
                    .removePrefix("#")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        if (compact.isEmpty()) return ""
        return if (compact.length <= NOTES_EXCERPT_MAX) {
            compact
        } else {
            compact.take(NOTES_EXCERPT_MAX).trimEnd() + "…"
        }
    }
}
