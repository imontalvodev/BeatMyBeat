package com.imontalvodev.beatmybeat.core

import android.util.Log
import com.imontalvodev.beatmybeat.BuildConfig

/**
 * Wrapper de logging que solo escribe en builds debug.
 * En release todas las llamadas son no-op para no filtrar trazas ni penalizar rendimiento.
 */
object Logger {

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, message, throwable)
    }
}
