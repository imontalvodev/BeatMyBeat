package com.imontalvodev.beatmybeat.shared.youtube

import com.imontalvodev.beatmybeat.shared.lyrics.cleanArtistForLyrics
import com.imontalvodev.beatmybeat.shared.text.stripDiacritics

data class TrackIdentity(
    val title: String,
    val artist: String,
    val album: String = "",
)

private val NOISE_SUFFIX = Regex(
    """\s*[(\[](official\s*(audio|video|music\s*video|lyric\s*video|visuali[sz]er)?|lyrics?(\s*video)?|audio|video|hd|hq|4k|explicit|visuali[sz]er|videoclip|video\s*oficial|audio\s*oficial|no\s*copyright(\s*music)?|copyright\s*free)[)\]]\s*""",
    RegexOption.IGNORE_CASE,
)
private val ARTIST_TITLE_SEPARATORS = listOf(" - ", " – ", " — ", " | ")
private val DURATION = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")
private val SUBTITLE_SEPARATOR = Regex("""\s+[•·]\s+""")

/**
 * Título de un vídeo normal de YouTube ("Artista - Canción (Official Video)") → título y artista
 * limpios. Sin separador, el artista es el canal (sin "- Topic", "VEVO"…).
 *
 * Muchos canales publican al revés ("Canción - Artista"): si la parte derecha coincide con el
 * canal y la izquierda no, se invierten. Los canales "- Topic" de YouTube Music ya traen el
 * título limpio y no se parten.
 */
fun parseYouTubeVideoTitle(rawTitle: String, channel: String): TrackIdentity {
    val cleaned = rawTitle.replace(NOISE_SUFFIX, " ").replace(Regex("\\s+"), " ").trim()
    val channelArtist = cleanArtistForLyrics(channel)
    if (TOPIC_CHANNEL.containsMatchIn(channel)) {
        return TrackIdentity(title = cleaned.ifBlank { rawTitle.trim() }, artist = channelArtist)
    }
    for (separator in ARTIST_TITLE_SEPARATORS) {
        val idx = cleaned.indexOf(separator)
        if (idx > 0) {
            val left = cleaned.substring(0, idx).trim()
            val right = cleaned.substring(idx + separator.length).trim()
            if (left.isNotBlank() && right.isNotBlank()) {
                val swapped = matchesChannel(right, channel) && !matchesChannel(left, channel)
                val artist = if (swapped) right else left
                val title = if (swapped) left else right
                return TrackIdentity(title = title, artist = cleanArtistForLyrics(artist))
            }
        }
    }
    return TrackIdentity(title = cleaned.ifBlank { rawTitle.trim() }, artist = channelArtist)
}

private val TOPIC_CHANNEL = Regex("""[\s\-–—]+Topic\s*$""", RegexOption.IGNORE_CASE)
private val CHANNEL_SUFFIX = Regex("""(vevo|official|oficial|music|topic|tv|channel|records?)$""")

/** Clave comparable: sin tildes, minúsculas, solo letras y dígitos ("Beyoncé VEVO" → "beyonce"). */
private fun artistKey(text: String): String {
    var key = stripDiacritics(text).lowercase().filter { it.isLetterOrDigit() }
    while (true) {
        val trimmed = key.replace(CHANNEL_SUFFIX, "")
        if (trimmed == key || trimmed.isEmpty()) return key
        key = trimmed
    }
}

/**
 * ¿Es [side] el artista del canal? Acepta el canal exacto, o el artista principal de una
 * colaboración ("Rosalía, J Balvin" con canal "Rosalía").
 */
private fun matchesChannel(side: String, channel: String): Boolean {
    val channelKey = artistKey(channel)
    if (channelKey.length < 2) return false
    val sideKey = artistKey(side)
    if (sideKey == channelKey) return true
    val firstArtist = side.split(Regex("""\s*(,|&|\bx\b|\bft\.?|\bfeat\.?|\by\b|\band\b)\s*""", RegexOption.IGNORE_CASE))
        .firstOrNull().orEmpty()
    return artistKey(firstArtist) == channelKey
}

/**
 * Subtítulo de una fila de YouTube Music: "Artista • Álbum • 3:26" (canciones) o
 * "Artista • 3:26". Antes se guardaba entero como artista y acababa en las etiquetas del fichero.
 */
fun parseYouTubeMusicSubtitle(title: String, subtitle: String): TrackIdentity {
    val parts = subtitle.split(SUBTITLE_SEPARATOR).map { it.trim() }.filter { it.isNotBlank() }
    val withoutDuration = parts.filterNot { DURATION.matches(it) }
    val artist = withoutDuration.firstOrNull().orEmpty()
    val album = withoutDuration.drop(1).firstOrNull().orEmpty()
    return TrackIdentity(title = title.trim(), artist = cleanArtistForLyrics(artist), album = album)
}

/** Duración "m:ss" o "h:mm:ss" de la fila de YouTube Music, en segundos; `null` si no hay. */
fun parseDurationText(text: String): Int? {
    val parts = text.trim().split(':').map { it.toIntOrNull() ?: return null }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
    }
}
