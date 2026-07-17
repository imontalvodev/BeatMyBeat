package com.imontalvodev.beatmybeat.ui.feature.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Acción "Instalar" de la notificación de actualización lista (ver [ApkUpdateInstaller]). */
class ApkUpdateInstallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL) return
        val apkUri = intent.data ?: return
        ApkUpdateInstaller.installNow(context.applicationContext, apkUri)
    }

    companion object {
        const val ACTION_INSTALL = "com.imontalvodev.beatmybeat.action.INSTALL_UPDATE"
    }
}
