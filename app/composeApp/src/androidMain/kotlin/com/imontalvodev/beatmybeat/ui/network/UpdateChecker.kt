package com.imontalvodev.beatmybeat.ui.network

import android.content.Context
import com.imontalvodev.beatmybeat.BuildConfig
import com.imontalvodev.beatmybeat.core.VersionCompare
import com.imontalvodev.beatmybeat.ui.storage.UpdatePrefs

object UpdateChecker {

    fun checkForUpdate(context: Context): GitHubReleaseInfo? {
        if (!UpdatePrefs.shouldCheckNow(context)) return null

        val release = ReleaseUpdateClient.fetchLatestRelease()
        UpdatePrefs.markChecked(context)

        if (release == null) return null
        if (!VersionCompare.isNewer(release.version, BuildConfig.VERSION_NAME)) return null
        if (UpdatePrefs.getDismissedVersion(context) == release.version) return null
        return release
    }
}
