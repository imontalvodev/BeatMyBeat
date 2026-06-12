package com.imontalvodev.beatmybeat.ui.network

import android.content.Context
import com.imontalvodev.beatmybeat.BuildConfig
import com.imontalvodev.beatmybeat.core.VersionCompare
import com.imontalvodev.beatmybeat.ui.storage.UpdatePrefs

object UpdateChecker {

    /**
     * @param force Si true, ignora el intervalo de 12 h (p. ej. botón en Perfil).
     * @param ignoreDismissed Si true, muestra el aviso aunque el usuario pulsara «Más tarde».
     */
    fun checkForUpdate(
        context: Context,
        force: Boolean = false,
        ignoreDismissed: Boolean = false,
    ): GitHubReleaseInfo? {
        if (!force && !UpdatePrefs.shouldCheckNow(context)) return null

        val release = ReleaseUpdateClient.fetchLatestRelease() ?: return null
        if (!force) {
            UpdatePrefs.markChecked(context)
        }

        if (!VersionCompare.isNewer(release.version, BuildConfig.VERSION_NAME)) return null
        if (!ignoreDismissed && UpdatePrefs.getDismissedVersion(context) == release.version) {
            return null
        }
        return release
    }
}
