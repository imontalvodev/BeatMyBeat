package com.imontalvodev.savetune.model

data class PlaylistInfo(
    val name: String,
    val totalTracks: Int,
    val songs: List<DownloadSong>
)

