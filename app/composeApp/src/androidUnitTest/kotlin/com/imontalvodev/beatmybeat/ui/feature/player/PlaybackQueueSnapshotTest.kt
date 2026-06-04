package com.imontalvodev.beatmybeat.ui.feature.player

import com.imontalvodev.beatmybeat.ui.data.DeviceTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackQueueSnapshotTest {

    private fun track(uri: String, id: Long = uri.hashCode().toLong()) = DeviceTrack(
        id = id,
        uri = uri,
        title = "Title $uri",
        artist = "Artist",
        album = null,
        durationMs = 1000L,
    )

    private fun libraryOf(vararg uris: String): Map<String, DeviceTrack> =
        uris.associateWith { track(it) }

    @Test
    fun emptySnapshotIsEmpty() {
        val snapshot = PlaybackQueueSnapshot(emptyList(), 0, 0L, false)
        assertTrue(snapshot.isEmpty)
    }

    @Test
    fun resolveReturnsNullForEmptyOrder() {
        val snapshot = PlaybackQueueSnapshot(emptyList(), 0, 0L, false)
        assertNull(resolvePlaybackQueueSnapshot(snapshot, libraryOf("a", "b")))
    }

    @Test
    fun resolveReturnsNullWhenNoUriExistsInLibrary() {
        val snapshot = PlaybackQueueSnapshot(listOf("x", "y"), 0, 0L, false)
        assertNull(resolvePlaybackQueueSnapshot(snapshot, libraryOf("a", "b")))
    }

    @Test
    fun resolveKeepsOnlyExistingTracksPreservingOrder() {
        val snapshot = PlaybackQueueSnapshot(listOf("a", "gone", "b"), 0, 1500L, true)
        val resolved = resolvePlaybackQueueSnapshot(snapshot, libraryOf("a", "b"))!!
        assertEquals(listOf("a", "b"), resolved.tracks.map { it.uri })
        assertEquals(1500L, resolved.positionMs)
        assertTrue(resolved.shuffleOn)
    }

    @Test
    fun resolveMapsCurrentIndexToResolvedList() {
        // Order: a(0), gone(1), b(2), c(3); current points at b.
        val snapshot = PlaybackQueueSnapshot(listOf("a", "gone", "b", "c"), 2, 0L, false)
        val resolved = resolvePlaybackQueueSnapshot(snapshot, libraryOf("a", "b", "c"))!!
        assertEquals(listOf("a", "b", "c"), resolved.tracks.map { it.uri })
        assertEquals("b", resolved.tracks[resolved.currentIndex].uri)
    }

    @Test
    fun resolveAdvancesWhenCurrentTrackDisappeared() {
        // current index points at the removed "gone"; should advance to next existing ("c").
        val snapshot = PlaybackQueueSnapshot(listOf("a", "gone", "c"), 1, 0L, false)
        val resolved = resolvePlaybackQueueSnapshot(snapshot, libraryOf("a", "c"))!!
        assertEquals("c", resolved.tracks[resolved.currentIndex].uri)
    }

    @Test
    fun resolveFallsBackToPreviousWhenNoLaterTrackExists() {
        // current points at last "gone"; no later track, fall back to previous existing ("a").
        val snapshot = PlaybackQueueSnapshot(listOf("a", "gone"), 1, 0L, false)
        val resolved = resolvePlaybackQueueSnapshot(snapshot, libraryOf("a"))!!
        assertEquals(0, resolved.currentIndex)
        assertEquals("a", resolved.tracks[resolved.currentIndex].uri)
    }

    @Test
    fun resolveClampsOutOfRangeCurrentIndex() {
        val snapshot = PlaybackQueueSnapshot(listOf("a", "b"), 99, 0L, false)
        val resolved = resolvePlaybackQueueSnapshot(snapshot, libraryOf("a", "b"))!!
        assertTrue(resolved.currentIndex in resolved.tracks.indices)
    }
}
