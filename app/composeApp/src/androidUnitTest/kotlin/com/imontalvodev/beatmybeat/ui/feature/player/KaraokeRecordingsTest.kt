package com.imontalvodev.beatmybeat.ui.feature.player

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KaraokeRecordingsTest {

    @Test
    fun `el nombre codifica pista e instante`() {
        val name = KaraokeRecordings.fileNameFor(trackId = 42L, startedAtMs = 1_700_000_000_000L)
        assertEquals("track42_1700000000000.m4a", name)
    }

    @Test
    fun `el nombre se puede volver a leer`() {
        val name = KaraokeRecordings.fileNameFor(trackId = 42L, startedAtMs = 1_700_000_000_000L)
        assertEquals(42L, KaraokeRecordings.trackIdFromFileName(name))
        assertEquals(1_700_000_000_000L, KaraokeRecordings.startedAtFromFileName(name))
    }

    @Test
    fun `los archivos ajenos a la carpeta se ignoran`() {
        // La carpeta es privada de la app, pero nada impide que caiga algo mas ahi.
        assertNull(KaraokeRecordings.trackIdFromFileName("cancion.m4a"))
        assertNull(KaraokeRecordings.trackIdFromFileName("track42_123.mp3"))
        assertNull(KaraokeRecordings.trackIdFromFileName("trackABC_123.m4a"))
        assertNull(KaraokeRecordings.startedAtFromFileName("track42.m4a"))
    }

    @Test
    fun `el bitrate elegido cuesta menos de la mitad que una cancion`() {
        // 64 kbps mono: ~1,6 MB por toma de 3:30, frente a 3,5-7 MB de una cancion descargada.
        // Si alguien sube este valor, que sea a sabiendas.
        val bytesPerSecond = KaraokeRecordings.BITRATE_BPS / 8
        val take = bytesPerSecond * 210L // 3:30
        assertEquals(1, KaraokeRecordings.CHANNELS)
        assertTrue(take < 2 * 1024 * 1024, "una toma de 3:30 no deberia pasar de 2 MB, son $take bytes")
    }

    @Test
    fun `el tamano legible no marea con decimales`() {
        assertEquals("0 MB", formatBytes(0L))
        assertEquals("1 KB", formatBytes(100L))
        assertEquals("500 KB", formatBytes(512_000L))
        // El separador decimal es el de la locale del dispositivo: "1,6 MB" en espanol,
        // "1.6 MB" en ingles. Se construye igual que en produccion para no atar el test a una.
        assertEquals(String.format(Locale.getDefault(), "%.1f MB", 1.6), formatBytes(1_677_722L))
        assertEquals("81 MB", formatBytes(85_000_000L))
    }
}
