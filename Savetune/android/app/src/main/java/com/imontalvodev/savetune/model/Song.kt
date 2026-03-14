package com.imontalvodev.savetune.model

import java.io.File

data class Song(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int = 0,
    val file: File? = null,
    val mediaStoreId: Long? = null
)

