package com.imontalvodev.beatmybeat.ui.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LrcParserTest {

    @Test
    fun parse_plainLrc_hasNoWordTimestamps() {
        val lrc = "[00:12.00]Hello world\n[00:14.50]Second line"
        val lines = LrcParser.parse(lrc)

        assertEquals(2, lines.size)
        assertEquals("Hello world", lines[0].text)
        assertTrue(lines[0].words.isEmpty())
        assertEquals(12_000L, lines[0].startMs)
        assertEquals(14_500L, lines[1].startMs)
    }

    @Test
    fun parse_enhancedLrc_extractsWordTimestamps() {
        val lrc = "[00:12.00]<00:12.00>Hello <00:12.50>world"
        val lines = LrcParser.parse(lrc)

        assertEquals(1, lines.size)
        val line = lines[0]
        assertEquals("Hello world", line.text)
        assertEquals(2, line.words.size)
        assertEquals(12_000L, line.words[0].startMs)
        assertEquals("Hello ", line.words[0].text)
        assertEquals(12_500L, line.words[1].startMs)
        assertEquals("world", line.words[1].text)
    }

    @Test
    fun parse_enhancedLrc_leadingTextWithoutWordTimestamp_getsLineStartMs() {
        val lrc = "[00:12.00]Hey <00:12.80>world"
        val lines = LrcParser.parse(lrc)

        val line = lines[0]
        assertEquals(2, line.words.size)
        assertEquals(12_000L, line.words[0].startMs)
        assertEquals("Hey ", line.words[0].text)
        assertEquals(12_800L, line.words[1].startMs)
    }

    @Test
    fun toPlainText_stripsWordTimestampsToo() {
        val lrc = "[00:12.00]<00:12.00>Hello <00:12.50>world"
        assertEquals("Hello world", LrcParser.toPlainText(lrc))
    }

    @Test
    fun karaokeHighlightLength_withoutWords_isAlwaysZero() {
        val line = LrcLine(startMs = 10_000L, text = "Hello world")
        assertEquals(0, LrcParser.karaokeHighlightLength(line, positionMs = 10_500L))
        assertEquals(0, LrcParser.karaokeHighlightLength(line, positionMs = 99_000L))
    }

    @Test
    fun karaokeHighlightLength_beforeLineStart_isZero() {
        val line = LrcLine(
            startMs = 10_000L,
            text = "Hello world",
            words = listOf(
                LrcWord(startMs = 10_000L, text = "Hello "),
                LrcWord(startMs = 10_500L, text = "world"),
            ),
        )
        assertEquals(0, LrcParser.karaokeHighlightLength(line, positionMs = 9_000L))
    }

    @Test
    fun karaokeHighlightLength_advancesPerRealWordTimestamp() {
        val line = LrcLine(
            startMs = 10_000L,
            text = "Hello world",
            words = listOf(
                LrcWord(startMs = 10_000L, text = "Hello "),
                LrcWord(startMs = 10_500L, text = "world"),
            ),
        )
        // Justo tras el inicio de la línea: solo "Hello " (6 chars).
        assertEquals(6, LrcParser.karaokeHighlightLength(line, positionMs = 10_200L))
        // En el inicio real de la segunda palabra: línea completa.
        assertEquals(11, LrcParser.karaokeHighlightLength(line, positionMs = 10_500L))
        // Mucho después: sigue siendo la línea completa (no hay palabra futura que reste).
        assertEquals(11, LrcParser.karaokeHighlightLength(line, positionMs = 60_000L))
    }

    @Test
    fun focusLineAtPosition_duranteLaIntro_enfocaLaPrimeraLinea() {
        val lines = LrcParser.parse("[00:12.00]Primera\n[00:14.50]Segunda")

        // Antes del primer timestamp no canta nadie...
        assertEquals(-1, LrcParser.lineAtPosition(lines, 0L))
        // ...pero la vista debe enfocar ya la primera linea.
        assertEquals(0, LrcParser.focusLineAtPosition(lines, 0L))
        assertEquals(0, LrcParser.focusLineAtPosition(lines, 11_999L))
    }

    @Test
    fun focusLineAtPosition_conLineaSonando_coincideConLaActiva() {
        val lines = LrcParser.parse("[00:12.00]Primera\n[00:14.50]Segunda")

        assertEquals(0, LrcParser.focusLineAtPosition(lines, 12_000L))
        assertEquals(1, LrcParser.focusLineAtPosition(lines, 14_500L))
        assertEquals(1, LrcParser.focusLineAtPosition(lines, 99_000L))
    }

    @Test
    fun focusLineAtPosition_sinLineas_devuelveMenosUno() {
        assertEquals(-1, LrcParser.focusLineAtPosition(emptyList(), 0L))
    }

}
