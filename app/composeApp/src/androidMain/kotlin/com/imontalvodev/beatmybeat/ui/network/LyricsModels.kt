package com.imontalvodev.beatmybeat.ui.network

data class LyricsResponse(
    val success: Boolean,
    val lyrics: String,
    /** LRC con marcas de tiempo; null si solo hay texto plano (p. ej. lyrics.ovh). */
    val syncedLrc: String? = null,
    val lrclibId: Long? = null,
    val source: String?,
    val sourceUrl: String?,
    val error: String?,
    val message: String?,
)

/**
 * Elimina sufijos de canal de YouTube del nombre del artista antes de buscar letras.
 */
fun cleanArtistForLyrics(raw: String): String {
    var result = raw.trim()
    result = result.replace(
        Regex("""[\s\-–—]+(Topic|Official|Music|VEVO|Channel|TV|Records?|Oficial)\s*$""", RegexOption.IGNORE_CASE),
        "",
    )
    result = result.replace(
        Regex("""\s*[\(\[]\s*(oficial|official|music|vevo|channel|records?|tv|ofici[ao]l)\s*[\)\]]\s*""", RegexOption.IGNORE_CASE),
        "",
    )
    return result.trim().ifBlank { raw.trim() }
}

/**
 * Extrae el nombre de artista usable en LRCLIB / lyrics.ovh a partir de metadatos de YouTube,
 * p. ej. `"Alex Lumbier • Caliente (Versión Techno) • 3:26"` → `"Alex Lumbier"`.
 */
fun extractPrimaryArtistForLyrics(raw: String): String {
    var result = raw.trim()
    if (result.isBlank()) return result

    val bulletParts = result.split(Regex("""\s*[•·|]\s*"""))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (bulletParts.size > 1) {
        result = bulletParts.first()
    }

    result = result.replace(Regex("""\s+\d{1,2}:\d{2}(:\d{2})?\s*$"""), "").trim()

    return cleanArtistForLyrics(result).ifBlank { cleanArtistForLyrics(raw) }
}

/** Artistas a probar en búsqueda de letras (primario limpio + variantes). */
fun buildLyricsArtistCandidates(rawArtist: String, extra: List<String> = emptyList()): List<String> =
    (listOf(
        extractPrimaryArtistForLyrics(rawArtist),
        cleanArtistForLyrics(rawArtist),
        rawArtist.trim(),
    ) + extra.map { extractPrimaryArtistForLyrics(it) } + extra.map { cleanArtistForLyrics(it) })
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
