package com.imontalvodev.beatmybeat.ui.network

import org.junit.Assume.assumeTrue
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Prueba contra YouTube real: comprueba que la versión de NewPipe Extractor sigue extrayendo
 * streams de audio. Solo corre con `NEWPIPE_LIVE=1` (la activa el workflow de actualización
 * automática); en el resto de ejecuciones se omite.
 *
 * Si YouTube bloquea la IP del runner (bot check) o no hay red, no es un fallo del Extractor: la
 * prueba se omite en vez de fallar. Un fallo real es que la extracción llegue a parsear y rompa.
 */
class NewPipeLiveSmokeTest {

    @Test
    fun extractsAudioStreamsFromStableVideo() {
        assumeTrue("NEWPIPE_LIVE no activado", System.getenv("NEWPIPE_LIVE") == "1")
        NewPipe.init(OkHttpDownloader, Localization.DEFAULT, ContentCountry.DEFAULT)

        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$VIDEO_ID")
        } catch (e: Exception) {
            val blocked = e is SignInConfirmNotBotException || e is ReCaptchaException || e is IOException
            assumeTrue("YouTube no accesible desde este runner: $e", !blocked)
            throw e
        }

        assertTrue(info.duration > 0, "duración vacía")
        assertTrue(info.audioStreams.isNotEmpty(), "sin streams de audio")
        assertTrue(info.audioStreams.all { it.content.startsWith("http") }, "stream sin URL directa")
    }

    private companion object {
        // "Me at the zoo": el primer vídeo de YouTube, no se va a retirar.
        const val VIDEO_ID = "jNQXAC9IVRw"
    }
}
