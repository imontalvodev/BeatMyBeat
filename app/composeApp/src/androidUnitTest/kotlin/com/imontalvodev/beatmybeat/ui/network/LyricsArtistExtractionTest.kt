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
    fun mojibake_bullet_metadata_formats_artist_for_display() {
        val raw = "Parkineos Y Amygdala Â€¢ Teknocity Â€¢ 4:21"
        assertEquals("Parkineos Y Amygdala", formatArtistForDisplay(raw))
    }

    @Test
    fun normalize_display_metadata_fixes_common_bullet_mojibake() {
        assertEquals("Artist • Song • 4:21", normalizeDisplayMetadata("Artist Â€¢ Song Â€¢ 4:21"))
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
