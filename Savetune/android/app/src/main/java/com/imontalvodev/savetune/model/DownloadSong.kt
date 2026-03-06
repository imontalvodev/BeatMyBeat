package com.imontalvodev.savetune.model

data class DownloadSong(
    val id: String? = null,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null
)

