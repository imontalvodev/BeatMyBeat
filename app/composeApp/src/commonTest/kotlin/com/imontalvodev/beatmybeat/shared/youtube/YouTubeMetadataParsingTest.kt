package com.imontalvodev.beatmybeat.shared.youtube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YouTubeMetadataParsingTest {

    @Test
    fun musicSubtitleSplitsArtistAlbumAndDuration() {
        assertEquals(
            TrackIdentity("Monkeys Spinning Monkeys", "Kevin MacLeod", "Monkeys Spinning Monkeys"),
            parseYouTubeMusicSubtitle("Monkeys Spinning Monkeys", "Kevin MacLeod • Monkeys Spinning Monkeys • 2:06"),
        )
    }

    @Test
    fun musicSubtitleWithoutAlbum() {
        assertEquals(
            TrackIdentity("Song", "Artist", ""),
            parseYouTubeMusicSubtitle("Song", "Artist • 3:26"),
        )
    }

    @Test
    fun musicSubtitleKeepsCollaborations() {
        assertEquals(
            "Kevin MacLeod y Kevin The Monkey",
            parseYouTubeMusicSubtitle("X", "Kevin MacLeod y Kevin The Monkey • Album • 2:05").artist,
        )
    }

    @Test
    fun videoTitleWithSeparatorAndNoise() {
        assertEquals(
            TrackIdentity("Blinding Lights", "The Weeknd"),
            parseYouTubeVideoTitle("The Weeknd - Blinding Lights (Official Video)", "TheWeekndVEVO"),
        )
    }

    @Test
    fun videoTitleWithoutSeparatorUsesCleanedChannel() {
        assertEquals(
            TrackIdentity("Yellow", "Coldplay"),
            parseYouTubeVideoTitle("Yellow [Official Audio]", "Coldplay - Topic"),
        )
    }

    @Test
    fun durationTextParsing() {
        assertEquals(126, parseDurationText("2:06"))
        assertEquals(3723, parseDurationText("1:02:03"))
        assertNull(parseDurationText("live"))
    }
}
