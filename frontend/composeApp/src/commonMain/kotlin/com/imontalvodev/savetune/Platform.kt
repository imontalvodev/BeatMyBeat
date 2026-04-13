package com.imontalvodev.savetune

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform