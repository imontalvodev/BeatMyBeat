package com.imontalvodev.beatmybeat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.imontalvodev.beatmybeat.playback.PlaybackServiceBinding
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private fun applyLanguage(languageTag: String) {
        if (languageTag.isBlank()) return
        val locales = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

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
            val themeBootstrap = remember(store) {
                val loaded = store.loadProfiles()
                val active = store.coerceActiveProfileId(loaded.map { it.id }, loaded.first().id)
                loaded to active
            }
            var storageLabel by remember { mutableStateOf(StorageSettings.getLocationLabel(this)) }
            var profiles by remember { mutableStateOf(themeBootstrap.first) }
            var activeProfileId by remember { mutableStateOf(themeBootstrap.second) }
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

            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            BeatMyBeatTheme(themeProfile = activeProfile) {
                CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                    PlaybackServiceBinding {
                        val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val showBottomBar = currentRoute in setOf("analyze", "player", "profile")

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            if (showBottomBar) {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = currentRoute == "analyze",
                                        onClick = {
                                            navController.navigate("analyze") { launchSingleTop = true }
                                        },
                                        icon = {
                                            Icon(
                                                Icons.Filled.Download,
                                                contentDescription = stringResource(R.string.nav_download),
                                            )
                                        },
                                        label = { Text(stringResource(R.string.nav_download)) },
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == "player",
                                        onClick = {
                                            navController.navigate("player") { launchSingleTop = true }
                                        },
                                        icon = {
                                            Icon(
                                                Icons.Filled.MusicNote,
                                                contentDescription = stringResource(R.string.nav_player),
                                            )
                                        },
                                        label = { Text(stringResource(R.string.nav_player)) },
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == "profile",
                                        onClick = {
                                            navController.navigate("profile") { launchSingleTop = true }
                                        },
                                        icon = {
                                            Icon(
                                                Icons.Filled.Person,
                                                contentDescription = stringResource(R.string.nav_profile),
                                            )
                                        },
                                        label = { Text(stringResource(R.string.nav_profile)) },
                                    )
                                }
                            }
                        },
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "splash",
                            modifier = Modifier.padding(innerPadding),
                            enterTransition = {
                                fadeIn(animationSpec = tween(280)) +
                                    slideInHorizontally(animationSpec = tween(280)) { it / 10 }
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(220)) +
                                    slideOutHorizontally(animationSpec = tween(240)) { -it / 10 }
                            },
                            popEnterTransition = {
                                fadeIn(animationSpec = tween(280)) +
                                    slideInHorizontally(animationSpec = tween(280)) { -it / 10 }
                            },
                            popExitTransition = {
                                fadeOut(animationSpec = tween(220)) +
                                    slideOutHorizontally(animationSpec = tween(240)) { it / 10 }
                            },
                        ) {
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
                                AnalyzeScreen()
                            }
                            composable(
                                route = "playlist?url={url}",
                                arguments = listOf(
                                    navArgument("url") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    },
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
                                    onNavigateToDownloader = {
                                        navController.navigate("analyze") {
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            }
                            composable("profile") {
                                ProfileScreen(
                                    onChangeLanguage = { languageTag ->
                                        applyLanguage(languageTag)
                                    },
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
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = this@MainActivity.getString(R.string.profile_folder_open_failed),
                                                    duration = SnackbarDuration.Short,
                                                )
                                            }
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
    }
}
