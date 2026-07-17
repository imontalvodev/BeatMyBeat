package com.imontalvodev.beatmybeat.ui.network

/**
 * Parser mínimo de LRC para uso futuro (scroll/karaoke sincronizado con ExoPlayer).
 */
data class LrcLine(
    val startMs: Long,
    val text: String,
    /**
     * Timestamps por palabra (LRC "enhanced", `<mm:ss.xx>palabra`), vacío si la fuente no los
     * provee. Sin esto NO se interpola nada dentro de la línea: adivinar el ritmo por proporción
     * de caracteres no sigue el audio real y queda desincronizado (ver historial de Fase D).
     */
    val words: List<LrcWord> = emptyList(),
)

/** Fragmento de texto (palabra + espacios adyacentes) con su instante de inicio real. */
data class LrcWord(
    val startMs: Long,
    val text: String,
)

object LrcParser {

    private val TIMESTAMP = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")
    private val WORD_TIMESTAMP = Regex("""<(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?>""")

    fun parse(lrc: String): List<LrcLine> {
        if (lrc.isBlank()) return emptyList()
        val lines = mutableListOf<LrcLine>()
        for (rawLine in lrc.lines()) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val timestamps = TIMESTAMP.findAll(line).toList()
            if (timestamps.isEmpty()) continue
            val remainder = TIMESTAMP.replace(line, "").trim()
            if (remainder.isEmpty()) continue
            val hasWordTimestamps = WORD_TIMESTAMP.containsMatchIn(remainder)
            for (match in timestamps) {
                val startMs = parseTimestamp(match) ?: continue
                val words = if (hasWordTimestamps) parseWordTimestamps(remainder, startMs) else emptyList()
                val text = if (words.isNotEmpty()) words.joinToString("") { it.text }.trim() else remainder
                if (text.isEmpty()) continue
                lines.add(LrcLine(startMs = startMs, text = text, words = words))
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
        return WORD_TIMESTAMP.replace(TIMESTAMP.replace(lrc, ""), "").lines()
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

    /**
     * Número de caracteres de [LrcLine.text] ya "cantados" en [positionMs], solo cuando la línea
     * trae timestamps reales por palabra ([LrcLine.words]). Si no los trae, devuelve 0 siempre:
     * no hay interpolación de respaldo, para no mostrar un resaltado que no sigue el audio real.
     */
    fun karaokeHighlightLength(line: LrcLine, positionMs: Long): Int {
        if (line.words.isEmpty() || positionMs <= line.startMs) return 0
        var consumed = 0
        for (word in line.words) {
            if (positionMs < word.startMs) break
            consumed += word.text.length
        }
        return consumed.coerceIn(0, line.text.length)
    }

    private fun parseWordTimestamps(remainder: String, lineStartMs: Long): List<LrcWord> {
        val matches = WORD_TIMESTAMP.findAll(remainder).toList()
        if (matches.isEmpty()) return emptyList()
        val words = mutableListOf<LrcWord>()
        val leading = remainder.substring(0, matches.first().range.first)
        if (leading.isNotEmpty()) {
            words.add(LrcWord(startMs = lineStartMs, text = leading))
        }
        for (i in matches.indices) {
            val match = matches[i]
            val startMs = parseTimestamp(match) ?: continue
            val segStart = match.range.last + 1
            val segEnd = if (i + 1 < matches.size) matches[i + 1].range.first else remainder.length
            if (segStart >= segEnd) continue
            words.add(LrcWord(startMs = startMs, text = remainder.substring(segStart, segEnd)))
        }
        return words
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
