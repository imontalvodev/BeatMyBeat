package com.imontalvodev.beatmybeat.ui.network

import com.imontalvodev.beatmybeat.BuildConfig
import com.imontalvodev.beatmybeat.shared.lyrics.LrcLibClient
import com.imontalvodev.beatmybeat.shared.lyrics.LyricsOvhClient
import com.imontalvodev.beatmybeat.shared.net.createAppHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/** Clientes de letras de `commonMain` montados sobre el pool OkHttp de la app. */
object LyricsClients {

    /** LRCLIB pide identificar la app con nombre, versión y web en el User-Agent. */
    private val userAgent =
        "BeatMyBeat/${BuildConfig.VERSION_NAME} (https://github.com/imontalvodev/BeatMyBeat)"

    private val http: HttpClient by lazy {
        createAppHttpClient(OkHttp.create { preconfigured = AppHttpClient.instance })
    }

    val lrcLib: LrcLibClient by lazy { LrcLibClient(http, userAgent) }

    val lyricsOvh: LyricsOvhClient by lazy { LyricsOvhClient(http, userAgent) }
}
