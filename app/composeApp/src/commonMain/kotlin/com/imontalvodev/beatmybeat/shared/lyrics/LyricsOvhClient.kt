package com.imontalvodev.beatmybeat.shared.lyrics

import com.imontalvodev.beatmybeat.shared.net.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class LyricsOvhBody(
    val lyrics: String? = null,
    val error: String? = null,
)

/** Reserva de texto plano ([lyrics.ovh](https://lyricsovh.docs.apiary.io)) cuando LRCLIB no tiene la canción. */
class LyricsOvhClient(
    private val http: HttpClient,
    private val userAgent: String,
    private val baseUrl: String = "https://api.lyrics.ovh/v1",
) {
    suspend fun fetch(title: String, artist: String): LyricsResponse {
        val safeArtist = extractPrimaryArtistForLyrics(artist).ifBlank {
            return LyricsResponse.failure("MissingArtist")
        }
        val safeTitle = title.trim().ifBlank { return LyricsResponse.failure("MissingTitle") }
        val url = URLBuilder(baseUrl).appendPathSegments(safeArtist, safeTitle).buildString()
        return try {
            val response = http.get(url) {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, userAgent)
                timeout {
                    requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds
                    connectTimeoutMillis = CONNECT_TIMEOUT.inWholeMilliseconds
                }
            }
            val text = response.bodyAsText()
            val body = runCatching { AppJson.decodeFromString(LyricsOvhBody.serializer(), text) }.getOrNull()
            when {
                body == null -> LyricsResponse.failure(
                    if (response.status.isSuccess()) "InvalidJson" else "Http${response.status.value}",
                )
                !body.lyrics.isNullOrBlank() && response.status.isSuccess() -> LyricsResponse(
                    success = true,
                    lyrics = body.lyrics.trim(),
                    syncedLrc = null,
                    source = SOURCE,
                    sourceUrl = url,
                    error = null,
                    message = null,
                )
                else -> LyricsResponse.failure("NoLyrics", body.error)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LyricsResponse.failure("NetworkError", e.message)
        }
    }

    companion object {
        const val SOURCE = "lyrics.ovh"
        private val REQUEST_TIMEOUT = 30.seconds
        private val CONNECT_TIMEOUT = 10.seconds
    }
}
