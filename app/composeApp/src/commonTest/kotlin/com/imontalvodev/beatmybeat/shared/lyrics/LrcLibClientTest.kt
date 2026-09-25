package com.imontalvodev.beatmybeat.shared.lyrics

import com.imontalvodev.beatmybeat.shared.net.createAppHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LrcLibClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun record(
        id: Long = 1,
        track: String = "Blinding Lights",
        artist: String = "The Weeknd",
        duration: Int = 200,
        synced: String? = "[00:01.00]I've been tryna call",
        plain: String? = "I've been tryna call",
        instrumental: Boolean = false,
    ): String = buildString {
        append("""{"id":$id,"trackName":"$track","artistName":"$artist","albumName":"After Hours",""")
        append(""""duration":$duration.0,"instrumental":$instrumental,""")
        append(""""plainLyrics":${plain?.let { "\"$it\"" } ?: "null"},""")
        append(""""syncedLyrics":${synced?.let { "\"$it\"" } ?: "null"}}""")
    }

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<LrcLibClient, MutableList<HttpRequestData>> {
        val calls = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            calls += request
            handler(request)
        }
        return LrcLibClient(createAppHttpClient(engine), userAgent = "BeatMyBeat/test") to calls
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, jsonHeaders)

    private fun MockRequestHandleScope.notFound() =
        respond("""{"code":404,"name":"TrackNotFound"}""", HttpStatusCode.NotFound, jsonHeaders)

    @Test
    fun exactCachedMatchWithSyncedLyricsReturnsImmediately() = runTest {
        val (lrc, calls) = client { req ->
            if (req.url.encodedPath.endsWith("/get-cached")) json(record()) else notFound()
        }

        val result = lrc.fetchLyrics("Blinding Lights", "The Weeknd", "After Hours", 200)

        assertTrue(result.success)
        assertEquals("[00:01.00]I've been tryna call", result.syncedLrc)
        assertEquals(1, calls.size)
        assertEquals("BeatMyBeat/test", calls.single().headers[HttpHeaders.UserAgent])
        assertEquals("200", calls.single().url.parameters["duration"])
    }

    @Test
    fun plainOnlyExactMatchKeepsSearchingForSyncedVersion() = runTest {
        val (lrc, _) = client { req ->
            when {
                req.url.encodedPath.endsWith("/get-cached") -> json(record(id = 1, synced = null))
                req.url.encodedPath.endsWith("/search") -> json("[${record(id = 2)}]")
                else -> notFound()
            }
        }

        val result = lrc.fetchLyrics("Blinding Lights", "The Weeknd", "", 200)

        assertTrue(result.success)
        assertEquals(2L, result.lrclibId)
        assertEquals("[00:01.00]I've been tryna call", result.syncedLrc)
    }

    @Test
    fun plainLyricsAreUsedWhenNoSyncedVersionExists() = runTest {
        val (lrc, _) = client { req ->
            when {
                req.url.encodedPath.endsWith("/search") -> json("[${record(synced = null)}]")
                else -> notFound()
            }
        }

        val result = lrc.fetchLyrics("Blinding Lights", "The Weeknd", "", 200)

        assertTrue(result.success)
        assertNull(result.syncedLrc)
        assertEquals("I've been tryna call", result.lyrics)
        assertNull(result.error)
    }

    @Test
    fun instrumentalTracksAreReportedAsSuch() = runTest {
        val (lrc, _) = client { req ->
            if (req.url.encodedPath.endsWith("/get-cached")) {
                json(record(synced = null, plain = null, instrumental = true))
            } else {
                notFound()
            }
        }

        val result = lrc.fetchLyrics("Blinding Lights", "The Weeknd", "", 200)

        assertFalse(result.success)
        assertTrue(result.isInstrumental)
    }

    @Test
    fun searchResultFromAnotherArtistIsRejected() = runTest {
        val (lrc, _) = client { req ->
            if (req.url.encodedPath.endsWith("/search")) {
                json("[${record(artist = "Some Cover Band")}]")
            } else {
                notFound()
            }
        }

        val result = lrc.fetchLyrics("Blinding Lights", "The Weeknd", "", 200)

        assertFalse(result.success)
        assertEquals(ERROR_NOT_FOUND, result.error)
    }

    @Test
    fun serverErrorsAreTransientNotMissingLyrics() = runTest {
        val (lrc, calls) = client { json("oops", HttpStatusCode.ServiceUnavailable) }

        val result = lrc.fetchLyrics("Blinding Lights", "The Weeknd", "", 200)

        assertEquals(ERROR_UNREACHABLE, result.error)
        assertTrue(isLyricsNetworkFailure(result.error))
        assertEquals(1, calls.size, "debe abortar en el primer fallo de red")
    }

    @Test
    fun networkFailureAfterPlainFallbackReturnsItFlaggedAsPartial() = runTest {
        val (lrc, _) = client { req ->
            when {
                req.url.encodedPath.endsWith("/get-cached") -> json(record(synced = null))
                else -> throw IOException("connection reset")
            }
        }

        val result = lrc.fetchLyrics("Blinding Lights", "The Weeknd", "", 200)

        assertTrue(result.success)
        assertEquals("I've been tryna call", result.lyrics)
        assertEquals(ERROR_UNREACHABLE, result.error)
    }

    @Test
    fun withoutDurationOnlySearchIsUsed() = runTest {
        val (lrc, calls) = client { req ->
            if (req.url.encodedPath.endsWith("/search")) json("[${record()}]") else notFound()
        }

        val result = lrc.fetchLyrics("Blinding Lights", "The Weeknd", "", 0)

        assertTrue(result.success)
        assertTrue(calls.all { it.url.encodedPath.endsWith("/search") })
    }

    @Test
    fun missingArtistFailsWithoutNetwork() = runTest {
        val (lrc, calls) = client { notFound() }

        val result = lrc.fetchLyrics("Blinding Lights", "  ", "", 200)

        assertEquals(ERROR_MISSING_FIELDS, result.error)
        assertTrue(calls.isEmpty())
    }
}
