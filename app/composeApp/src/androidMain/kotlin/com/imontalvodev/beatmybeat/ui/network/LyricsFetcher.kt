package com.imontalvodev.beatmybeat.ui.network

import android.content.Context

/**
 * Orquesta la obtención de letras: caché local → LRCLIB → lyrics.ovh.
 * Las peticiones concurrentes y el límite de paralelismo van en [LyricsFetchCoordinator].
 */
object LyricsFetcher {

    data class Request(
        val title: String,
        val artist: String,
        val album: String = "",
        val durationMs: Long = 0L,
        /** Variantes de título a probar (p. ej. título sanitizado). */
        val titleCandidates: List<String> = emptyList(),
        /** Variantes de artista (p. ej. nombre sin limpiar). */
        val artistCandidates: List<String> = emptyList(),
    )

    fun fetch(
        context: Context,
        request: Request,
        skipCache: Boolean = false,
    ): LyricsResponse {
        val titles = (listOf(request.title.trim()) + request.titleCandidates)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val artists = buildLyricsArtistCandidates(request.artist, request.artistCandidates)

        if (artists.isEmpty() || titles.isEmpty()) {
            return notFound("MissingFields")
        }

        if (!skipCache) {
            for (title in titles) {
                for (artist in artists) {
                    LyricsCache.getEntry(context, title, artist)?.let { cached ->
                        if (cached.hasAnyLyrics()) {
                            return cached.toResponse()
                        }
                    }
                }
            }
        }

        val durationSec = (request.durationMs / 1000L).toInt().coerceAtLeast(0)
        val album = request.album.trim()

        val lrc = LrcLibApi.fetchLyrics(
            trackName = titles.first(),
            artistName = artists.first(),
            albumName = album,
            durationSeconds = durationSec,
            titleCandidates = titles.drop(1),
            artistCandidates = artists.drop(1),
        )
        if (lrc.success && lrc.lyrics.isNotBlank()) {
            LyricsCache.putFromResponse(context, titles.first(), artists.first(), lrc)
            return lrc
        }

        for (title in titles.take(2)) {
            for (artist in artists.take(2)) {
                val ovh = LyricsOvhApi.fetch(title = title, artist = artist)
                if (ovh.success && ovh.lyrics.isNotBlank()) {
                    LyricsCache.putFromResponse(context, title, artist, ovh)
                    return ovh
                }
            }
        }

        return notFound("NotFound")
    }

    private fun notFound(error: String) = LyricsResponse(
        success = false,
        lyrics = "",
        syncedLrc = null,
        lrclibId = null,
        source = null,
        sourceUrl = null,
        error = error,
        message = null,
    )
}
