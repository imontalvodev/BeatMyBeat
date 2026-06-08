package com.imontalvodev.beatmybeat.ui.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricsArtistExtractionTest {

    @Test
    fun extracts_artist_from_youtube_bullet_metadata() {
        val raw = "Alex Lumbier • Caliente (Versión Techno) • 3:26"
        assertEquals("Alex Lumbier", extractPrimaryArtistForLyrics(raw))
    }

    @Test
    fun build_candidates_puts_primary_artist_first() {
        val candidates = buildLyricsArtistCandidates(
            "Melendi - Topic",
            emptyList(),
        )
        assertTrue(candidates.first().equals("Melendi", ignoreCase = true))
    }

    @Test
    fun polluted_metadata_accepts_lrclib_match() {
        val ok = isAcceptableLrcCandidate(
            expectedTitle = "Caliente (Version Techno)",
            expectedArtist = "Alex Lumbier • Caliente (Versión Techno) • 3:26",
            expectedDurationSec = 206,
            candidate = LrcLyricsCandidate(
                trackName = "Caliente (Versión Techno)",
                artistName = "Alex Lumbier",
                durationSeconds = 205,
            ),
        )
        assertTrue(ok)
    }
}
