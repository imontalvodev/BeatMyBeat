package com.imontalvodev.beatmybeat.ui.network

import okhttp3.OkHttpClient

/** Cliente HTTP compartido para evitar pools duplicados y fugas de sockets. */
object AppHttpClient {
    val instance: OkHttpClient by lazy { OkHttpClient() }
}
