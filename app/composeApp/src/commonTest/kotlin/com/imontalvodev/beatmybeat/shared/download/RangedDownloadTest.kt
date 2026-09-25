package com.imontalvodev.beatmybeat.shared.download

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RangedDownloadTest {

    private val file = ByteArray(10) { it.toByte() }

    private fun serve(start: Long, end: Long): ChunkResponse.Partial {
        val last = minOf(end, file.size - 1L).toInt()
        return ChunkResponse.Partial(file.copyOfRange(start.toInt(), last + 1), file.size.toLong())
    }

    @Test
    fun downloadsAllChunksWhenLengthIsKnown() = runTest {
        val out = mutableListOf<Byte>()
        val result = downloadInRanges(
            knownLength = 10,
            chunkSize = 4,
            fetchChunk = { s, e -> serve(s, e) },
            write = { out += it.toList() },
        )
        assertEquals(RangedDownloadResult.Completed(10), result)
        assertContentEquals(file, out.toByteArray())
    }

    @Test
    fun learnsLengthFromFirstChunkWhenUnknown() = runTest {
        val out = mutableListOf<Byte>()
        val result = downloadInRanges(
            knownLength = -1,
            chunkSize = 4,
            fetchChunk = { s, e -> serve(s, e) },
            write = { out += it.toList() },
        )
        assertEquals(RangedDownloadResult.Completed(10), result)
        assertContentEquals(file, out.toByteArray())
    }

    @Test
    fun transientFailuresAreRetried() = runTest {
        var failuresLeft = 2
        val result = downloadInRanges(
            knownLength = 10,
            chunkSize = 4,
            fetchChunk = { s, e ->
                if (s == 4L && failuresLeft > 0) {
                    failuresLeft--
                    ChunkResponse.RetryableError("timeout")
                } else {
                    serve(s, e)
                }
            },
            write = {},
        )
        assertEquals(RangedDownloadResult.Completed(10), result)
    }

    @Test
    fun persistentFailureMidDownloadIsReportedAsIncomplete() = runTest {
        val result = downloadInRanges(
            knownLength = 10,
            chunkSize = 4,
            fetchChunk = { s, e -> if (s >= 4L) ChunkResponse.RetryableError("timeout") else serve(s, e) },
            write = {},
        )
        assertTrue(result is RangedDownloadResult.Incomplete)
        assertEquals(4, result.bytesWritten)
    }

    @Test
    fun expiredUrlIsNotRetried() = runTest {
        var calls = 0
        val result = downloadInRanges(
            knownLength = 10,
            chunkSize = 4,
            fetchChunk = { _, _ -> calls++; ChunkResponse.FatalError("HTTP 403") },
            write = {},
        )
        assertTrue(result is RangedDownloadResult.Incomplete)
        assertEquals(1, calls)
    }

    @Test
    fun fullResponseIsAcceptedOnlyAsFirstChunk() = runTest {
        val first = downloadInRanges(
            knownLength = 10,
            chunkSize = 4,
            fetchChunk = { _, _ -> ChunkResponse.Full(file) },
            write = {},
        )
        assertEquals(RangedDownloadResult.Completed(10), first)

        val mid = downloadInRanges(
            knownLength = 10,
            chunkSize = 4,
            fetchChunk = { s, e -> if (s == 0L) serve(s, e) else ChunkResponse.Full(file) },
            write = {},
        )
        assertTrue(mid is RangedDownloadResult.Incomplete)
    }
}
