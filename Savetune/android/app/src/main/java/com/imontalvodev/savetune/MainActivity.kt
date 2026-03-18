package com.imontalvodev.savetune

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.imontalvodev.savetune.ui.feature.analyze.AnalyzeScreen
import com.imontalvodev.savetune.ui.feature.playlist.PlaylistScreen
import com.imontalvodev.savetune.ui.feature.player.PlayerScreen
import com.imontalvodev.savetune.ui.theme.SavetuneTheme
import com.imontalvodev.savetune.ui.theme.SavetuneThemeMode
import com.imontalvodev.savetune.ui.theme.rememberSavetuneThemeModeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeModeState = rememberSavetuneThemeModeState()
            SavetuneTheme(themeMode = themeModeState.value) {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        androidx.compose.material3.ModalDrawerSheet {
                            Text(
                                text = "Savetune",
                                modifier = Modifier.padding(16.dp),
                            )
                            NavigationDrawerItem(
                                label = { Text("Downloader") },
                                selected = false,
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
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate("player")
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
                                title = { Text("Savetune") },
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
                                    AnalyzeScreen(
                                        themeMode = themeModeState.value,
                                        onToggleTheme = {
                                            themeModeState.value =
                                                if (themeModeState.value == SavetuneThemeMode.NeonMint) {
                                                    SavetuneThemeMode.CherryPulse
                                                } else {
                                                    SavetuneThemeMode.NeonMint
                                                }
                                        },
                                        onOpenPlaylist = { playlistUrl ->
                                            navController.navigate("playlist?url=${java.net.URLEncoder.encode(playlistUrl, "UTF-8")}")
                                        },
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
                                PlayerScreen()
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}