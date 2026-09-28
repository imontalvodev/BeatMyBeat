package com.imontalvodev.beatmybeat.shared.lyrics

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [Lyricsfile](https://lrclib.net/lyricsfile): formato YAML de LRCLIB con letra por líneas y,
 * cuando `hasWordSync` es true, tiempos por palabra:
 *
 * ```yaml
 * lines:
 * - text: Song lyric line
 *   start_ms: 1500
 *   end_ms: 3200
 *   words:
 *   - text: 'Song '
 *     start_ms: 1500
 *     end_ms: 2000
 * ```
 *
 * Aquí solo se traduce a LRC "enhanced" (`[mm:ss.xxx]<mm:ss.xxx>palabra …`), que es lo que ya
 * guardan la caché y el sidecar y lo que entiende [LrcParser]. Así no cambia el almacenamiento y
 * las letras antiguas siguen valiendo.
 */
@Serializable
internal data class LyricsfileDocument(
    val lines: List<LyricsfileLine> = emptyList(),
)

@Serializable
internal data class LyricsfileLine(
    val text: String = "",
    @SerialName("start_ms") val startMs: Long? = null,
    val words: List<LyricsfileWord> = emptyList(),
)

@Serializable
internal data class LyricsfileWord(
    val text: String = "",
    @SerialName("start_ms") val startMs: Long? = null,
)

// El formato es un borrador (1.0): campos nuevos no deben romper la lectura.
private val LyricsfileYaml = Yaml(configuration = YamlConfiguration(strictMode = false))

/**
 * LRC enhanced a partir de un Lyricsfile, o `null` si no se puede leer o ninguna línea trae
 * tiempos por palabra (entonces es mejor quedarse con `syncedLyrics`).
 */
fun lyricsfileToEnhancedLrc(yaml: String): String? {
    if (yaml.isBlank()) return null
    val document = runCatching {
        LyricsfileYaml.decodeFromString(LyricsfileDocument.serializer(), yaml)
    }.getOrNull() ?: return null
    if (document.lines.none { line -> line.words.any { it.startMs != null } }) return null

    return buildString {
        for (line in document.lines) {
            val start = line.startMs ?: continue
            val text = line.text.trim()
            if (text.isEmpty()) continue
            append('[').append(formatLrcTimestamp(start)).append(']')
            val segments = wordSegments(text, line.words.filter { it.startMs != null && it.text.isNotBlank() })
            if (segments.isEmpty()) {
                append(text)
            } else {
                for ((ms, segment) in segments) {
                    append('<').append(formatLrcTimestamp(ms)).append('>').append(segment)
                }
            }
            append('\n')
        }
    }.trimEnd().ifEmpty { null }
}

/**
 * Reparte el texto de la línea entre sus palabras. Cada tramo va desde el inicio de su palabra
 * hasta el de la siguiente, así los espacios y signos quedan dentro y la concatenación es
 * exactamente la línea (lo que necesita el resaltado de [LrcParser.karaokeHighlightLength]).
 * Si alguna palabra no aparece en la línea, se usan los textos de las palabras tal cual.
 */
private fun wordSegments(lineText: String, words: List<LyricsfileWord>): List<Pair<Long, String>> {
    if (words.isEmpty()) return emptyList()
    val starts = ArrayList<Int>(words.size)
    var cursor = 0
    for (word in words) {
        val idx = lineText.indexOf(word.text.trim(), cursor)
        if (idx < 0) return rawSegments(words)
        starts.add(idx)
        cursor = idx + word.text.trim().length
    }
    starts[0] = 0
    return words.mapIndexed { i, word ->
        val end = if (i + 1 < starts.size) starts[i + 1] else lineText.length
        word.startMs!! to lineText.substring(starts[i], end)
    }
}

private fun rawSegments(words: List<LyricsfileWord>): List<Pair<Long, String>> {
    val needsSpaces = words.size > 1 && words.none { w -> w.text.first().isWhitespace() || w.text.last().isWhitespace() }
    return words.mapIndexed { i, word ->
        val text = if (needsSpaces && i < words.lastIndex) "${word.text} " else word.text
        word.startMs!! to text
    }
}

/** `mm:ss.xxx`, con los minutos que hagan falta (el parser admite 1-2 dígitos). */
internal fun formatLrcTimestamp(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    val minutes = safe / 60_000
    val seconds = (safe / 1_000) % 60
    val millis = safe % 1_000
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.${millis.toString().padStart(3, '0')}"
}
