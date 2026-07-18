package com.imontalvodev.beatmybeat.ui.feature.player

import android.content.Context
import android.media.AudioDeviceInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import android.media.AudioDeviceCallback
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import com.imontalvodev.beatmybeat.core.Logger
import java.io.File

/**
 * Grabación de voz para el Modo Karaoke (Fase F).
 *
 * Graba **solo la voz**: Android no ofrece ninguna API que capture "micro + lo que suena" en un
 * archivo. Mezclar exigiría decodificar ambas pistas a PCM, sumarlas y recodificar con
 * MediaCodec/MediaMuxer — eso queda para la exportación, que es opcional.
 *
 * Para revisar la toma se reproducen la canción y la voz a la vez, sincronizadas por
 * [Session.trackOffsetMs], que es la posición de la canción en el instante en que empezó a grabar.
 */
class KaraokeRecorder(private val context: Context) {

    /** Toma en curso o terminada, con lo necesario para reproducirla junto a la canción. */
    data class Session(
        val file: File,
        val trackId: Long,
        /** Posición de la canción (ms) cuando arrancó la grabación. */
        val trackOffsetMs: Long,
        val startedAtMs: Long,
    )

    private var recorder: MediaRecorder? = null
    private var current: Session? = null

    val isRecording: Boolean get() = recorder != null

    /**
     * Arranca la grabación. Devuelve la sesión, o `null` si el micro no se pudo abrir (otra app lo
     * tiene tomado, permiso revocado en caliente, dispositivo sin micro).
     *
     * El llamador debe haber comprobado ya el permiso `RECORD_AUDIO`.
     */
    fun start(trackId: Long, trackPositionMs: Long): Session? {
        if (isRecording) return current

        val startedAt = System.currentTimeMillis()
        val file = KaraokeRecordings.newFile(context, trackId, startedAt)

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return runCatching {
            rec.apply {
                // MIC y no VOICE_COMMUNICATION: este último aplica el cancelador de eco del
                // sistema, pensado para llamadas — mono de banda reducida y AGC agresivo, que
                // para cantar suena mal. El eco se evita pidiendo auriculares (ver
                // [headphonesConnected]), no degradando la voz.
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(KaraokeRecordings.CHANNELS)
                setAudioEncodingBitRate(KaraokeRecordings.BITRATE_BPS)
                setAudioSamplingRate(KaraokeRecordings.SAMPLE_RATE_HZ)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            val session = Session(
                file = file,
                trackId = trackId,
                trackOffsetMs = trackPositionMs.coerceAtLeast(0L),
                startedAtMs = startedAt,
            )
            current = session
            session
        }.getOrElse { error ->
            Logger.e(LOG_TAG, "No se pudo iniciar la grabación", error)
            runCatching { rec.release() }
            file.delete()
            recorder = null
            current = null
            null
        }
    }

    /**
     * Para la grabación y devuelve la toma para revisarla. Devuelve `null` si no había nada
     * grabando o si la toma salió vacía — `MediaRecorder.stop()` lanza si se para demasiado
     * pronto, y en ese caso el archivo no es reproducible y se borra.
     */
    fun stop(): Session? {
        val rec = recorder ?: return null
        val session = current
        recorder = null
        current = null

        val stoppedOk = runCatching { rec.stop() }
            .onFailure { Logger.w(LOG_TAG, "stop() falló (toma demasiado corta): ${it.message}") }
            .isSuccess
        runCatching { rec.release() }

        if (session == null) return null
        if (!stoppedOk || !session.file.exists() || session.file.length() <= 0L) {
            session.file.delete()
            return null
        }
        return session
    }

    /** Cancela la toma en curso y borra el archivo. Para salir del modo sin dejar basura. */
    fun cancel() {
        val session = stop()
        session?.let { KaraokeRecordings.delete(it.file) }
    }

    companion object {
        private const val LOG_TAG = "KaraokeRecorder"

        /**
         * ¿Hay auriculares (cable, USB o Bluetooth)? Sin ellos, el micro capta la canción por el
         * altavoz y la toma sale con la pista duplicada y desfasada, no con la voz limpia.
         *
         * Es una **recomendación, no un requisito**: grabar sin auriculares está permitido.
         */
        fun headphonesConnected(context: Context): Boolean {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        }
    }
}

/**
 * Estado reactivo de "hay auriculares conectados".
 *
 * Usa `AudioDeviceCallback` en vez de sondear: si el usuario enchufa los auriculares con el
 * reproductor abierto, el aviso desaparece solo.
 */
@Composable
fun rememberHeadphonesConnected(): Boolean {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(KaraokeRecorder.headphonesConnected(context)) }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            onDispose { }
        } else {
            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    connected = KaraokeRecorder.headphonesConnected(context)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    connected = KaraokeRecorder.headphonesConnected(context)
                }
            }
            audioManager.registerAudioDeviceCallback(callback, null)
            onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
        }
    }
    return connected
}
