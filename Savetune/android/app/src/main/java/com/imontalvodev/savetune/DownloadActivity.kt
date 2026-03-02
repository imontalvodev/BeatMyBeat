package com.imontalvodev.savetune

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

data class DownloadSong(
    val id: String? = null,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null
)

class DownloadActivity : AppCompatActivity() {

    private lateinit var edtSource: EditText
    private lateinit var btnAnalyze: Button
    private lateinit var txtPlaylistTitle: TextView
    private lateinit var txtPlaylistSubtitle: TextView
    private lateinit var btnDownloadAll: Button
    private lateinit var listDownloadSongs: ListView
    private lateinit var btnMenu: ImageButton
    private lateinit var progressLoading: ProgressBar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navPlayerDownload: TextView
    private lateinit var navDownloadDownload: TextView

    private var songs: List<DownloadSong> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)

        bindViews()
        setupMenu()
        setupDummyState()
        setupAnalyze()
    }

    private fun bindViews() {
        drawerLayout = findViewById(R.id.drawerLayoutDownload)
        edtSource = findViewById(R.id.edtSource)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        txtPlaylistTitle = findViewById(R.id.txtPlaylistTitle)
        txtPlaylistSubtitle = findViewById(R.id.txtPlaylistSubtitle)
        btnDownloadAll = findViewById(R.id.btnDownloadAll)
        listDownloadSongs = findViewById(R.id.listDownloadSongs)
        btnMenu = findViewById(R.id.btnMenuDownload)
        navPlayerDownload = findViewById(R.id.navPlayerDownload)
        navDownloadDownload = findViewById(R.id.navDownloadDownload)
        progressLoading = findViewById(R.id.progressLoading)
    }

    private fun setupMenu() {
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navPlayerDownload.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        navDownloadDownload.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            // Ya estamos en Download
        }
    }

    private fun setupDummyState() {
        // Estado inicial vacío; se rellenará al analizar una playlist real
        txtPlaylistTitle.text = getString(R.string.app_name)
        txtPlaylistSubtitle.text = ""
    }

    private fun setupAnalyze() {
        btnAnalyze.setOnClickListener {
            val text = edtSource.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Pega una URL de Spotify o escribe un título", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoading(true)

            Thread {
                try {
                    val playlist = ApiClient.fetchPlaylist(text)

                    runOnUiThread {
                        setLoading(false)
                        txtPlaylistTitle.text = playlist.name
                        txtPlaylistSubtitle.text = "${playlist.totalTracks} Tracks Found"
                        songs = playlist.songs
                        updateDownloadList()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        setLoading(false)
                        Toast.makeText(this, "Error al contactar con el backend: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    private fun updateDownloadList() {
        val adapter = object : ArrayAdapter<DownloadSong>(
            this,
            R.layout.item_song,
            songs
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_song, parent, false)

                val titleView = view.findViewById<TextView>(R.id.txtSongTitle)
                val artistView = view.findViewById<TextView>(R.id.txtSongArtist)
                val artView = view.findViewById<ImageView>(R.id.imgSongArt)
                val btnDownload = view.findViewById<ImageButton>(R.id.btnDownloadSong)

                val song = getItem(position)
                if (song != null) {
                    titleView.text = song.title
                    artistView.text = song.artist
                    artView.setImageResource(R.mipmap.ic_launcher_round)

                    btnDownload.setOnClickListener {
                        Toast.makeText(
                            this@DownloadActivity,
                            "Descargar '${song.title}' (pendiente de implementación real)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                return view
            }
        }

        listDownloadSongs.adapter = adapter

        btnDownloadAll.setOnClickListener {
            Toast.makeText(this, "Descargar todos los temas (pendiente de implementación real)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnAnalyze.isEnabled = !isLoading
        btnAnalyze.text = if (isLoading) "LOADING..." else "ANALYZE SOURCE"
    }
}

