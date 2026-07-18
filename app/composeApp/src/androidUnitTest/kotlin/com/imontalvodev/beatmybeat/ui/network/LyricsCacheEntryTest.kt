package com.imontalvodev.beatmybeat.ui.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regresión del caso "la biblioteca se queda sin karaoke": un timeout de LRCLIB durante una
 * descarga masiva hacía que se guardara texto plano de lyrics.ovh, y esa entrada respondía a
 * partir de entonces sin volver a consultar LRCLIB nunca.
 */
class LyricsCacheEntryTest {

    /** Misma condición que aplica `LyricsFetcher` al leer la caché. */
    private fun servesWithoutRetry(entry: LyricsCacheEntry): Boolean =
        entry.hasAnyLyrics() && (entry.lrclibChecked || !entry.syncedLrc.isNullOrBlank())

    @Test
    fun `texto plano guardado sin consultar LRCLIB no bloquea el reintento`() {
        val fromOvhAfterTimeout = LyricsCacheEntry(
            plain = "letra plana",
            syncedLrc = null,
            source = "lyrics.ovh",
            lrclibChecked = false,
        )
        assertFalse(servesWithoutRetry(fromOvhAfterTimeout))
    }

    @Test
    fun `texto plano con LRCLIB ya consultado si es definitivo`() {
        // LRCLIB contestó "no la tengo": la letra plana es lo mejor disponible, no se reintenta.
        val definitive = LyricsCacheEntry(
            plain = "letra plana",
            syncedLrc = null,
            source = "lyrics.ovh",
            lrclibChecked = true,
        )
        assertTrue(servesWithoutRetry(definitive))
    }

    @Test
    fun `una entrada sincronizada siempre sirve, venga marcada o no`() {
        val synced = LyricsCacheEntry(
            plain = "letra",
            syncedLrc = "[00:12.00]letra",
            source = "lrclib",
            lrclibChecked = false,
        )
        assertTrue(servesWithoutRetry(synced))
    }

    @Test
    fun `una entrada vacia nunca sirve`() {
        assertFalse(servesWithoutRetry(LyricsCacheEntry(plain = "", lrclibChecked = true)))
    }

    @Test
    fun `por defecto una entrada se considera no consultada`() {
        // Importa para las entradas ya escritas en disco antes de este cambio: al no llevar la
        // marca se reintenta LRCLIB una vez, en vez de darlas por definitivas.
        assertFalse(LyricsCacheEntry(plain = "x").lrclibChecked)
    }
}
