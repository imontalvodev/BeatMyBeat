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
        // Estado inicial de ejemplo similar al mockup
        txtPlaylistTitle.text = "Late Night Lo-Fi Playlist"
        txtPlaylistSubtitle.text = "42 Tracks Found"

        songs = listOf(
            DownloadSong("City Lights Chill", "Beatmaker X"),
            DownloadSong("Demo Light Chill", "Beatmaker X"),
            DownloadSong("Mornow Chill", "Beatmaker X"),
            DownloadSong("Interstellar", "Hans Zimmer"),
            DownloadSong("Never Gonna Give You Up", "Rick Astley")
        )

        val adapter = object : ArrayAdapter<DownloadSong>(
            this,
            android.R.layout.simple_list_item_2,
            android.R.id.text1,
            songs
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = super.getView(position, convertView, parent)
                val titleView = view.findViewById<TextView>(android.R.id.text1)
                val subtitleView = view.findViewById<TextView>(android.R.id.text2)

                val song = getItem(position)
                if (song != null) {
                    titleView.text = song.title
                    subtitleView.text = song.artist
                }
                return view
            }
        }

        listDownloadSongs.adapter = adapter

        btnDownloadAll.setOnClickListener {
            Toast.makeText(this, "Descarga de todos los temas (demo)", Toast.LENGTH_SHORT).show()
        }

        listDownloadSongs.setOnItemClickListener { _, _, position, _ ->
            val song = songs[position]
            Toast.makeText(this, "Descargar '${song.title}' (demo)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAnalyze() {
        btnAnalyze.setOnClickListener {
            val text = edtSource.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Pega una URL de Spotify o escribe un título", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Próximo paso: aquí haremos la llamada real al backend.
            Toast.makeText(this, "Analizando fuente (demo)", Toast.LENGTH_SHORT).show()
        }
    }
}

