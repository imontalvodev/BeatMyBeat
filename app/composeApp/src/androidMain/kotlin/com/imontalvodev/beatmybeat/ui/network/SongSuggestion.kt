package com.imontalvodev.beatmybeat.ui.network

data class SongSuggestion(
    val title: String,
    val artist: String,
    val videoId: String = "",
    val thumbnailUrl: String = "",
    val durationText: String = "",
    val source: YouTubeSearchSource = YouTubeSearchSource.YOUTUBE,
)
