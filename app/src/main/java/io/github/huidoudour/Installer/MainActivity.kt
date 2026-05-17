package io.github.huidoudour.Installer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.huidoudour.Installer.ui.InstallerScreen
import io.github.huidoudour.Installer.ui.LogsScreen
import io.github.huidoudour.Installer.ui.SettingsScreen
import io.github.huidoudour.Installer.ui.ShellScreen
import io.github.huidoudour.Installer.ui.theme.AppTheme
import io.github.huidoudour.Installer.ui.theme.ThemeMode
import io.github.huidoudour.Installer.ui.theme.ThemeStateHolder
import io.github.huidoudour.Installer.util.LanguageManager
import io.github.huidoudour.Installer.util.ThemeManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyUserThemePreference(this)
        LanguageManager.applyUserLanguagePreference(this)

        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AppTheme {
                MainScreen(
                    onThemeClick = { /* Navigate to theme settings */ }
                )
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
    onThemeClick: () -> Unit = {}
) {
    val navController = rememberNavController()

    val bottomNavItems = listOf(
        BottomNavItemData(Screen.Home.route, R.string.title_home, ImageVector.vectorResource(R.drawable.ic_home_black)),
        BottomNavItemData(Screen.Shell.route, R.string.title_shell, ImageVector.vectorResource(R.drawable.ic_terminal)),
        BottomNavItemData(Screen.Logs.route, R.string.title_notifications, ImageVector.vectorResource(R.drawable.ic_list)),
        BottomNavItemData(Screen.Settings.route, R.string.title_settings, ImageVector.vectorResource(R.drawable.ic_settings))
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
        }
    }
}

// ============ Compose 预览 ============

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    AppTheme {
        MainScreen(
            onThemeClick = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Dark Theme")
@Composable
fun MainScreenDarkPreview() {
    val themeStateHolder = remember { 
        ThemeStateHolder().apply {
            setThemeMode(ThemeMode.DARK)
        }
    }
    AppTheme(themeStateHolder) {
        MainScreen(
            onThemeClick = {}
        )
    }
}
