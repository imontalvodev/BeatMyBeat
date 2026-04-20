package com.imontalvodev.beatmybeat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
            var profiles by remember { mutableStateOf(store.loadProfiles()) }
            var activeProfileId by remember {
                mutableStateOf(store.loadActiveProfileId() ?: profiles.first().id)
            }
            val activeProfile = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()

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
                                onCustomizeBackground = { navController.navigate("theme-customizer") },
                            )
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