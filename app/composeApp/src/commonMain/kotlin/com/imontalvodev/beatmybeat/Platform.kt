package com.imontalvodev.beatmybeat

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform