package com.imontalvodev.beatmybeat.ui.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LrcLibMatchingTest {

    @Test
    fun accepts_same_title_and_artist_with_one_second_duration_delta() {
        val ok = isAcceptableLrcCandidate(
            expectedTitle = "Por Amarte Tanto",
            expectedArtist = "Melendi",
            expectedDurationSec = 198,
            candidate = LrcLyricsCandidate(
                trackName = "Por Amarte Tanto",
                artistName = "Melendi",
                durationSeconds = 197,
            ),
        )
        assertTrue(ok)
    }

    @Test
    fun rejects_same_title_different_artist() {
        val ok = isAcceptableLrcCandidate(
            expectedTitle = "Yesterday",
            expectedArtist = "The Beatles",
            expectedDurationSec = 125,
            candidate = LrcLyricsCandidate(
                trackName = "Yesterday",
                artistName = "Boyce Avenue",
                durationSeconds = 126,
            ),
        )
        assertFalse(ok)
    }

    @Test
    fun rejects_large_duration_mismatch_even_with_matching_names() {
        val ok = isAcceptableLrcCandidate(
            expectedTitle = "Hello",
            expectedArtist = "Adele",
            expectedDurationSec = 295,
            candidate = LrcLyricsCandidate(
                trackName = "Hello",
                artistName = "Adele",
                durationSeconds = 240,
            ),
        )
        assertFalse(ok)
    }

    @Test
    fun accepts_when_duration_unknown_on_either_side() {
        val ok = isAcceptableLrcCandidate(
            expectedTitle = "Shape of You",
            expectedArtist = "Ed Sheeran",
            expectedDurationSec = 0,
            candidate = LrcLyricsCandidate(
                trackName = "Shape of You",
                artistName = "Ed Sheeran",
                durationSeconds = 233,
            ),
        )
        assertTrue(ok)
    }

    @Test
    fun scores_homonym_with_correct_artist_above_wrong_artist() {
        val expectedTitle = "Let It Be"
        val expectedArtist = "The Beatles"
        val duration = 243

        val correct = scoreLrcCandidate(
            expectedTitle,
            expectedArtist,
            duration,
            LrcLyricsCandidate("Let It Be", "The Beatles", duration, hasSyncedLyrics = true),
        )
        val wrong = scoreLrcCandidate(
            expectedTitle,
            expectedArtist,
            duration,
            LrcLyricsCandidate("Let It Be", "Paul McCartney", duration, hasSyncedLyrics = true),
        )

        assertTrue(correct > wrong)
        assertTrue(wrong == Int.MIN_VALUE)
    }
}
