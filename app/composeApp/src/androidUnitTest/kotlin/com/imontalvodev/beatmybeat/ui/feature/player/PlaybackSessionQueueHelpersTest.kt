package com.imontalvodev.beatmybeat.ui.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSessionQueueHelpersTest {

    @Test
    fun appendTracksToSessionAddsAtEnd() {
        val state = SessionQueueState(listOf("a", "b", "c"), currentIndex = 1)
        val updated = appendTracksToSession(state, listOf("d", "e"))
        assertEquals(listOf("a", "b", "c", "d", "e"), updated.orderUris)
        assertEquals(1, updated.currentIndex)
        assertEquals(listOf("c", "d", "e"), pendingUrisFromSession(updated))
    }

    @Test
    fun insertTracksPlayNextInSessionInsertsAfterCurrent() {
        val state = SessionQueueState(listOf("a", "b", "c", "d"), currentIndex = 2)
        val updated = insertTracksPlayNextInSession(state, listOf("x", "y"))
        assertEquals(listOf("a", "b", "c", "x", "y", "d"), updated.orderUris)
        assertEquals(listOf("x", "y", "d"), pendingUrisFromSession(updated))
    }

    @Test
    fun ensureSessionQueueBuildsFromCurrentAndPending() {
        val state = ensureSessionQueue(
            currentTrackUri = "b",
            pendingQueueUris = listOf("c", "d"),
            existing = null,
        )
        assertEquals(SessionQueueState(listOf("b", "c", "d"), 0), state)
    }

    @Test
    fun advanceSessionToTrackUpdatesCurrentIndex() {
        val state = SessionQueueState(listOf("a", "b", "c"), currentIndex = 0)
        val updated = advanceSessionToTrack(state, "c")
        assertEquals(2, updated?.currentIndex)
        assertEquals(listOf("c"), pendingUrisFromSession(updated!!))
    }

    @Test
    fun removeTrackFromSessionAdjustsCurrentIndex() {
        val state = SessionQueueState(listOf("a", "b", "c"), currentIndex = 2)
        val updated = removeTrackFromSession(state, "b")
        assertEquals(listOf("a", "c"), updated.orderUris)
        assertEquals(1, updated.currentIndex)
    }

    @Test
    fun ensureSessionQueueReturnsExistingWhenPresent() {
        val existing = SessionQueueState(listOf("a", "b"), 1)
        assertEquals(existing, ensureSessionQueue("z", listOf("q"), existing))
    }

    @Test
    fun ensureSessionQueueReturnsNullWithoutCurrentTrack() {
        assertNull(ensureSessionQueue(null, listOf("a"), null))
    }
}
