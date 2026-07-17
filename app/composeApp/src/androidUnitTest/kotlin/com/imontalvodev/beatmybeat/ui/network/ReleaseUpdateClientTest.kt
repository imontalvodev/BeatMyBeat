package com.imontalvodev.beatmybeat.ui.network

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleaseUpdateClientTest {

    private fun asset(name: String, url: String): JSONObject =
        JSONObject().put("name", name).put("browser_download_url", url)

    private fun assets(vararg items: JSONObject): JSONArray {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return arr
    }

    @Test
    fun findApkDownloadUrl_nullOrEmptyAssets_returnsNull() {
        assertNull(ReleaseUpdateClient.findApkDownloadUrl(null))
        assertNull(ReleaseUpdateClient.findApkDownloadUrl(JSONArray()))
    }

    @Test
    fun findApkDownloadUrl_exactExpectedName_returnsItsUrl() {
        val url = ReleaseUpdateClient.findApkDownloadUrl(
            assets(asset("BeatMyBeat.apk", "https://example.com/BeatMyBeat.apk")),
        )
        assertEquals("https://example.com/BeatMyBeat.apk", url)
    }

    @Test
    fun findApkDownloadUrl_caseInsensitiveName_stillMatches() {
        val url = ReleaseUpdateClient.findApkDownloadUrl(
            assets(asset("beatmybeat.APK", "https://example.com/x.apk")),
        )
        assertEquals("https://example.com/x.apk", url)
    }

    @Test
    fun findApkDownloadUrl_unexpectedApkAsset_isIgnoredNoFallback() {
        // Regresión: antes caía al primer .apk encontrado aunque no fuera el esperado.
        val url = ReleaseUpdateClient.findApkDownloadUrl(
            assets(asset("malicious-payload.apk", "https://evil.example.com/payload.apk")),
        )
        assertNull(url)
    }

    @Test
    fun findApkDownloadUrl_expectedNameAmongOtherAssets_findsCorrectOne() {
        val url = ReleaseUpdateClient.findApkDownloadUrl(
            assets(
                asset("other-tool.apk", "https://example.com/other-tool.apk"),
                asset("BeatMyBeat.apk", "https://example.com/BeatMyBeat.apk"),
            ),
        )
        assertEquals("https://example.com/BeatMyBeat.apk", url)
    }

    @Test
    fun findApkDownloadUrl_blankDownloadUrl_isSkipped() {
        val url = ReleaseUpdateClient.findApkDownloadUrl(
            assets(asset("BeatMyBeat.apk", "")),
        )
        assertNull(url)
    }
}
