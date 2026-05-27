package com.imontalvodev.beatmybeat.ui.network

import android.content.Context

/**
 * Orquesta la obtención de letras: caché local → LRCLIB → lyrics.ovh.
 */
object LyricsFetcher {

    data class Request(
        val title: String,
        val artist: String,
        val album: String = "",
        val durationMs: Long = 0L,
        /** Variantes de título a probar (p. ej. título sanitizado). */
        val titleCandidates: List<String> = emptyList(),
    )

    fun fetch(context: Context, request: Request): LyricsResponse {
        val artist = cleanArtistForLyrics(request.artist).trim()
        val titles = (listOf(request.title.trim()) + request.titleCandidates)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (artist.isBlank() || titles.isEmpty()) {
            return LyricsResponse(
                success = false,
                lyrics = "",
                syncedLrc = null,
                lrclibId = null,
                source = null,
                sourceUrl = null,
                error = "MissingFields",
                message = null,
            )
        }

        for (title in titles) {
            LyricsCache.getEntry(context, title, artist)?.let { cached ->
                if (cached.hasAnyLyrics()) {
                    return cached.toResponse()
                }
            }
        }

        val durationSec = (request.durationMs / 1000L).toInt().coerceAtLeast(0)
        val album = request.album.trim()

        for (title in titles) {
            val lrc = LrcLibApi.fetchLyrics(
                trackName = title,
                artistName = artist,
                albumName = album,
                durationSeconds = durationSec,
            )
            if (lrc.success && lrc.lyrics.isNotBlank()) {
                LyricsCache.putFromResponse(context, title, artist, lrc)
                return lrc
            }
        }

        for (title in titles) {
            val ovh = MiddlewareApi.fetchLyricsDirect(title = title, artist = artist)
            if (ovh.success && ovh.lyrics.isNotBlank()) {
                LyricsCache.putFromResponse(context, title, artist, ovh)
                return ovh
            }
        }

        return LyricsResponse(
            success = false,
            lyrics = "",
            syncedLrc = null,
            lrclibId = null,
            source = null,
            sourceUrl = null,
            error = "NotFound",
            message = null,
        )
    }
}
