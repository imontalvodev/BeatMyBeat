package com.imontalvodev.beatmybeat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.core.content.ContextCompat
import com.imontalvodev.beatmybeat.ui.feature.analyze.AnalyzeScreen
import com.imontalvodev.beatmybeat.ui.feature.playlist.PlaylistScreen
import com.imontalvodev.beatmybeat.ui.feature.player.PlayerScreen
import com.imontalvodev.beatmybeat.ui.feature.profile.ProfileScreen
import com.imontalvodev.beatmybeat.ui.feature.splash.SplashScreen
import com.imontalvodev.beatmybeat.ui.feature.theme.ThemeCustomizerScreen
import com.imontalvodev.beatmybeat.ui.feature.theme.ThemeCustomizerSection
import com.imontalvodev.beatmybeat.ui.storage.StorageSettings
import com.imontalvodev.beatmybeat.ui.theme.BeatMyBeatTheme
import com.imontalvodev.beatmybeat.ui.theme.ThemeProfilesStore

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // No hacemos nada: si deniega, simplemente no se verán notificaciones.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+ requiere permiso runtime para notificaciones.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(permission)
        }

        setContent {
            val store = remember { ThemeProfilesStore(this) }
            var storageLabel by remember { mutableStateOf(StorageSettings.getLocationLabel(this)) }
            var profiles by remember { mutableStateOf(store.loadProfiles()) }
            var activeProfileId by remember {
                mutableStateOf(store.loadActiveProfileId() ?: profiles.first().id)
            }
            val activeProfile = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()

            val storagePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
            ) { uri: Uri? ->
                if (uri != null) {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, flags)
                    }
                    StorageSettings.setCustomTreeUri(this, uri)
                    storageLabel = StorageSettings.getLocationLabel(this)
                }
            }

            BeatMyBeatTheme(themeProfile = activeProfile) {
                val navController = rememberNavController()
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            SplashScreen(
                                onGoToDownloader = {
                                    navController.navigate("analyze") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                },
                                onGoToPlayer = {
                                    navController.navigate("player") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable("analyze") {
                            AnalyzeScreen(
                                themeName = activeProfile.name,
                                onOpenPlayer = { navController.navigate("player") },
                            )
                        }
                        composable(
                            route = "playlist?url={url}",
                            arguments = listOf(
                                navArgument("url") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            ),
                        ) { backStackEntry ->
                            val url = backStackEntry.arguments?.getString("url").orEmpty()
                            PlaylistScreen(
                                onOpenPlayer = { navController.navigate("player") },
                                playlistUrl = url,
                            )
                        }
                        composable("player") {
                            PlayerScreen(
                                onOpenProfile = { navController.navigate("profile") },
                                onOpenDownloader = { navController.navigate("analyze") },
                            )
                        }
                        composable("profile") {
                            ProfileScreen(
                                storageLocationLabel = storageLabel,
                                onPickStorageLocation = { storagePicker.launch(null) },
                                onOpenStorageFolder = {
                                    val customTree = StorageSettings.getCustomTreeUri(this@MainActivity)
                                    val targetTreeUri = customTree
                                        ?: Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMusic%2FBeatMyBeat")
                                    val targetDocUri = runCatching {
                                        val treeId = DocumentsContract.getTreeDocumentId(targetTreeUri)
                                        DocumentsContract.buildDocumentUriUsingTree(targetTreeUri, treeId)
                                    }.getOrDefault(targetTreeUri)

                                    val opened = runCatching {
                                        startActivity(
                                            Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(targetDocUri, DocumentsContract.Document.MIME_TYPE_DIR)
                                                addFlags(
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                                                )
                                            },
                                        )
                                        true
                                    }.recoverCatching {
                                        startActivity(
                                            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                                putExtra(DocumentsContract.EXTRA_INITIAL_URI, targetTreeUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            },
                                        )
                                        true
                                    }.getOrDefault(false)
                                    if (!opened) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "No se pudo abrir la carpeta en este dispositivo.",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                onCustomizeBackground = { navController.navigate("theme-customizer/background") },
                                onCustomizeText = { navController.navigate("theme-customizer/text") },
                            )
                        }
                        composable("theme-customizer/background") {
                            ThemeCustomizerScreen(
                                section = ThemeCustomizerSection.Background,
                                profiles = profiles,
                                activeProfileId = activeProfileId,
                                onApplyProfile = { id ->
                                    activeProfileId = id
                                    store.saveActiveProfileId(id)
                                },
                                onDeleteProfile = { id ->
                                    profiles = profiles.filterNot { it.id == id }.ifEmpty { store.defaultProfiles() }
                                    if (activeProfileId == id) {
                                        activeProfileId = profiles.first().id
                                        store.saveActiveProfileId(activeProfileId)
                                    }
                                    store.saveProfiles(profiles)
                                },
                                onSaveProfile = { profile ->
                                    profiles = if (profiles.any { it.id == profile.id }) {
                                        profiles.map { existing -> if (existing.id == profile.id) profile else existing }
                                    } else {
                                        profiles + profile
                                    }
                                    store.saveProfiles(profiles)
                                    activeProfileId = profile.id
                                    store.saveActiveProfileId(profile.id)
                                },
                            )
                        }
                        composable("theme-customizer/text") {
                            ThemeCustomizerScreen(
                                section = ThemeCustomizerSection.Text,
                                profiles = profiles,
                                activeProfileId = activeProfileId,
                                onApplyProfile = { id ->
                                    activeProfileId = id
                                    store.saveActiveProfileId(id)
                                },
                                onDeleteProfile = { id ->
                                    profiles = profiles.filterNot { it.id == id }.ifEmpty { store.defaultProfiles() }
                                    if (activeProfileId == id) {
                                        activeProfileId = profiles.first().id
                                        store.saveActiveProfileId(activeProfileId)
                                    }
                                    store.saveProfiles(profiles)
                                },
                                onSaveProfile = { profile ->
                                    profiles = if (profiles.any { it.id == profile.id }) {
                                        profiles.map { existing -> if (existing.id == profile.id) profile else existing }
                                    } else {
                                        profiles + profile
                                    }
                                    store.saveProfiles(profiles)
                                    activeProfileId = profile.id
                                    store.saveActiveProfileId(profile.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}