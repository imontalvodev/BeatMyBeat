package com.imontalvodev.savetune.ui.network

// Base URL del middleware SaveTune en desarrollo.
// Android Emulator special alias to reach services running on the host machine.
// Important: it's 10.0.2.2 (not 10.0.0.2).
const val MIDDLEWARE_BASE_URL: String = "http://178.104.156.71:41311"
// const val MIDDLEWARE_BASE_URL: String = "http://mini-pc-dev:41311"

// const val MIDDLEWARE_BASE_URL: String = "http://pc-dev:4001"

fun getMiddlewareBaseCandidates(preferredBaseUrl: String = MIDDLEWARE_BASE_URL): List<String> {
    val values = listOf(
        preferredBaseUrl,
        // Host visible desde emulador Android.
        "http://10.0.2.2:41311",
        // Posibles puertos de middleware/proxy usados en local.
        "http://10.0.2.2:3000",
        "http://10.0.2.2:4000",
    )
    return values
        .map { it.trim().trimEnd('/') }
        .filter { it.isNotBlank() }
        .distinct()
}
