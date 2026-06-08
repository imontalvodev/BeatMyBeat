package com.imontalvodev.beatmybeat.ui.network

import java.text.Normalizer
import kotlin.math.abs

/** Resultado de búsqueda LRCLIB reducido a campos comparables (testeable sin org.json). */
internal data class LrcLyricsCandidate(
    val trackName: String,
    val artistName: String,
    val durationSeconds: Int = -1,
    val hasSyncedLyrics: Boolean = false,
    val hasPlainLyrics: Boolean = false,
)

/** Puntuación mínima de similitud de título (0–30). */
internal const val LRC_MIN_TITLE_MATCH = 15

/** Puntuación mínima de similitud de artista cuando se conoce el artista esperado. */
internal const val LRC_MIN_ARTIST_MATCH = 15

/** Tolerancia “suave” de duración (variantes de álbum, ±1 s, etc.). */
internal const val LRC_DURATION_SOFT_TOLERANCE_SEC = 5

/** Por encima de esto se descarta el candidato si ambas duraciones son conocidas. */
internal const val LRC_DURATION_HARD_MISMATCH_SEC = 15

internal fun normalizeLyricsMatchText(raw: String): String =
    Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ")
        .replace(
            Regex("(?i)\\b(feat\\.?|ft\\.?|featuring|remaster(ed)?|official|audio|video|live|lyrics|prod\\.?|produced)\\b"),
            " ",
        )
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun lyricsTitleMatchScore(expected: String, actual: String): Int {
    val a = normalizeLyricsMatchText(expected)
    val b = normalizeLyricsMatchText(actual)
    if (a.isBlank() || b.isBlank()) return 0
    if (a == b) return 30
    if (b.contains(a) || a.contains(b)) return 20
    val aTokens = a.split(' ').filter { it.length > 2 }
    val bTokens = b.split(' ').filter { it.length > 2 }
    if (aTokens.isEmpty() || bTokens.isEmpty()) return 0
    val overlap = aTokens.count { token -> bTokens.any { it.contains(token) || token.contains(it) } }
    return (overlap * 10).coerceAtMost(25)
}

internal fun durationMismatchSeconds(expectedSec: Int, candidateSec: Int): Int? {
    if (expectedSec <= 0 || candidateSec <= 0) return null
    return abs(candidateSec - expectedSec)
}

internal fun isAcceptableLrcCandidate(
    expectedTitle: String,
    expectedArtist: String,
    expectedDurationSec: Int,
    candidate: LrcLyricsCandidate,
): Boolean {
    val titleScore = lyricsTitleMatchScore(expectedTitle, candidate.trackName)
    if (titleScore < LRC_MIN_TITLE_MATCH) return false

    val artist = extractPrimaryArtistForLyrics(expectedArtist).trim()
    if (artist.isNotBlank()) {
        val artistScore = lyricsTitleMatchScore(artist, candidate.artistName)
        if (artistScore < LRC_MIN_ARTIST_MATCH) return false
    }

    durationMismatchSeconds(expectedDurationSec, candidate.durationSeconds)?.let { delta ->
        if (delta > LRC_DURATION_HARD_MISMATCH_SEC) return false
    }

    return true
}

internal fun scoreLrcCandidate(
    expectedTitle: String,
    expectedArtist: String,
    expectedDurationSec: Int,
    candidate: LrcLyricsCandidate,
): Int {
    if (!isAcceptableLrcCandidate(expectedTitle, expectedArtist, expectedDurationSec, candidate)) {
        return Int.MIN_VALUE
    }

    var score = lyricsTitleMatchScore(expectedTitle, candidate.trackName) * 4
    val artist = expectedArtist.trim()
    if (artist.isNotBlank()) {
        score += lyricsTitleMatchScore(artist, candidate.artistName) * 3
    }

    durationMismatchSeconds(expectedDurationSec, candidate.durationSeconds)?.let { delta ->
        score += when {
            delta <= 2 -> 40
            delta <= LRC_DURATION_SOFT_TOLERANCE_SEC -> 25
            delta <= 12 -> 8
            else -> -20
        }
    }

    if (candidate.hasSyncedLyrics) score += 15
    if (candidate.hasPlainLyrics) score += 5

    return score
}
