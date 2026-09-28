package com.imontalvodev.beatmybeat.shared.text

/** Quita tildes y diacríticos ("canción" → "cancion"). Depende de la normalización Unicode de cada plataforma. */
expect fun stripDiacritics(text: String): String

/**
 * Deshace el mojibake típico de UTF-8 leído como Latin-1 ("canciÃ³n" → "canción"). Si el texto
 * no es reinterpretable como UTF-8 válido, se devuelve tal cual.
 */
fun repairLatin1Mojibake(text: String): String {
    if (text.any { it.code > 0xFF }) return text
    val bytes = ByteArray(text.length) { text[it].code.toByte() }
    return runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrDefault(text)
}
