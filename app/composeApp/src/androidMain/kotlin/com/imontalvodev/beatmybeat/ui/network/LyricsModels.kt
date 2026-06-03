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
