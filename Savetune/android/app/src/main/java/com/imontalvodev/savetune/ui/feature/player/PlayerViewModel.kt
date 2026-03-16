package com.imontalvodev.savetune.ui.feature.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imontalvodev.savetune.ui.data.DeviceTrack
import com.imontalvodev.savetune.ui.data.MediaStoreScanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = MediaStoreScanner(app)

    private val _tracks = MutableStateFlow<List<DeviceTrack>>(emptyList())
    val tracks: StateFlow<List<DeviceTrack>> =
        _tracks.asStateFlow()

    fun syncLibrary(auto: Boolean) {
        viewModelScope.launch {
            val scanned = scanner.scanAudio()
            _tracks.value = scanned
        }
    }

    // toggleFavorite y playlists se implementarán más adelante con persistencia
}

