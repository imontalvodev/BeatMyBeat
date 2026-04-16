package com.imontalvodev.beatmybeat.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ThemeProfilesStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("beatmybeat_theme_profiles", Context.MODE_PRIVATE)

    fun loadProfiles(): List<BeatMyBeatThemeProfile> {
        val raw = prefs.getString(KEY_PROFILES, null)
        if (raw.isNullOrBlank()) return defaultProfiles()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return defaultProfiles()
        val out = mutableListOf<BeatMyBeatThemeProfile>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += BeatMyBeatThemeProfile(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Custom"),
                backgroundTop = Color(o.optLong("backgroundTop", NeonMintProfile.backgroundTop.value.toLong())),
                backgroundBottom = Color(o.optLong("backgroundBottom", NeonMintProfile.backgroundBottom.value.toLong())),
                primary = Color(o.optLong("primary", NeonMintProfile.primary.value.toLong())),
                primaryVariant = Color(o.optLong("primaryVariant", NeonMintProfile.primaryVariant.value.toLong())),
                secondary = Color(o.optLong("secondary", NeonMintProfile.secondary.value.toLong())),
                surface = Color(o.optLong("surface", NeonMintProfile.surface.value.toLong())),
                onSurface = Color(o.optLong("onSurface", NeonMintProfile.onSurface.value.toLong())),
                onSurfaceMuted = Color(o.optLong("onSurfaceMuted", NeonMintProfile.onSurfaceMuted.value.toLong())),
            )
        }
        return if (out.isEmpty()) defaultProfiles() else out
    }

    fun saveProfiles(profiles: List<BeatMyBeatThemeProfile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("backgroundTop", p.backgroundTop.value.toLong())
                    put("backgroundBottom", p.backgroundBottom.value.toLong())
                    put("primary", p.primary.value.toLong())
                    put("primaryVariant", p.primaryVariant.value.toLong())
                    put("secondary", p.secondary.value.toLong())
                    put("surface", p.surface.value.toLong())
                    put("onSurface", p.onSurface.value.toLong())
                    put("onSurfaceMuted", p.onSurfaceMuted.value.toLong())
                },
            )
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun loadActiveProfileId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    fun saveActiveProfileId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun defaultProfiles(): List<BeatMyBeatThemeProfile> = listOf(NeonMintProfile, CherryPulseProfile)

    companion object {
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ACTIVE_ID = "active_profile_id"
    }
}

