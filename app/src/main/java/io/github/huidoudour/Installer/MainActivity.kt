package io.github.huidoudour.Installer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import io.github.huidoudour.Installer.ui.MeScreen
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

            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                bottomNavItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.titleResId)) },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
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
                SettingsScreen(
                    onThemeClick = onThemeClick,
                    onNavigateToMe = { navController.navigate(Screen.Me.route) }
                )
            }
            composable(Screen.Me.route) {
                MeScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

// ============ Compose 预览 ============

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    // 简单预览 - 仅显示底部导航栏框架
    // 由于 Screen 依赖 ViewModel，预览中无法实例化
    AppTheme {
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
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp
                ) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.titleResId)) },
                            selected = item.route == Screen.Home.route,
                            onClick = {},
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            // 显示完整的 InstallerScreen UI
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                io.github.huidoudour.Installer.ui.InstallerScreenContent(
                    privilegeMode = io.github.huidoudour.Installer.util.PrivilegeHelper.PrivilegeMode.SHIZUKU,
                    privilegeStatus = io.github.huidoudour.Installer.util.PrivilegeHelper.PrivilegeStatus.AUTHORIZED,
                    selectedFileName = "example_app.apk",
                    fileType = "APK 文件",
                    isXapkFile = false,
                    isInstallEnabled = true,
                    isInstalling = false,
                    enableCustomPackageName = true,
                    replaceExisting = true,
                    grantPermissions = false,
                    onSwitchPrivilege = {},
                    onRequestPermission = {},
                    onRefresh = {},
                    onSelectFile = {},
                    onEnableCustomPackageNameChange = {},
                    onReplaceExistingChange = {},
                    onGrantPermissionsChange = {},
                    onInstall = {},
                    onSwitchInstallerPackage = {}
                )
            }
        }
    }
}
