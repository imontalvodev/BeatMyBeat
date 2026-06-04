package com.imontalvodev.beatmybeat.ui.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Cliente HTTP compartido para evitar pools duplicados y fugas de sockets. */
object AppHttpClient {
    /**
     * Cliente base. El resto de clientes derivan de él con [withTimeouts] para reutilizar el
     * connection pool, el dispatcher y la caché en vez de crear instancias nuevas.
     */
    val instance: OkHttpClient by lazy { OkHttpClient() }

    /**
     * Devuelve un cliente con timeouts específicos que comparte el pool/dispatcher del [instance].
     * Los valores `<= 0` dejan el valor por defecto de OkHttp para ese timeout.
     */
    fun withTimeouts(
        connectSeconds: Long = 0,
        readSeconds: Long = 0,
        writeSeconds: Long = 0,
        callSeconds: Long = 0,
        followRedirects: Boolean? = null,
    ): OkHttpClient = instance.newBuilder()
        .apply {
            if (connectSeconds > 0) connectTimeout(connectSeconds, TimeUnit.SECONDS)
            if (readSeconds > 0) readTimeout(readSeconds, TimeUnit.SECONDS)
            if (writeSeconds > 0) writeTimeout(writeSeconds, TimeUnit.SECONDS)
            if (callSeconds > 0) callTimeout(callSeconds, TimeUnit.SECONDS)
            if (followRedirects != null) followRedirects(followRedirects)
        }
        .build()
}
