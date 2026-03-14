package com.imontalvodev.savetune.ui.nowplaying

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.imontalvodev.savetune.R
import com.imontalvodev.savetune.model.Song
import com.imontalvodev.savetune.player.NowPlayingState

class NowPlayingActivity : AppCompatActivity() {

    private lateinit var imgArtLarge: ImageView
    private lateinit var txtTitleLarge: TextView
    private lateinit var txtArtistLarge: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var txtElapsed: TextView
    private lateinit var txtRemaining: TextView
    private lateinit var btnPlayPauseLarge: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)

        imgArtLarge = findViewById(R.id.imgArtLarge)
        txtTitleLarge = findViewById(R.id.txtTitleLarge)
        txtArtistLarge = findViewById(R.id.txtArtistLarge)
        seekBar = findViewById(R.id.seekBar)
        txtElapsed = findViewById(R.id.txtElapsed)
        txtRemaining = findViewById(R.id.txtRemaining)
        btnPlayPauseLarge = findViewById(R.id.btnPlayPauseLarge)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            finish()
        }

        val song = NowPlayingState.currentSong
        if (song == null) {
            Toast.makeText(this, "No hay ninguna canción reproduciéndose", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        applySongInfo(song)

        setupControls()
        startProgressUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun setupControls() {
        btnPlayPauseLarge.setOnClickListener {
            NowPlayingState.togglePlayPause(this)
            updatePlayPauseIcon()
        }

        btnPrev.setOnClickListener {
            if (!NowPlayingState.playPrevious(this)) {
                Toast.makeText(this, "No hay pista anterior", Toast.LENGTH_SHORT).show()
            } else {
                refreshSongInfo()
            }
        }

        btnNext.setOnClickListener {
            if (!NowPlayingState.playNext(this)) {
                Toast.makeText(this, "No hay pista siguiente", Toast.LENGTH_SHORT).show()
            } else {
                refreshSongInfo()
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    NowPlayingState.seekTo(progress)
                    updateTimeLabels()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        updatePlayPauseIcon()
    }

    private fun startProgressUpdates() {
        handler.post(object : Runnable {
            override fun run() {
                val mp = NowPlayingState.mediaPlayer
                if (mp != null) {
                    val duration = mp.duration
                    val position = mp.currentPosition
                    if (duration > 0) {
                        seekBar.max = duration
                        seekBar.progress = position
                        updateTimeLabels()
                    }
                }
                handler.postDelayed(this, 500L)
            }
        })
    }

    private fun updateTimeLabels() {
        val mp = NowPlayingState.mediaPlayer ?: return
        val position = mp.currentPosition
        val duration = mp.duration
        txtElapsed.text = formatTime(position / 1000)
        val remaining = ((duration - position) / 1000).coerceAtLeast(0)
        txtRemaining.text = "-${formatTime(remaining)}"
    }

    private fun formatTime(totalSeconds: Int): String {
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format("%d:%02d", mins, secs)
    }

    private fun updatePlayPauseIcon() {
        val mp = NowPlayingState.mediaPlayer
        val isPlaying = mp?.isPlaying == true
        btnPlayPauseLarge.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun refreshSongInfo() {
        val song = NowPlayingState.currentSong ?: return
        applySongInfo(song)
    }

    private fun applySongInfo(song: Song) {
        txtTitleLarge.text = song.title
        txtArtistLarge.text = song.artist

        // Intentar mostrar siempre la carátula de la canción
        val file = song.file
        if (file != null && file.exists()) {
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(file.absolutePath)
                val art = mmr.embeddedPicture
                if (art != null) {
                    val bmp = BitmapFactory.decodeByteArray(art, 0, art.size)
                    imgArtLarge.setImageBitmap(bmp)
                } else {
                    imgArtLarge.setImageResource(R.mipmap.ic_launcher_round)
                }
                mmr.release()
            } catch (e: Exception) {
                imgArtLarge.setImageResource(R.mipmap.ic_launcher_round)
            }
        } else {
            imgArtLarge.setImageResource(R.mipmap.ic_launcher_round)
        }
    }
}