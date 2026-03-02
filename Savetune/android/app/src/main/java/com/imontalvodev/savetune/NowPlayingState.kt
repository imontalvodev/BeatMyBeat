package com.imontalvodev.savetune

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast

object NowPlayingState {
    var songs: List<Song> = emptyList()
    var currentIndex: Int = -1
    var mediaPlayer: MediaPlayer? = null
    var currentSong: Song? = null

    private const val TAG = "NowPlayingState"

    fun playSong(context: Context, index: Int) {
        if (index < 0 || index >= songs.size) return
        val song = songs[index]
        currentIndex = index
        currentSong = song

        mediaPlayer?.release()
        mediaPlayer = null

        val mp = MediaPlayer()
        mediaPlayer = mp
        try {
            val file = song.file
            val hasValidFile = file != null && file.exists()
            if (hasValidFile) {
                mp.setDataSource(file!!.absolutePath)
            } else if (song.mediaStoreId != null) {
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    song.mediaStoreId
                )
                mp.setDataSource(context, uri)
            } else {
                Toast.makeText(context, "Archivo de audio no encontrado", Toast.LENGTH_SHORT).show()
                return
            }

            mp.setOnPreparedListener {
                it.start()
                // Asegurar que el servicio en foreground está activo para reproducción en segundo plano
                val startIntent = Intent(context, MusicService::class.java).apply {
                    action = MusicService.ACTION_START
                }
                androidx.core.content.ContextCompat.startForegroundService(context, startIntent)
            }
            mp.setOnCompletionListener {
                // Avanzar automáticamente a la siguiente pista si existe
                if (!playNext(context)) {
                    // Si no hay siguiente, simplemente paramos
                    Log.d(TAG, "Fin de la cola de reproducción")
                }
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir ${song.title}", e)
            Toast.makeText(context, "No se pudo reproducir la canción", Toast.LENGTH_SHORT).show()
            mp.release()
            mediaPlayer = null
        }
    }

    fun togglePlayPause(context: Context) {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
        } else {
            mp.start()
        }
    }

    fun seekTo(positionMs: Int) {
        val mp = mediaPlayer ?: return
        mp.seekTo(positionMs.coerceAtLeast(0))
    }

    fun playNext(context: Context): Boolean {
        if (songs.isEmpty()) return false
        val nextIndex = currentIndex + 1
        return if (nextIndex < songs.size) {
            playSong(context, nextIndex)
            true
        } else {
            false
        }
    }

    fun playPrevious(context: Context): Boolean {
        if (songs.isEmpty()) return false
        val prevIndex = currentIndex - 1
        return if (prevIndex >= 0) {
            playSong(context, prevIndex)
            true
        } else {
            false
        }
    }
}

