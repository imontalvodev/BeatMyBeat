package com.imontalvodev.savetune.ui.feature.player

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.imontalvodev.savetune.ui.data.DeviceTrack
import com.imontalvodev.savetune.ui.data.MediaStoreScanner
import org.json.JSONArray
import org.json.JSONObject

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = MediaStoreScanner(app)

    private val prefs = app.getSharedPreferences("savetune_player_prefs", Context.MODE_PRIVATE)

    private val _tracks = MutableStateFlow<List<DeviceTrack>>(emptyList())
    val tracks: StateFlow<List<DeviceTrack>> = _tracks.asStateFlow()

    // ids de favoritos persistidos
    private val _favoriteIds = MutableStateFlow<Set<Long>>(loadIdSet(PREF_FAVORITES))
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    data class PlaylistEntity(
        val id: Long,
        val name: String,
        val songIds: List<Long>, // admite duplicados (si el usuario los permite)
    )

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(loadPlaylists())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists.asStateFlow()

    sealed interface AddToPlaylistResult {
        data object Added : AddToPlaylistResult
        data class AlreadyExists(val occurrences: Int) : AddToPlaylistResult
    }

    sealed interface CreatePlaylistResult {
        data class Created(val id: Long) : CreatePlaylistResult
        data class AlreadyExists(val id: Long) : CreatePlaylistResult
    }

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
     * Crea una playlist con nombre proporcionado.
     * Si ya existe una playlist con ese mismo nombre (case-insensitive), no crea otra.
     */
    fun createPlaylist(name: String): CreatePlaylistResult {
        val safeName = name.trim().ifBlank { "Playlist" }
        val normalized = safeName.lowercase()

        val existing = _playlists.value.firstOrNull { it.name.lowercase() == normalized }
        if (existing != null) {
            return CreatePlaylistResult.AlreadyExists(existing.id)
        }

        val id = System.currentTimeMillis()
        val updated = _playlists.value.toMutableList().apply {
            add(PlaylistEntity(id = id, name = safeName, songIds = emptyList()))
        }
        _playlists.value = updated
        savePlaylists(updated)
        return CreatePlaylistResult.Created(id)
    }

    /**
     * Añade una pista a una playlist.
     * - Si `allowDuplicate=false` y la canción ya existe, no se añade y se devuelve `AlreadyExists`.
     * - Si `allowDuplicate=true`, se permite duplicado (se añade siempre).
     */
    fun addToPlaylist(
        track: DeviceTrack,
        playlistId: Long,
        allowDuplicate: Boolean,
    ): AddToPlaylistResult {
        val current = _playlists.value
        val idx = current.indexOfFirst { it.id == playlistId }
        if (idx < 0) return AddToPlaylistResult.Added // playlist no encontrada: comportamiento seguro

        val playlist = current[idx]
        val occurrences = playlist.songIds.count { it == track.id }

        if (occurrences > 0 && !allowDuplicate) {
            return AddToPlaylistResult.AlreadyExists(occurrences = occurrences)
        }

        val newSongIds = playlist.songIds + track.id
        val updatedPlaylist = playlist.copy(songIds = newSongIds)
        val updated = current.toMutableList().apply {
            this[idx] = updatedPlaylist
        }

        _playlists.value = updated
        savePlaylists(updated)
        return AddToPlaylistResult.Added
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

    private fun loadPlaylists(): List<PlaylistEntity> {
        val raw = prefs.getString(PREF_PLAYLISTS_JSON, null) ?: return migrateLegacyIfNeeded()
        if (raw.isBlank()) return migrateLegacyIfNeeded()

        return runCatching {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("playlists") ?: JSONArray()
            val list = mutableListOf<PlaylistEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optLong("id", 0L)
                val name = obj.optString("name", "Playlist")
                val songIdsArr = obj.optJSONArray("songIds") ?: JSONArray()
                val songIds = mutableListOf<Long>()
                for (j in 0 until songIdsArr.length()) {
                    val v = songIdsArr.optLong(j, -1L)
                    if (v >= 0) songIds.add(v)
                }
                if (id > 0) list.add(PlaylistEntity(id = id, name = name, songIds = songIds))
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun migrateLegacyIfNeeded(): List<PlaylistEntity> {
        // Compatibilidad: antiguamente existía `playlist_ids` como set (sin duplicados).
        // Si no hay JSON nuevo, convertimos a una playlist única.
        val legacy = loadIdSet(PREF_PLAYLIST)
        if (legacy.isEmpty()) return emptyList()

        val id = System.currentTimeMillis()
        return listOf(PlaylistEntity(id = id, name = "Mi Playlist", songIds = legacy.toList()))
            .also { savePlaylists(it) }
    }

    private fun savePlaylists(playlists: List<PlaylistEntity>) {
        val root = JSONObject()
        val arr = JSONArray()
        playlists.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            val songIdsArr = JSONArray()
            p.songIds.forEach { songIdsArr.put(it) }
            obj.put("songIds", songIdsArr)
            arr.put(obj)
        }
        root.put("playlists", arr)
        prefs.edit().putString(PREF_PLAYLISTS_JSON, root.toString()).apply()
    }

    companion object {
        private const val PREF_FAVORITES = "favorites_ids"
        // Legacy (migración)
        private const val PREF_PLAYLIST = "playlist_ids"
        private const val PREF_PLAYLISTS_JSON = "playlists_json"
    }
}

