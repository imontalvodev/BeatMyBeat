package com.imontalvodev.beatmybeat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.imontalvodev.beatmybeat.ui.theme.Motion
import com.imontalvodev.beatmybeat.ui.theme.DownloadStatusBar
import com.imontalvodev.beatmybeat.download.DownloadProgressBus
import com.imontalvodev.beatmybeat.service.SongDownloadService
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.imontalvodev.beatmybeat.playback.PlaybackServiceBinding
import com.imontalvodev.beatmybeat.ui.feature.analyze.AnalyzeScreen
import com.imontalvodev.beatmybeat.ui.feature.player.GlobalMiniPlayer
import com.imontalvodev.beatmybeat.ui.feature.player.PlayerScreen
import com.imontalvodev.beatmybeat.ui.feature.player.rememberHasActivePlayback
import com.imontalvodev.beatmybeat.ui.feature.profile.ProfileScreen
import com.imontalvodev.beatmybeat.ui.feature.update.ApkUpdateInstaller
import com.imontalvodev.beatmybeat.ui.feature.update.ReleaseUpdatePrompt
import com.imontalvodev.beatmybeat.ui.feature.update.UpdateDownloadStatusPrompt
import com.imontalvodev.beatmybeat.ui.feature.theme.ThemeCustomizerScreen
import com.imontalvodev.beatmybeat.ui.feature.theme.ThemeCustomizerSection
import com.imontalvodev.beatmybeat.ui.storage.StorageSettings
import com.imontalvodev.beatmybeat.ui.theme.BeatMyBeatTheme
import com.imontalvodev.beatmybeat.ui.theme.ThemeProfilesStore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
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

        // Solo afecta mientras la ventana está visible en primer plano: Android deja de
        // respetar el flag en cuanto la app pasa a segundo plano, sin necesidad de limpiarlo
        // manualmente en onPause.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Android 13+ requiere permiso runtime para notificaciones.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(permission)
        }

        ApkUpdateInstaller.tryCompletePendingInstall(this)

        setContent {
            var localeCompositionEpoch by remember { mutableIntStateOf(0) }
            @Suppress("UNUSED_VARIABLE")
            val localeTick = localeCompositionEpoch

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
                    ReleaseUpdatePrompt()
                    UpdateDownloadStatusPrompt()
                    PlaybackServiceBinding {
                        key(localeTick) {
                        val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    var playerImmersive by remember { mutableStateOf(false) }
                    val showBottomBar = currentRoute in TOP_LEVEL_ROUTES && !playerImmersive

                    fun navigateToTab(route: String) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            Column {
                                // Se conserva el último estado para que la barra no se vacíe
                                // durante la animación de salida al terminar la descarga.
                                val activeDownload by DownloadProgressBus.state.collectAsState()
                                var lastDownload by remember { mutableStateOf(activeDownload) }
                                if (activeDownload != null) lastDownload = activeDownload
                                AnimatedVisibility(
                                    visible = activeDownload != null && !playerImmersive,
                                    enter = expandVertically(tween(Motion.LAYOUT)) + fadeIn(tween(Motion.STANDARD)),
                                    exit = shrinkVertically(tween(Motion.LAYOUT)) + fadeOut(tween(Motion.QUICK)),
                                ) {
                                    lastDownload?.let { download ->
                                        DownloadStatusBar(
                                            download = download,
                                            onOpen = { navigateToTab("analyze") },
                                            onCancel = { SongDownloadService.cancelDownload(this@MainActivity) },
                                        )
                                    }
                                }
                                // Biblioteca pinta su propio mini reproductor (con carátula de la biblioteca);
                                // en el resto de pestañas se muestra este, leído del servicio.
                                val hasPlayback = rememberHasActivePlayback()
                                AnimatedVisibility(
                                    visible = hasPlayback && showBottomBar && currentRoute != "player",
                                    enter = expandVertically(tween(Motion.LAYOUT)) + fadeIn(tween(Motion.STANDARD)),
                                    exit = shrinkVertically(tween(Motion.LAYOUT)) + fadeOut(tween(Motion.QUICK)),
                                ) {
                                    GlobalMiniPlayer(onOpenPlayer = { navigateToTab("player") })
                                }
                                AnimatedVisibility(
                                    visible = showBottomBar,
                                    enter = expandVertically(tween(Motion.LAYOUT)) + fadeIn(tween(Motion.STANDARD)),
                                    exit = shrinkVertically(tween(Motion.LAYOUT)) + fadeOut(tween(Motion.QUICK)),
                                ) {
                                    AppNavigationBar(
                                        currentRoute = currentRoute,
                                        onSelect = ::navigateToTab,
                                    )
                                }
                            }
                        },
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "player",
                            modifier = Modifier.padding(innerPadding),
                            // Entre pestañas: fundido (Material "fade through"). Deslizar lateralmente
                            // sugiere jerarquía, y las pestañas son hermanas.
                            enterTransition = {
                                if (isTabSwitch()) fadeIn(tween(Motion.STANDARD))
                                else fadeIn(tween(Motion.STANDARD)) + slideInHorizontally(tween(Motion.LAYOUT)) { it / 10 }
                            },
                            exitTransition = {
                                if (isTabSwitch()) fadeOut(tween(Motion.QUICK))
                                else fadeOut(tween(Motion.QUICK)) + slideOutHorizontally(tween(Motion.LAYOUT)) { -it / 10 }
                            },
                            popEnterTransition = {
                                if (isTabSwitch()) fadeIn(tween(Motion.STANDARD))
                                else fadeIn(tween(Motion.STANDARD)) + slideInHorizontally(tween(Motion.LAYOUT)) { -it / 10 }
                            },
                            popExitTransition = {
                                if (isTabSwitch()) fadeOut(tween(Motion.QUICK))
                                else fadeOut(tween(Motion.QUICK)) + slideOutHorizontally(tween(Motion.LAYOUT)) { it / 10 }
                            },
                        ) {
                            composable("analyze") {
                                AnalyzeScreen()
                            }
                            composable("player") {
                                PlayerScreen(
                                    onNavigateToDownloader = { navigateToTab("analyze") },
                                    onImmersiveChange = { playerImmersive = it },
                                )
                            }
                            composable("profile") {
                                ProfileScreen(
                                    onChangeLanguage = { languageTag ->
                                        applyLanguage(languageTag)
                                        localeCompositionEpoch++
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
                                    onBack = { navController.popBackStack() },
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
                                    onBack = { navController.popBackStack() },
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
                        } // localeTick
                    } // PlaybackServiceBinding
                } // CompositionLocalProvider
            } // BeatMyBeatTheme
        } // setContent
    } // onCreate

    override fun onResume() {
        super.onResume()
        ApkUpdateInstaller.tryCompletePendingInstall(this)
    }
}

private val TOP_LEVEL_ROUTES = setOf("player", "analyze", "profile")

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in TOP_LEVEL_ROUTES && targetState.destination.route in TOP_LEVEL_ROUTES

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

private val NAV_TABS = listOf(
    NavTab("player", R.string.nav_player, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    NavTab("analyze", R.string.nav_download, Icons.Filled.Download, Icons.Outlined.Download),
    NavTab("profile", R.string.nav_profile, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
private fun AppNavigationBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        NAV_TABS.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(tab.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}
