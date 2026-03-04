package com.imontalvodev.savetune

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.MediaStore
import android.util.Log
import android.media.MediaPlayer
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import java.io.File

data class Song(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int = 0,
    val file: File? = null,
    val mediaStoreId: Long? = null
)

class MainActivity : AppCompatActivity() {

    private lateinit var txtPlaylistName: TextView
    private lateinit var txtPlaylistSubtitle: TextView
    private lateinit var listSongs: ListView
    private lateinit var btnPlayAll: Button

    private lateinit var imgCurrentArt: ImageView
    private lateinit var txtCurrentTitle: TextView
    private lateinit var txtCurrentArtist: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrevMain: ImageButton
    private lateinit var btnNextMain: ImageButton
    private lateinit var playerBar: LinearLayout
    private lateinit var btnMenuMain: ImageButton
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navPlayerMain: TextView
    private lateinit var navDownloadMain: TextView
    private lateinit var navPlaylists: TextView
    private lateinit var navFavorites: TextView

    private var isPlaying: Boolean = false
    private var songs: List<Song> = emptyList()
    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: Song? = null

    private val PERMISSION_REQUEST_CODE = 1001
    private val TAG = "SaveTune"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        checkPermissionsAndLoad()
        setupPlayerBar()
    }

    override fun onResume() {
        super.onResume()
        // Cada vez que volvemos al Home, reescaneamos por si hay MP3 nuevos
        checkPermissionsAndLoad()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun bindViews() {
        drawerLayout = findViewById(R.id.drawerLayoutMain)
        txtPlaylistName = findViewById(R.id.txtPlaylistName)
        txtPlaylistSubtitle = findViewById(R.id.txtPlaylistSubtitle)
        listSongs = findViewById(R.id.listSongs)
        btnPlayAll = findViewById(R.id.btnPlayAll)

        playerBar = findViewById(R.id.playerBar)
        imgCurrentArt = findViewById(R.id.imgCurrentArt)
        txtCurrentTitle = findViewById(R.id.txtCurrentTitle)
        txtCurrentArtist = findViewById(R.id.txtCurrentArtist)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevMain = findViewById(R.id.btnPrevMain)
        btnNextMain = findViewById(R.id.btnNextMain)
        btnMenuMain = findViewById(R.id.btnMenuMain)
        navPlayerMain = findViewById(R.id.navPlayerMain)
        navDownloadMain = findViewById(R.id.navDownloadMain)
        navPlaylists = findViewById(R.id.navPlaylists)
        navFavorites = findViewById(R.id.navFavorites)

        setupMenu()
    }

    private fun setupMenu() {
        btnMenuMain.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navPlayerMain.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            // Ya estamos en Player
        }

        navDownloadMain.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, DownloadActivity::class.java))
        }

        navPlaylists.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, PlaylistsActivity::class.java))
        }

        navFavorites.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, FavoritesActivity::class.java))
        }
    }

    private fun checkPermissionsAndLoad() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            // Android 12 y anteriores
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Solicitando permisos: $permissionsToRequest")
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            Log.d(TAG, "Permisos ya otorgados")
            loadSongsAndSetupList()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "Permisos otorgados")
                loadSongsAndSetupList()
            } else {
                Log.e(TAG, "Permisos denegados")
                songs = emptyList()
                txtPlaylistName.text = "No Permissions"
                txtPlaylistSubtitle.text = "Grant storage permission to see your music"
                Toast.makeText(this, "Se necesitan permisos de almacenamiento", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadSongsAndSetupList() {
        songs = loadDownloadedSongs()
        Log.d(TAG, "Canciones encontradas: ${songs.size}")

        txtPlaylistName.text = "My Music"
        txtPlaylistSubtitle.text = "${songs.size} Track${if (songs.size != 1) "s" else ""}"

        if (songs.isEmpty()) {
            Toast.makeText(this, "No se encontraron canciones en el dispositivo", Toast.LENGTH_LONG).show()
        }

        setupList()
    }
    
    private fun loadDownloadedSongs(): List<Song> {
        val result = mutableListOf<Song>()

        // Primero intentar con MediaStore
        result.addAll(loadFromMediaStore())

        // Si no encontramos nada, buscar directamente en carpetas conocidas
        if (result.isEmpty()) {
            Log.w(TAG, "MediaStore vacío, buscando directamente en sistema de archivos...")
            result.addAll(loadFromFileSystem())
        }

        // Eliminar duplicados usando primero el ID de MediaStore
        val distinct = result.distinctBy { song ->
            song.mediaStoreId
                ?: song.file?.let { f -> "${f.name.lowercase()}-${f.length()}" }
                ?: "${song.title.lowercase()}-${song.durationSeconds}"
        }

        Log.d(TAG, "Total de canciones cargadas (sin duplicados): ${distinct.size}")
        return distinct
    }

    private fun loadFromFileSystem(): List<Song> {
        val result = mutableListOf<Song>()

        // Carpetas comunes donde se guardan canciones
        val musicFolders = listOf(
            "/storage/emulated/0/Music",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Downloads",
            "/sdcard/Music",
            "/sdcard/Download"
        )

        val audioExtensions = setOf("mp3", "m4a", "webm", "ogg", "wav", "flac", "aac")

        for (folderPath in musicFolders) {
            val folder = File(folderPath)
            if (!folder.exists() || !folder.isDirectory) {
                Log.d(TAG, "Carpeta no existe: $folderPath")
                continue
            }

            Log.d(TAG, "Escaneando carpeta: $folderPath")

            folder.listFiles()?.forEach { file ->
                if (file.isFile && audioExtensions.contains(file.extension.lowercase())) {
                    try {
                        val song = Song(
                            title = file.nameWithoutExtension,
                            artist = "Unknown Artist",
                            album = "",
                            durationSeconds = 0,
                            file = file
                        )
                        result.add(song)
                        Log.d(TAG, "Archivo encontrado: ${file.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error procesando ${file.name}", e)
                    }
                }
            }
        }

        return result
    }

    private fun loadFromMediaStore(): List<Song> {
        val result = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        // Selección mejorada: solo archivos de música (incluye webm, m4a, etc.)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                Log.d(TAG, "Escaneando MediaStore... Total filas: ${it.count}")

                while (it.moveToNext()) {
                    try {
                        val id = it.getLong(idIdx)
                        val displayName = it.getString(nameIdx) ?: ""
                        var title = it.getString(titleIdx)?.takeIf { t -> t.isNotBlank() }
                            ?: displayName.substringBeforeLast('.')
                        var artist = it.getString(artistIdx)?.takeIf { a -> a.isNotBlank() && a != "<unknown>" }
                            ?: "Unknown Artist"
                        val album = it.getString(albumIdx) ?: ""
                        val durationMs = it.getLong(durationIdx)
                        val durationSec = if (durationMs > 0) (durationMs / 1000).toInt() else 0
                        val fullPath = it.getString(dataIdx) ?: ""

                        val file = try {
                            if (fullPath.isNotBlank()) File(fullPath) else null
                        } catch (e: Exception) {
                            Log.w(TAG, "Error creando File para: $fullPath", e)
                            null
                        }

                        // Fallback: si seguimos con "Unknown Artist", intentar extraer de "Título - Artista"
                        if (artist == "Unknown Artist") {
                            val source = title.ifBlank { displayName.substringBeforeLast('.') }
                            if (source.contains(" - ")) {
                                val parts = source.split(" - ")
                                if (parts.size >= 2) {
                                    val maybeArtist = parts.last().trim()
                                    val maybeTitle = parts.dropLast(1).joinToString(" - ").trim()
                                    if (maybeArtist.length in 2..40) {
                                        artist = maybeArtist
                                        if (maybeTitle.isNotBlank()) {
                                            title = maybeTitle
                                        }
                                    }
                                }
                            }
                        }

                        val song = Song(
                            title = title,
                            artist = artist,
                            album = album,
                            durationSeconds = durationSec,
                            file = file,
                            mediaStoreId = id
                        )

                        result.add(song)

                        Log.d(TAG, "Canción encontrada: $title - $artist (${formatDuration(durationSec)})")

                    } catch (e: Exception) {
                        Log.e(TAG, "Error procesando fila", e)
                    }
                }
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException al acceder a MediaStore", e)
            Toast.makeText(this, "Error de permisos al acceder a la música", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error general al cargar canciones", e)
            Toast.makeText(this, "Error al cargar canciones: ${e.message}", Toast.LENGTH_LONG).show()
        }

        return result
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "--:--"
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }

    private fun setupList() {
        if (songs.isEmpty()) {
            Log.d(TAG, "Lista de canciones vacía")
        }

        val adapter = object : ArrayAdapter<Song>(
            this,
            R.layout.item_song,
            songs
        ) {
            override fun getView(
                position: Int,
                convertView: android.view.View?,
                parent: android.view.ViewGroup
            ): android.view.View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_song, parent, false)

                val titleView = view.findViewById<TextView>(R.id.txtSongTitle)
                val artistView = view.findViewById<TextView>(R.id.txtSongArtist)
                val artView = view.findViewById<ImageView>(R.id.imgSongArt)
                val btnMenu = view.findViewById<ImageButton>(R.id.btnSongMenu)

                val song = getItem(position)
                if (song != null) {
                    titleView.text = song.title
                    artistView.text = song.artist

                    // Intentar cargar la carátula embebida en el MP3 (si existe)
                    val file = song.file
                    if (file != null && file.exists()) {
                        try {
                            val mmr = MediaMetadataRetriever()
                            mmr.setDataSource(file.absolutePath)
                            val art = mmr.embeddedPicture
                            if (art != null) {
                                val bmp = BitmapFactory.decodeByteArray(art, 0, art.size)
                                artView.setImageBitmap(bmp)
                            } else {
                                artView.setImageResource(R.mipmap.ic_launcher_round)
                            }
                            mmr.release()
                        } catch (e: Exception) {
                            Log.w(TAG, "No se pudo leer la carátula de ${file.name}", e)
                            artView.setImageResource(R.mipmap.ic_launcher_round)
                        }
                    } else {
                        artView.setImageResource(R.mipmap.ic_launcher_round)
                    }

                    btnMenu.setOnClickListener {
                        showSongMenu(song, it)
                    }
                }
                return view
            }
        }

        listSongs.adapter = adapter

        listSongs.setOnItemClickListener { _, _, position, _ ->
            val song = songs[position]
            NowPlayingState.songs = songs
            NowPlayingState.playSong(this, position)
            updateCurrentTrack(song)
            Log.d(TAG, "Canción seleccionada: ${song.title}")
        }

        btnPlayAll.setOnClickListener {
            if (songs.isNotEmpty()) {
                NowPlayingState.songs = songs
                NowPlayingState.playSong(this, 0)
                updateCurrentTrack(songs.first())
                Log.d(TAG, "Play All presionado")
            } else {
                Toast.makeText(this, "No hay canciones para reproducir", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPlayerBar() {
        playerBar.setOnClickListener {
            if (NowPlayingState.currentSong != null) {
                startActivity(Intent(this, NowPlayingActivity::class.java))
            } else {
                Toast.makeText(this, "No hay ninguna canción reproduciéndose", Toast.LENGTH_SHORT).show()
            }
        }

        btnPlayPause.setOnClickListener {
            NowPlayingState.togglePlayPause(this)
            val mp = NowPlayingState.mediaPlayer
            val isPlayingNow = mp?.isPlaying == true
            isPlaying = isPlayingNow
            btnPlayPause.setImageResource(
                if (isPlayingNow) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }

        btnPrevMain.setOnClickListener {
            if (NowPlayingState.playPrevious(this)) {
                NowPlayingState.currentSong?.let { updateCurrentTrack(it) }
            } else {
                Toast.makeText(this, "No hay pista anterior", Toast.LENGTH_SHORT).show()
            }
        }

        btnNextMain.setOnClickListener {
            if (NowPlayingState.playNext(this)) {
                NowPlayingState.currentSong?.let { updateCurrentTrack(it) }
            } else {
                Toast.makeText(this, "No hay pista siguiente", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCurrentTrack(song: Song) {
        txtCurrentTitle.text = song.title
        txtCurrentArtist.text = song.artist
        currentSong = song
    }

    private fun showSongMenu(song: Song, anchor: android.view.View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_song_actions, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_favorite -> {
                    val isFav = LibraryStore.toggleFavorite(this, song)
                    Toast.makeText(
                        this,
                        if (isFav) "Añadido a favoritos" else "Eliminado de favoritos",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
                R.id.action_add_to_playlist -> {
                    showAddToPlaylistDialog(song)
                    true
                }
                R.id.action_add_to_queue -> {
                    addToQueue(song)
                    true
                }
                R.id.action_delete -> {
                    deleteSong(song)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun showAddToPlaylistDialog(song: Song) {
        val existing = LibraryStore.getPlaylists(this)

        // Si no hay playlists, vamos directamente al flujo de crear nueva
        if (existing.isEmpty()) {
            showCreatePlaylistDialog(song)
            return
        }

        val items = existing + "➕ Nueva playlist..."

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Añadir a playlist")
            .setItems(items.toTypedArray()) { dialog, which ->
                if (which == existing.size) {
                    // Opción "Nueva playlist..."
                    dialog.dismiss()
                    showCreatePlaylistDialog(song)
                } else {
                    val name = existing[which]
                    LibraryStore.addSongToPlaylist(this, name, song)
                    Toast.makeText(this, "Añadido a '$name'", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showCreatePlaylistDialog(song: Song) {
        val editText = EditText(this)
        editText.hint = "Nombre de la playlist"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Nueva playlist")
            .setView(editText)
            .setPositiveButton("Crear") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    LibraryStore.addSongToPlaylist(this, name, song)
                    Toast.makeText(this, "Añadido a '$name'", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addToQueue(song: Song) {
        if (NowPlayingState.songs.isEmpty()) {
            NowPlayingState.songs = listOf(song)
            NowPlayingState.playSong(this, 0)
            updateCurrentTrack(song)
        } else {
            val list = NowPlayingState.songs.toMutableList()
            list.add(song)
            NowPlayingState.songs = list
            Toast.makeText(this, "Añadido a la cola", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSong(song: Song) {
        // Borrar de MediaStore si tenemos ID
        try {
            song.mediaStoreId?.let { id ->
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                contentResolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error borrando de MediaStore", e)
        }

        // Borrar archivo físico si existe
        try {
            song.file?.let { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error borrando archivo físico", e)
        }

        Toast.makeText(this, "Canción eliminada", Toast.LENGTH_SHORT).show()
        loadSongsAndSetupList()
    }
}