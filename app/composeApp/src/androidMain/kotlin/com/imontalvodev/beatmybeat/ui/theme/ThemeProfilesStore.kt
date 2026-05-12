package com.imontalvodev.beatmybeat.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private fun linearSrgb(channel: Float): Double {
    val v = channel.toDouble().coerceIn(0.0, 1.0)
    return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

private fun relativeLuminance(c: Color): Double {
    val r = linearSrgb(c.red)
    val g = linearSrgb(c.green)
    val b = linearSrgb(c.blue)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private fun contrastRatio(c1: Color, c2: Color): Double {
    val l1 = relativeLuminance(c1) + 0.05
    val l2 = relativeLuminance(c2) + 0.05
    return max(l1, l2) / min(l1, l2)
}

/** Perfiles guardados corruptos o con poco contraste rompen toda la UI (texto ≈ fondo). */
private fun BeatMyBeatThemeProfile.hasUsableContrast(): Boolean {
    if (surface.alpha < 0.35f || onSurface.alpha < 0.35f) return false
    if (backgroundBottom.alpha < 0.35f) return false
    if (primary.alpha < 0.35f) return false
    if (contrastRatio(surface, onSurface) < 2.0) return false
    if (contrastRatio(backgroundBottom, onSurface) < 2.0) return false
    if (contrastRatio(primary, Color.Black) < 2.0) return false
    return true
}

class ThemeProfilesStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("beatmybeat_theme_profiles", Context.MODE_PRIVATE)

    fun loadProfiles(): List<BeatMyBeatThemeProfile> {
        val raw = prefs.getString(KEY_PROFILES, null)
        if (raw.isNullOrBlank()) return defaultProfiles()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return defaultProfiles()
        val out = mutableListOf<BeatMyBeatThemeProfile>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var profile = BeatMyBeatThemeProfile(
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
            if (profile.id != NeonMintProfile.id && !profile.hasUsableContrast()) {
                profile = NeonMintProfile.copy(id = profile.id, name = profile.name)
            }
            out += profile
        }
        if (out.isEmpty()) return defaultProfiles()
        val sanitized = out.mapNotNull { profile ->
            // Si es un perfil built-in, priorizamos la versión actual del código
            // para reflejar cambios globales de paleta al instante.
            when (profile.id) {
                NeonMintProfile.id -> NeonMintProfile
                "builtin-cherry" -> null
                else -> profile
            }
        }
        return if (sanitized.isEmpty()) defaultProfiles() else sanitized
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

    fun defaultProfiles(): List<BeatMyBeatThemeProfile> = listOf(NeonMintProfile)

    companion object {
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ACTIVE_ID = "active_profile_id"
    }
}

