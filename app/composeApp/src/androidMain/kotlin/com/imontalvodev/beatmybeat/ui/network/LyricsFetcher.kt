package com.imontalvodev.beatmybeat.ui.network

import android.content.Context
import com.imontalvodev.beatmybeat.shared.lyrics.ERROR_MISSING_FIELDS
import com.imontalvodev.beatmybeat.shared.lyrics.ERROR_NOT_FOUND
import com.imontalvodev.beatmybeat.shared.lyrics.LyricsResponse
import com.imontalvodev.beatmybeat.shared.lyrics.buildLyricsArtistCandidates
import com.imontalvodev.beatmybeat.shared.lyrics.isLyricsNetworkFailure

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

    suspend fun fetch(
        context: Context,
        request: Request,
        skipCache: Boolean = false,
    ): LyricsResponse {
        val titles = (listOf(request.title.trim()) + request.titleCandidates)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val artists = buildLyricsArtistCandidates(request.artist, request.artistCandidates)

        if (artists.isEmpty() || titles.isEmpty()) return LyricsResponse.failure(ERROR_MISSING_FIELDS)

        if (!skipCache) {
            for (title in titles) {
                for (artist in artists) {
                    val cached = LyricsCache.getEntry(context, title, artist) ?: continue
                    if (cached.instrumental) return cached.toResponse()
                    // Una entrada guardada sin haber podido consultar LRCLIB (texto plano de
                    // lyrics.ovh tras un timeout) no debe bloquear el intento de conseguir
                    // letra sincronizada: se ignora aquí y se vuelve a preguntar.
                    if (cached.hasAnyLyrics() && (cached.lrclibChecked || !cached.syncedLrc.isNullOrBlank())) {
                        return cached.toResponse()
                    }
                }
            }
        }

        val lrc = LyricsClients.lrcLib.fetchLyrics(
            trackName = titles.first(),
            artistName = artists.first(),
            albumName = request.album,
            durationSeconds = (request.durationMs / 1000L).toInt(),
            titleCandidates = titles.drop(1),
            artistCandidates = artists.drop(1),
        )
        if (lrc.isInstrumental) {
            LyricsCache.putEntry(
                context,
                titles.first(),
                artists.first(),
                LyricsCacheEntry(plain = "", instrumental = true, lrclibId = lrc.lrclibId, lrclibChecked = true),
            )
            return lrc
        }
        if (lrc.success && lrc.lyrics.isNotBlank()) {
            // Con error puesto la búsqueda se cortó y esto es solo la reserva en texto plano:
            // se guarda sin "consultado" para volver a por la sincronizada más adelante.
            LyricsCache.putFromResponse(context, titles.first(), artists.first(), lrc, lrclibChecked = lrc.error == null)
            return lrc
        }

        // Si LRCLIB no contestó (timeout/DNS/conexión), NO se cae a lyrics.ovh: devuelve texto
        // plano sin sincronizar, y al cachearlo dejaría la pista sin karaoke posible. Ante un
        // fallo pasajero es mejor no tener letra ahora que tener la mala para siempre.
        if (isLyricsNetworkFailure(lrc.error)) return lrc

        for (title in titles.take(2)) {
            for (artist in artists.take(2)) {
                val ovh = LyricsClients.lyricsOvh.fetch(title = title, artist = artist)
                if (ovh.success && ovh.lyrics.isNotBlank()) {
                    // LRCLIB sí respondió (dijo que no la tiene), así que esta entrada plana es
                    // definitiva y no hay que volver a preguntarle.
                    LyricsCache.putFromResponse(context, title, artist, ovh, lrclibChecked = true)
                    return ovh
                }
            }
        }

        return LyricsResponse.failure(ERROR_NOT_FOUND)
    }
}
