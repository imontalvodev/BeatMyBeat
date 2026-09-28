package com.imontalvodev.beatmybeat.shared.text

import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{M}+")

actual fun stripDiacritics(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
