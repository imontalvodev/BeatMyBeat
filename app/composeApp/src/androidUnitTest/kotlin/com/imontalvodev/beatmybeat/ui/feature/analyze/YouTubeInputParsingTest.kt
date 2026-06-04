package com.imontalvodev.beatmybeat.ui.feature.analyze

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubeInputParsingTest {

    private val videoId = "dQw4w9WgXcQ" // 11 chars

    @Test
    fun nonUrlInputIsInvalid() {
        assertTrue(parseYouTubeInput("not a url") is ParsedYouTubeInput.Invalid)
    }

    @Test
    fun disallowedHostIsInvalid() {
        val result = parseYouTubeInput("https://example.com/watch?v=$videoId")
        assertTrue(result is ParsedYouTubeInput.Invalid)
    }

    @Test
    fun watchUrlResolvesToSingleSong() {
        val result = parseYouTubeInput("https://www.youtube.com/watch?v=$videoId")
        assertEquals(ParsedYouTubeInput.SingleSong(videoId), result)
    }

    @Test
    fun youtuBeShortUrlResolvesToSingleSong() {
        val result = parseYouTubeInput("https://youtu.be/$videoId")
        assertEquals(ParsedYouTubeInput.SingleSong(videoId), result)
    }

    @Test
    fun shortsUrlResolvesToSingleSong() {
        val result = parseYouTubeInput("https://www.youtube.com/shorts/$videoId")
        assertEquals(ParsedYouTubeInput.SingleSong(videoId), result)
    }

    @Test
    fun musicHostIsAllowed() {
        val result = parseYouTubeInput("https://music.youtube.com/watch?v=$videoId")
        assertEquals(ParsedYouTubeInput.SingleSong(videoId), result)
    }

    @Test
    fun playlistUrlResolvesToPlaylist() {
        val result = parseYouTubeInput("https://www.youtube.com/playlist?list=PL1234567890")
        assertTrue(result is ParsedYouTubeInput.PlaylistOrAlbum)
        assertEquals("PL1234567890", result.listId)
    }

    @Test
    fun listParameterTakesPriorityOverVideo() {
        val result = parseYouTubeInput("https://www.youtube.com/watch?v=$videoId&list=PL1234567890")
        assertTrue(result is ParsedYouTubeInput.PlaylistOrAlbum)
        assertEquals("PL1234567890", result.listId)
        // El video id se preserva en la URL canónica para resolver la pista concreta.
        assertTrue(result.url.contains(videoId))
    }

    @Test
    fun youtubeHomePageIsInvalid() {
        val result = parseYouTubeInput("https://www.youtube.com/")
        assertTrue(result is ParsedYouTubeInput.Invalid)
    }

    @Test
    fun extractVideoIdRejectsWrongLength() {
        val url = "https://www.youtube.com/watch?v=tooShort".toHttpUrl()
        assertNull(extractYouTubeVideoId(url))
    }

    @Test
    fun extractVideoIdFromLiveSegment() {
        val url = "https://www.youtube.com/live/$videoId".toHttpUrl()
        assertEquals(videoId, extractYouTubeVideoId(url))
    }
}
