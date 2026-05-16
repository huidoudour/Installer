package io.github.huidoudour.Installer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.huidoudour.Installer.ui.*
import io.github.huidoudour.Installer.ui.theme.AppTheme
import io.github.huidoudour.Installer.util.LanguageManager
import io.github.huidoudour.Installer.util.ThemeManager

class MainActivity : ComponentActivity() {

    private var _pendingInstallUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyUserThemePreference(this)
        LanguageManager.applyUserLanguagePreference(this)

        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AppTheme {
                MainScreen(
                    installUri = _pendingInstallUri,
                    onInstallUriConsumed = { _pendingInstallUri = null },
                    onThemeClick = { /* Navigate to theme settings */ }
                )
            }
        }

        handleInstallIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInstallIntent(intent)
    }

    private fun handleInstallIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val data: Uri? = intent.data

        if (Intent.ACTION_VIEW == action || Intent.ACTION_INSTALL_PACKAGE == action) {
            if (data != null) {
                _pendingInstallUri = data
                println("Received install intent: $data")
            }
        }
    }
}

data class BottomNavItemData(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    installUri: Uri? = null,
    onInstallUriConsumed: () -> Unit = {},
    onThemeClick: () -> Unit = {}
) {
    val navController = rememberNavController()
    var installUriState by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(installUri) {
        if (installUri != null) {
            installUriState = installUri
        }
    }

    LaunchedEffect(installUriState) {
        if (installUriState != null) {
            navController.navigate(Screen.Install.createRoute(installUriState.toString())) {
                launchSingleTop = true
            }
            installUriState = null
            onInstallUriConsumed()
        }
    }

    val bottomNavItems = listOf(
        BottomNavItemData(Screen.Home.route, R.string.title_home, Icons.Default.Home),
        BottomNavItemData(Screen.Shell.route, R.string.title_shell, Icons.Default.Build),
        BottomNavItemData(Screen.Logs.route, R.string.title_notifications, Icons.Default.List),
        BottomNavItemData(Screen.Settings.route, R.string.title_settings, Icons.Default.Settings)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.titleResId)) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                InstallerScreen(
                    onThemeClick = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Shell.route) {
                ShellScreen()
            }
            composable(Screen.Logs.route) {
                LogsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onThemeClick = onThemeClick)
            }
            composable(Screen.Install.route) { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("uri")
                val decodedUri = uri?.let { Uri.decode(it) }?.let { Uri.parse(it) }
                InstallDialogScreen(
                    installUri = decodedUri,
                    onDismiss = { navController.popBackStack() },
                    onInstallComplete = { navController.popBackStack() },
                    onOpenApp = { packageName ->
                        // Open app implementation
                    }
                )
            }
        }
    }
}
