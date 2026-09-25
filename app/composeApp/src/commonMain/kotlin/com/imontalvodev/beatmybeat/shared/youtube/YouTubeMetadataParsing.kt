package com.imontalvodev.beatmybeat.shared.youtube

import com.imontalvodev.beatmybeat.shared.lyrics.cleanArtistForLyrics

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
 */
fun parseYouTubeVideoTitle(rawTitle: String, channel: String): TrackIdentity {
    val cleaned = rawTitle.replace(NOISE_SUFFIX, " ").replace(Regex("\\s+"), " ").trim()
    for (separator in ARTIST_TITLE_SEPARATORS) {
        val idx = cleaned.indexOf(separator)
        if (idx > 0) {
            val artist = cleaned.substring(0, idx).trim()
            val title = cleaned.substring(idx + separator.length).trim()
            if (artist.isNotBlank() && title.isNotBlank()) {
                return TrackIdentity(title = title, artist = cleanArtistForLyrics(artist))
            }
        }
    }
    return TrackIdentity(title = cleaned.ifBlank { rawTitle.trim() }, artist = cleanArtistForLyrics(channel))
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
