package com.imontalvodev.beatmybeat.shared.download

/** Resultado de un bloque descargado por rango. */
sealed interface ChunkResponse {
    /** 206 con los bytes del rango pedido. */
    data class Partial(val bytes: ByteArray, val totalLength: Long) : ChunkResponse

    /** 200: el servidor ignoró el rango y mandó el fichero completo. */
    data class Full(val bytes: ByteArray) : ChunkResponse

    /** Fallo recuperable (timeout, 5xx): se reintenta el mismo rango. */
    data class RetryableError(val reason: String) : ChunkResponse

    /** Fallo definitivo (403 por URL caducada, 404…). */
    data class FatalError(val reason: String) : ChunkResponse
}

sealed interface RangedDownloadResult {
    data class Completed(val bytesWritten: Long) : RangedDownloadResult

    /** No se consiguió el fichero entero: nunca debe procesarse como si lo fuera. */
    data class Incomplete(val bytesWritten: Long, val expected: Long, val reason: String) : RangedDownloadResult
}

/**
 * Bucle de descarga por rangos, independiente del cliente HTTP.
 *
 * Antes, un bloque fallido cortaba el bucle y el fichero parcial se convertía y guardaba como si
 * estuviera completo: canciones cortadas sin ningún aviso. Aquí cada bloque se reintenta y, si
 * aun así no se completa, el resultado es [RangedDownloadResult.Incomplete].
 *
 * @param fetchChunk pide `bytes=start-endInclusive`.
 * @param write escribe los bytes al final del fichero de destino.
 * @param beforeChunk punto de cancelación (p. ej. `ensureActive()`), se llama antes de cada bloque.
 */
suspend fun downloadInRanges(
    knownLength: Long,
    chunkSize: Long = DEFAULT_CHUNK_SIZE,
    maxAttemptsPerChunk: Int = 3,
    fetchChunk: suspend (start: Long, endInclusive: Long) -> ChunkResponse,
    write: (ByteArray) -> Unit,
    onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    backoff: suspend (attempt: Int) -> Unit = {},
    beforeChunk: suspend () -> Unit = {},
): RangedDownloadResult {
    var total = knownLength
    var offset = 0L
    while (total < 0 || offset < total) {
        beforeChunk()
        val end = if (total > 0) minOf(offset + chunkSize, total) - 1 else offset + chunkSize - 1
        var response: ChunkResponse = ChunkResponse.RetryableError("not attempted")
        for (attempt in 1..maxAttemptsPerChunk) {
            response = fetchChunk(offset, end)
            if (response !is ChunkResponse.RetryableError) break
            if (attempt < maxAttemptsPerChunk) backoff(attempt)
        }
        when (response) {
            is ChunkResponse.Partial -> {
                if (total < 0 && response.totalLength > 0) total = response.totalLength
                if (response.bytes.isEmpty()) {
                    return if (total < 0) RangedDownloadResult.Completed(offset)
                    else RangedDownloadResult.Incomplete(offset, total, "empty chunk")
                }
                write(response.bytes)
                offset += response.bytes.size
                onProgress(offset, total)
                // Sin tamaño conocido, un bloque corto es la señal de fin de fichero.
                if (total < 0 && response.bytes.size < chunkSize) return RangedDownloadResult.Completed(offset)
            }
            is ChunkResponse.Full -> {
                // Solo es válido como primera respuesta: a mitad de descarga duplicaría datos.
                if (offset != 0L) return RangedDownloadResult.Incomplete(offset, total, "range ignored mid-download")
                write(response.bytes)
                offset = response.bytes.size.toLong()
                onProgress(offset, offset)
                return RangedDownloadResult.Completed(offset)
            }
            is ChunkResponse.RetryableError -> return RangedDownloadResult.Incomplete(offset, total, response.reason)
            is ChunkResponse.FatalError -> return RangedDownloadResult.Incomplete(offset, total, response.reason)
        }
    }
    return RangedDownloadResult.Completed(offset)
}

const val DEFAULT_CHUNK_SIZE: Long = 1_048_576L
