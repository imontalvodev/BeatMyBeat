package com.imontalvodev.beatmybeat.ui.storage

import android.content.Context

object UpdatePrefs {
    private const val PREFS = "beatmybeat_update_prefs"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val KEY_DISMISSED_VERSION = "dismissed_version"
    private const val KEY_PENDING_APK_DOWNLOAD_ID = "pending_apk_download_id"
    private const val KEY_PENDING_APK_VERSION = "pending_apk_version"
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

    fun setPendingApkDownloadId(context: Context, downloadId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PENDING_APK_DOWNLOAD_ID, downloadId)
            .apply()
    }

    fun getPendingApkDownloadId(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_PENDING_APK_DOWNLOAD_ID, -1L)

    fun clearPendingApkDownloadId(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_APK_DOWNLOAD_ID)
            .apply()
    }

    fun setPendingApkVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_APK_VERSION, version)
            .apply()
    }

    fun getPendingApkVersion(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_APK_VERSION, "") ?: ""

    fun clearPendingApkVersion(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_APK_VERSION)
            .apply()
    }
}
