package com.imontalvodev.beatmybeat.shared.lyrics

import com.imontalvodev.beatmybeat.shared.net.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Registro de LRCLIB tal como lo devuelven `/api/get`, `/api/get-cached` y `/api/search`. */
@Serializable
internal data class LrcLibRecord(
    val id: Long = 0,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
) {
    val hasSynced: Boolean get() = !syncedLyrics.isNullOrBlank()
    val hasPlain: Boolean get() = !plainLyrics.isNullOrBlank()

    fun toCandidate() = LrcLyricsCandidate(
        trackName = trackName.orEmpty().trim(),
        artistName = artistName.orEmpty().trim(),
        durationSeconds = duration?.toInt() ?: -1,
        hasSyncedLyrics = hasSynced,
        hasPlainLyrics = hasPlain,
    )
}

/**
 * Cliente de [LRCLIB](https://lrclib.net/docs), multiplataforma.
 *
 * Orden de consulta por cada combinación título × artista:
 * 1. `/api/get-cached`: coincidencia exacta solo con la base de LRCLIB. Rápido.
 * 2. `/api/search`: búsqueda difusa, puntuada con [scoreLrcCandidate].
 * 3. `/api/get` (solo la combinación principal): puede ir a fuentes externas, lento.
 *
 * Se busca **letra sincronizada**. Si una fuente solo trae texto plano, se guarda como reserva
 * y se sigue buscando; la reserva solo se devuelve si al final no aparece ninguna sincronizada.
 */
