package com.imontalvodev.savetune.ui.feature.player

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imontalvodev.savetune.ui.data.DeviceTrack
import com.imontalvodev.savetune.ui.data.MediaStoreScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = MediaStoreScanner(app)

    private val prefs = app.getSharedPreferences("savetune_player_prefs", Context.MODE_PRIVATE)

    private val _tracks = MutableStateFlow<List<DeviceTrack>>(emptyList())
    val tracks: StateFlow<List<DeviceTrack>> = _tracks.asStateFlow()

    // ids de favoritos persistidos
    private val _favoriteIds = MutableStateFlow<Set<Long>>(loadIdSet(PREF_FAVORITES))
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    // ids de playlist principal persistida
    private val _playlistIds = MutableStateFlow<Set<Long>>(loadIdSet(PREF_PLAYLIST))
    val playlistIds: StateFlow<Set<Long>> = _playlistIds.asStateFlow()

    fun syncLibrary(auto: Boolean) {
        viewModelScope.launch {
            val scanned = scanner.scanAudio()
            _tracks.value = scanned
        }
    }

    fun isFavorite(track: DeviceTrack): Boolean =
        _favoriteIds.value.contains(track.id)

    fun toggleFavorite(track: DeviceTrack) {
        val current = _favoriteIds.value.toMutableSet()
        if (current.contains(track.id)) {
            current.remove(track.id)
        } else {
            current.add(track.id)
        }
        _favoriteIds.value = current.toSet()
        saveIdSet(PREF_FAVORITES, _favoriteIds.value)
    }

    /**
     * Añade una pista a la playlist principal.
     * @return true si se añadió, false si ya existía y no se añadió.
     */
    fun addToPlaylist(track: DeviceTrack, allowDuplicate: Boolean = false): Boolean {
        val current = _playlistIds.value.toMutableSet()
        val alreadyIn = current.contains(track.id)
        if (alreadyIn && !allowDuplicate) {
            return false
        }
        if (allowDuplicate) {
            // Para este caso sencillo seguimos guardando sólo ids únicos.
            if (!alreadyIn) {
                current.add(track.id)
            }
        } else {
            current.add(track.id)
        }
        _playlistIds.value = current.toSet()
        saveIdSet(PREF_PLAYLIST, _playlistIds.value)
        return true
    }

    private fun loadIdSet(key: String): Set<Long> {
        val raw = prefs.getString(key, "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split(",")
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    private fun saveIdSet(key: String, values: Set<Long>) {
        val asString = values.joinToString(",")
        prefs.edit().putString(key, asString).apply()
    }

    companion object {
        private const val PREF_FAVORITES = "favorites_ids"
        private const val PREF_PLAYLIST = "playlist_ids"
    }
}

