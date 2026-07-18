package com.imontalvodev.beatmybeat.ui.feature.player

import java.util.Calendar
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KaraokeRecordingsTest {

    @Test
    fun `el nombre es REC mas fecha y hora`() {
        // Hora local: es la que el usuario reconoce al ver el archivo en su carpeta.
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 18, 11, 9, 57)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals("REC-2026-07-18-11-09-57.m4a", KaraokeRecordings.fileNameFor(cal.timeInMillis))
    }

    @Test
    fun `un nombre generado se reconoce como grabacion`() {
        val name = KaraokeRecordings.fileNameFor(System.currentTimeMillis())
        assertTrue(KaraokeRecordings.isRecordingFileName(name))
    }

    @Test
    fun `la musica del usuario no se filtra`() {
        // Filtrar de mas es peor que filtrar de menos: esconderle musica propia al usuario es un
        // fallo silencioso y ademas cuesta de diagnosticar.
        assertFalse(KaraokeRecordings.isRecordingFileName("REC-ensayo.m4a"))
        assertFalse(KaraokeRecordings.isRecordingFileName("RECuerdos.mp3"))
        assertFalse(KaraokeRecordings.isRecordingFileName("Rammstein - Du Hast.m4a"))
        assertFalse(KaraokeRecordings.isRecordingFileName("REC.m4a"))
        assertFalse(KaraokeRecordings.isRecordingFileName("REC-2026-07-18.m4a"))
        assertFalse(KaraokeRecordings.isRecordingFileName(""))
    }

    @Test
    fun `el prefijo por si solo no basta`() {
        // La consulta a MediaStore usa LIKE 'REC-%', que casaria estos; por eso el nombre completo
        // se vuelve a validar despues de la consulta.
        assertFalse(KaraokeRecordings.isRecordingFileName("REC-mi cancion favorita.m4a"))
        assertFalse(KaraokeRecordings.isRecordingFileName("REC-2026-07-18-11-09-57"))
    }

    @Test
    fun `se reconoce con espacios alrededor`() {
        assertTrue(KaraokeRecordings.isRecordingFileName("  REC-2026-07-18-11-09-57.m4a  "))
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
