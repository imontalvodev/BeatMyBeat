package com.imontalvodev.beatmybeat.download

import android.content.Context
import com.imontalvodev.beatmybeat.ui.data.DeviceTrack
import com.imontalvodev.beatmybeat.ui.feature.player.resolveTrackMetadata
import com.imontalvodev.beatmybeat.ui.network.LyricsCache

data class LyricsLibraryStats(
    val totalTracks: Int,
    val eligibleTracks: Int,
    val cachedTracks: Int,
) {
    val pendingTracks: Int get() = (eligibleTracks - cachedTracks).coerceAtLeast(0)
}

object LyricsLibraryStatsCalculator {
    fun compute(context: Context, tracks: List<DeviceTrack>): LyricsLibraryStats {
        val distinct = tracks.distinctBy { it.uri }
        var eligible = 0
        var cached = 0
        for (track in distinct) {
            val meta = resolveTrackMetadata(track)
            if (!isEligible(meta.title, meta.artist)) continue
            eligible++
            if (LyricsCache.getEntry(context, meta.title, meta.artist)?.hasAnyLyrics() == true) {
                cached++
            }
        }
        return LyricsLibraryStats(
            totalTracks = distinct.size,
            eligibleTracks = eligible,
            cachedTracks = cached,
        )
    }

    private fun isEligible(title: String, artist: String): Boolean {
        fun isUnknown(s: String): Boolean =
            s.equals("unknown", ignoreCase = true) ||
                s.equals("unknown artist", ignoreCase = true) ||
                s.isBlank()
        return !isUnknown(title) && !isUnknown(artist)
    }
}
