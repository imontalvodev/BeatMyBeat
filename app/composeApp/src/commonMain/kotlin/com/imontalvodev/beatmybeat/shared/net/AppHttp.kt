package com.imontalvodev.beatmybeat.shared.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.json.Json

/** JSON tolerante: las APIs públicas añaden campos sin avisar y eso no debe romper el parseo. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/**
 * Cliente Ktor común. Cada plataforma aporta su motor (OkHttp en Android); los tests usan
 * `MockEngine`. Sin `expectSuccess`: los códigos HTTP se interpretan en cada cliente, porque un
 * 404 de LRCLIB significa "no la tengo" y un 503 significa "vuelve luego".
 */
fun createAppHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(HttpTimeout)
}
