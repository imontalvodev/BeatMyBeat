package com.imontalvodev.beatmybeat.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.imontalvodev.beatmybeat.service.PlaybackService

/**
 * [PlaybackService] ligado al ciclo de la actividad principal: no se desvincula al cambiar de pestaña,
 * para que la reproducción y la notificación sigan en segundo plano.
 */
val LocalPlaybackService = compositionLocalOf<PlaybackService?> { null }

@Composable
fun PlaybackServiceBinding(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val serviceState: MutableState<PlaybackService?> = remember { mutableStateOf(null) }

    DisposableEffect(context.applicationContext) {
        val app = context.applicationContext
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                serviceState.value = (binder as? PlaybackService.LocalBinder)?.service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceState.value = null
            }
        }
        val intent = Intent(app, PlaybackService::class.java)
        app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            runCatching { app.unbindService(connection) }
            serviceState.value = null
        }
    }

    val svc by serviceState

    CompositionLocalProvider(
        LocalPlaybackService provides svc,
    ) {
        content()
    }
}