class LrcLibClient(
    private val http: HttpClient,
    private val userAgent: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val budget: Duration = DEFAULT_BUDGET,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    suspend fun fetchLyrics(
        trackName: String,
        artistName: String,
        albumName: String,
        durationSeconds: Int,
        titleCandidates: List<String> = emptyList(),
        artistCandidates: List<String> = emptyList(),
    ): LyricsResponse {
        val titles = (listOf(trackName) + titleCandidates)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_TITLE_VARIANTS)
        val artists = buildLyricsArtistCandidates(artistName, artistCandidates).take(MAX_ARTIST_VARIANTS)
        if (titles.isEmpty() || artists.isEmpty()) return LyricsResponse.failure(ERROR_MISSING_FIELDS)

        val search = Search(
            album = albumName.trim().ifBlank { UNKNOWN_ALBUM },
            durationSeconds = durationSeconds.coerceAtLeast(0),
            deadline = timeSource.markNow() + budget,
        )
        return try {
            search.run(titles, artists)
        } catch (_: Unreachable) {
            search.partialResult(ERROR_UNREACHABLE)
        }
    }

    private inner class Search(
        val album: String,
        val durationSeconds: Int,
        val deadline: TimeMark,
    ) {
        var plainFallback: LyricsResponse? = null

        /**
         * Búsqueda cortada (red caída o sin tiempo): se devuelve la reserva de texto plano si la
         * hay, pero con [error] puesto, para que quien cachee sepa que la búsqueda no terminó y
         * que puede existir una versión sincronizada.
         */
        fun partialResult(error: String): LyricsResponse =
            plainFallback?.copy(error = error) ?: LyricsResponse.failure(error)

        private val expired: Boolean get() = deadline.hasPassedNow()

        suspend fun run(titles: List<String>, artists: List<String>): LyricsResponse {
            for ((ti, title) in titles.withIndex()) {
                for ((ai, artist) in artists.withIndex()) {
                    if (expired) return partialResult(ERROR_TIMEOUT)

                    if (durationSeconds > 0) {
                        exact("get-cached", title, artist, album, FAST_TIMEOUT)?.let { return it }
                        if (album != UNKNOWN_ALBUM) {
                            exact("get-cached", title, artist, UNKNOWN_ALBUM, FAST_TIMEOUT)?.let { return it }
                        }
                    }

                    search(title, artist)?.let { return it }

                    if (ti == 0 && ai == 0 && durationSeconds > 0 && !expired) {
                        exact("get", title, artist, album, SLOW_TIMEOUT)?.let { return it }
                    }
                }
            }
            return plainFallback ?: LyricsResponse.failure(ERROR_NOT_FOUND)
        }

        /** Devuelve una respuesta definitiva (sincronizada o instrumental); el texto plano va a la reserva. */
        private fun accept(record: LrcLibRecord, sourceUrl: String): LyricsResponse? {
            if (record.hasSynced) return record.toResponse(sourceUrl)
            if (record.instrumental && !record.hasPlain) {
                return LyricsResponse.failure(ERROR_INSTRUMENTAL).copy(lrclibId = record.id.takeIf { it > 0 })
            }
            if (record.hasPlain && plainFallback == null) plainFallback = record.toResponse(sourceUrl)
            return null
        }

        private suspend fun exact(
            endpoint: String,
            title: String,
            artist: String,
            album: String,
            timeout: Duration,
        ): LyricsResponse? {
            val params = listOf(
                "track_name" to title,
                "artist_name" to artist,
                "album_name" to album,
                "duration" to durationSeconds.toString(),
            )
            val body = request(endpoint, params, timeout) ?: return null
            val record = runCatching { AppJson.decodeFromString(LrcLibRecord.serializer(), body) }.getOrNull()
                ?: return null
            return accept(record, "$baseUrl/$endpoint")
        }

        private suspend fun search(title: String, artist: String): LyricsResponse? {
            val attempts = listOf(
                listOf("track_name" to title, "artist_name" to artist),
                listOf("q" to "$artist $title"),
            )
            var best: LrcLibRecord? = null
            var bestScore = Int.MIN_VALUE
            for (params in attempts) {
                if (expired) break
                val body = request("search", params, FAST_TIMEOUT) ?: continue
                val records = runCatching {
                    AppJson.decodeFromString(ListSerializer(LrcLibRecord.serializer()), body)
                }.getOrNull() ?: continue
                for (record in records) {
                    val score = scoreLrcCandidate(title, artist, durationSeconds, record.toCandidate())
                    if (score > bestScore) {
                        bestScore = score
                        best = record
                    }
                }
                // Con una sincronizada bien puntuada no merece la pena la búsqueda libre.
                if (best?.hasSynced == true && bestScore >= STRONG_MATCH_SCORE) break
            }
            val record = best?.takeIf { bestScore != Int.MIN_VALUE } ?: return null
            return accept(record, "$baseUrl/search")
        }

        private suspend fun request(
            endpoint: String,
            params: List<Pair<String, String>>,
            timeout: Duration,
        ): String? {
            val remaining = -deadline.elapsedNow()
            if (!remaining.isPositive()) return null
            val effective = minOf(timeout, remaining)
            try {
                val response = http.get("$baseUrl/$endpoint") {
                    params.forEach { (key, value) -> parameter(key, value) }
                    header(HttpHeaders.Accept, "application/json")
                    header(HttpHeaders.UserAgent, userAgent)
                    timeout {
                        requestTimeoutMillis = effective.inWholeMilliseconds
                        connectTimeoutMillis = minOf(effective, CONNECT_TIMEOUT).inWholeMilliseconds
                    }
                }
                val status = response.status
                return when {
                    status.isSuccess() -> response.bodyAsText().takeIf { it.isNotBlank() }
                    // Saturación o caída de LRCLIB: no es "no existe". Si se tratara como tal,
                    // la pista quedaría cacheada sin letra sincronizada por un fallo pasajero.
                    status == HttpStatusCode.TooManyRequests || status.value >= 500 -> throw Unreachable()
                    else -> null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Unreachable) {
                throw e
            } catch (_: HttpRequestTimeoutException) {
                throw Unreachable()
            } catch (_: IOException) {
                throw Unreachable()
            }
        }
    }

    private class Unreachable : Exception()

    private fun LrcLibRecord.toResponse(sourceUrl: String): LyricsResponse {
        val synced = syncedLyrics?.takeIf { it.isNotBlank() }
        val plain = plainLyrics?.takeIf { it.isNotBlank() } ?: synced?.let { LrcParser.toPlainText(it) }.orEmpty()
        return LyricsResponse(
            success = true,
            lyrics = plain,
            syncedLrc = synced,
            lrclibId = id.takeIf { it > 0 },
            source = SOURCE,
            sourceUrl = sourceUrl,
            error = null,
            message = null,
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://lrclib.net/api"
        const val SOURCE = "lrclib"
        internal const val UNKNOWN_ALBUM = "Unknown Album"
        private const val MAX_TITLE_VARIANTS = 2
        private const val MAX_ARTIST_VARIANTS = 2
        private const val STRONG_MATCH_SCORE = 75

        /** Tope total por pista: sin él, una pista lenta bloqueaba el lote de letras minutos. */
        val DEFAULT_BUDGET: Duration = 20.seconds
        private val FAST_TIMEOUT: Duration = 12.seconds
        private val SLOW_TIMEOUT: Duration = 25.seconds
        private val CONNECT_TIMEOUT: Duration = 8.seconds
    }
}
