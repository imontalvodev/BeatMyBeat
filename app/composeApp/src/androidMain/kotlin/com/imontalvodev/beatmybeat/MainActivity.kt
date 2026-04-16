package com.imontalvodev.beatmybeat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.core.content.ContextCompat
import com.imontalvodev.beatmybeat.ui.feature.analyze.AnalyzeScreen
import com.imontalvodev.beatmybeat.ui.feature.playlist.PlaylistScreen
import com.imontalvodev.beatmybeat.ui.feature.player.PlayerScreen
import com.imontalvodev.beatmybeat.ui.feature.theme.ThemeCustomizerScreen
import com.imontalvodev.beatmybeat.ui.theme.BeatMyBeatTheme
import com.imontalvodev.beatmybeat.ui.theme.ThemeProfilesStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
            var profiles by remember { mutableStateOf(store.loadProfiles()) }
            var activeProfileId by remember {
                mutableStateOf(store.loadActiveProfileId() ?: profiles.first().id)
            }
            val activeProfile = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()

            BeatMyBeatTheme(themeProfile = activeProfile) {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        androidx.compose.material3.ModalDrawerSheet {
                            Text(
                                text = "BeatMyBeat",
                                modifier = Modifier.padding(16.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("Downloader") },
                                selected = currentRoute == "analyze",
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate("analyze") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            inclusive = false
                                        }
                                    }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                            NavigationDrawerItem(
                                label = { Text("Player demo") },
                                selected = currentRoute == "player",
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate("player")
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                            NavigationDrawerItem(
                                label = { Text("Editar tema") },
                                selected = currentRoute == "theme-customizer",
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate("theme-customizer")
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                        }
                    },
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = { Text("BeatMyBeat") },
                                navigationIcon = {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                drawerState.open()
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "Open menu",
                                        )
                                    }
                                },
                            )
                        },
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            NavHost(navController = navController, startDestination = "analyze") {
                                composable("analyze") {
                                    AnalyzeScreen(themeName = activeProfile.name)
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
                                PlayerScreen()
                            }
                            composable("theme-customizer") {
                                ThemeCustomizerScreen(
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
                                        profiles = profiles + profile
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