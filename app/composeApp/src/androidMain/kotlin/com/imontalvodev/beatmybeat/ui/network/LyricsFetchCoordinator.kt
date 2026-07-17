package com.imontalvodev.beatmybeat.ui.network

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Cola global de descargas de letras: máximo [MAX_CONCURRENT_FETCHES] peticiones de red
 * en paralelo y deduplicación de la misma pista mientras hay una en curso.
 */
object LyricsFetchCoordinator {

    private const val MAX_CONCURRENT_FETCHES = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val limiter = Semaphore(MAX_CONCURRENT_FETCHES)
    private val inFlight = ConcurrentHashMap<String, Deferred<LyricsResponse>>()

    suspend fun fetch(
        context: Context,
        request: LyricsFetcher.Request,
        skipCache: Boolean = false,
    ): LyricsResponse {
        val appContext = context.applicationContext
        val key = request.inFlightKey(skipCache)

        inFlight[key]?.let { existing ->
            if (existing.isActive) return existing.await()
        }

        // LAZY: el cuerpo (incluida la llamada bloqueante de red) no arranca hasta que alguien
        // haga await()/start(). Si perdemos la carrera de putIfAbsent, cancel() aquí no deja
        // ningún permiso del semáforo adquirido ni ninguna llamada HTTP en curso que cancelar.
        val deferred = scope.async(start = CoroutineStart.LAZY) {
            limiter.withPermit {
                LyricsFetcher.fetch(appContext, request, skipCache)
            }
        }

        val existing = inFlight.putIfAbsent(key, deferred)
        if (existing != null) {
            deferred.cancel()
            return existing.await()
        }

        return try {
            deferred.await()
        } finally {
            inFlight.remove(key, deferred)
        }
    }

    private fun LyricsFetcher.Request.inFlightKey(skipCache: Boolean): String {
        val primaryTitle = (listOf(title.trim()) + titleCandidates)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .lowercase()
        val primaryArtist = buildLyricsArtistCandidates(artist, artistCandidates)
            .firstOrNull()
            .orEmpty()
            .lowercase()
        val durationSec = (durationMs / 1000L).toString()
        return "$primaryTitle|$primaryArtist|$durationSec|skip=$skipCache"
    }
}
