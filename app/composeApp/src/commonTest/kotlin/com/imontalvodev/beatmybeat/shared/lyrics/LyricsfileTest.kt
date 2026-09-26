package com.imontalvodev.beatmybeat.shared.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsfileTest {

    private val wordSynced = """
        version: '1.0'
        metadata:
          title: Blinding Lights
          artist: The Weeknd
          vocalists: [main]
        lines:
        - text: I've been tryna call
          start_ms: 26950
          end_ms: 28560
          words:
          - text: "I've "
            start_ms: 26950
            end_ms: 27200
          - text: 'been '
            start_ms: 27200
            end_ms: 27500
          - text: 'tryna '
            start_ms: 27500
            end_ms: 27900
          - text: call
            start_ms: 27900
            end_ms: 28560
        - text: ''
          start_ms: 28560
          end_ms: 29830
        - text: Yeah
          start_ms: 61000
          end_ms: 62000
    """.trimIndent()

    @Test
    fun wordSyncedFileBecomesEnhancedLrc() {
        assertEquals(
            "[00:26.950]<00:26.950>I've <00:27.200>been <00:27.500>tryna <00:27.900>call\n[01:01.000]Yeah",
            lyricsfileToEnhancedLrc(wordSynced),
        )
    }

    @Test
    fun enhancedLrcRoundTripsThroughParserWithWordTimes() {
        val lines = LrcParser.parse(lyricsfileToEnhancedLrc(wordSynced)!!)
        assertEquals(2, lines.size)
        assertEquals("I've been tryna call", lines[0].text)
        assertEquals(listOf(26950L, 27200L, 27500L, 27900L), lines[0].words.map { it.startMs })
        assertEquals("I've been ".length, LrcParser.karaokeHighlightLength(lines[0], 27300))
        assertEquals(emptyList(), lines[1].words)
    }

    @Test
    fun wordsWithoutSpacesAreAlignedToTheLineText() {
        val yaml = """
            lines:
            - text: Oh, baby!
              start_ms: 1000
              words:
              - text: Oh
                start_ms: 1000
              - text: baby
                start_ms: 1500
        """.trimIndent()
        val line = LrcParser.parse(lyricsfileToEnhancedLrc(yaml)!!).single()
        assertEquals("Oh, baby!", line.text)
        assertEquals(listOf("Oh, ", "baby!"), line.words.map { it.text })
    }

    @Test
    fun lineSyncedOnlyOrBrokenFilesReturnNull() {
        assertNull(lyricsfileToEnhancedLrc("lines:\n- text: Hi\n  start_ms: 10\n  end_ms: 20"))
        assertNull(lyricsfileToEnhancedLrc("lines: [unclosed"))
        assertNull(lyricsfileToEnhancedLrc(""))
    }

    @Test
    fun timestampsKeepMilliseconds() {
        assertEquals("00:00.005", formatLrcTimestamp(5))
        assertEquals("61:01.250", formatLrcTimestamp(3_661_250))
    }
}
