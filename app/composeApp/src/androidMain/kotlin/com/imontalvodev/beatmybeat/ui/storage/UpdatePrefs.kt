package com.imontalvodev.beatmybeat.ui.storage

import android.content.Context

object UpdatePrefs {
    private const val PREFS = "beatmybeat_update_prefs"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val KEY_DISMISSED_VERSION = "dismissed_version"
    private const val CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L

    fun shouldCheckNow(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val lastCheck = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK_MS, 0L)
        return nowMs - lastCheck >= CHECK_INTERVAL_MS
    }

    fun markChecked(context: Context, nowMs: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CHECK_MS, nowMs)
            .apply()
    }

    fun getDismissedVersion(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DISMISSED_VERSION, null)
            ?.takeIf { it.isNotBlank() }

    fun setDismissedVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED_VERSION, version)
            .apply()
    }
}
