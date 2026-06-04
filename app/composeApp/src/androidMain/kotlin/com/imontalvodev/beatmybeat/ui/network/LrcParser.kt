package com.imontalvodev.beatmybeat.ui.network

/**
 * Parser mínimo de LRC para uso futuro (scroll/karaoke sincronizado con ExoPlayer).
 */
data class LrcLine(
    val startMs: Long,
    val text: String,
)

object LrcParser {

    private val TIMESTAMP = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")

    fun parse(lrc: String): List<LrcLine> {
        if (lrc.isBlank()) return emptyList()
        val lines = mutableListOf<LrcLine>()
        for (rawLine in lrc.lines()) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val timestamps = TIMESTAMP.findAll(line).toList()
            if (timestamps.isEmpty()) continue
            val text = TIMESTAMP.replace(line, "").trim()
            if (text.isEmpty()) continue
            for (match in timestamps) {
                val startMs = parseTimestamp(match) ?: continue
                lines.add(LrcLine(startMs = startMs, text = text))
            }
        }
        return lines.sortedBy { it.startMs }
    }

    /** Convierte LRC a texto plano (sin marcas de tiempo). */
    fun toPlainText(lrc: String): String {
        val parsed = parse(lrc)
        if (parsed.isNotEmpty()) {
            return parsed.map { it.text }.distinct().joinToString("\n")
        }
        return TIMESTAMP.replace(lrc, "").lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /** Línea activa para una posición de reproducción (ms). */
    fun lineAtPosition(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].startMs <= positionMs) idx = i else break
        }
        return idx
    }

    private fun parseTimestamp(match: MatchResult): Long? {
        val min = match.groupValues[1].toLongOrNull() ?: return null
        val sec = match.groupValues[2].toLongOrNull() ?: return null
        val frac = match.groupValues.getOrNull(3)?.trim().orEmpty()
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> (frac.toLongOrNull() ?: 0L) * 100L
            2 -> (frac.toLongOrNull() ?: 0L) * 10L
            else -> (frac.take(3).toLongOrNull() ?: 0L)
        }
        return min * 60_000L + sec * 1_000L + fracMs
    }
}
