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
 * Corrige texto UTF-8 mal interpretado como Latin-1 (p. ej. `Â€¢` → `•`).
 */
fun normalizeDisplayMetadata(raw: String): String {
    var text = raw.trim()
    if (text.isEmpty()) return text

    text = text
        .replace("Ã¢â‚¬Â¢", "•")
        .replace("Â€¢", "•")
        .replace("â€¢", "•")
        .replace("Ã‚Â·", "·")
        .replace("Â·", "·")
        .replace("â€™", "'")
        .replace("Ã¢â‚¬â„¢", "'")
        .replace("â€œ", "\"")
        .replace("â€", "\"")

    if (text.contains('Ã') || text.contains('Â')) {
        text = runCatching {
            String(text.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        }.getOrDefault(text)
    }
    return text
}

/** Artista legible en la UI a partir de metadatos de YouTube/MediaStore. */
fun formatArtistForDisplay(raw: String): String {
    val normalized = normalizeDisplayMetadata(raw)
    return extractPrimaryArtistForLyrics(normalized).ifBlank { normalized }
}

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
    var result = normalizeDisplayMetadata(raw)
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
