package dev.huidoudour.installer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.huidoudour.installer.ui.changelog.ChangelogActivity
import dev.huidoudour.installer.ui.installer.InstallerScreen
import dev.huidoudour.installer.ui.lab.LabScreen
import dev.huidoudour.installer.ui.logs.LogsScreen
import dev.huidoudour.installer.ui.me.MeActivity
import dev.huidoudour.installer.ui.settings.SettingsScreen
import dev.huidoudour.installer.ui.shell.ShellScreen
import dev.huidoudour.installer.ui.theme.AppTheme
import dev.huidoudour.installer.util.LanguageManager
import dev.huidoudour.installer.util.ThemeManager
import dev.huidoudour.installer.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyUserThemePreference(this)
        LanguageManager.applyUserLanguagePreference(this)

        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}

data class BottomNavItemData(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var showLab by rememberSaveable { mutableStateOf(false) }
    var labBackProgress by rememberSaveable { mutableFloatStateOf(0f) }
    val labEnterProgress = remember { Animatable(0f) }
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(showLab) {
        if (showLab) {
            labEnterProgress.snapTo(0f)
            labEnterProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = LabEnterEasing
                )
            )
        } else {
            labEnterProgress.snapTo(0f)
        }
    }

    val bottomNavItems = listOf(
        BottomNavItemData(Screen.Home.route, R.string.title_home, ImageVector.vectorResource(R.drawable.ic_home_black)),
        BottomNavItemData(Screen.Shell.route, R.string.title_shell, ImageVector.vectorResource(R.drawable.ic_terminal)),
        BottomNavItemData(Screen.Logs.route, R.string.title_notifications, ImageVector.vectorResource(R.drawable.ic_list)),
        BottomNavItemData(Screen.Settings.route, R.string.title_settings, ImageVector.vectorResource(R.drawable.ic_settings))
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val coverProgress = if (showLab) {
                        if (labBackProgress > 0f) 0f else labEnterProgress.value
                    } else {
                        0f
                    }
                    val direction = if (layoutDirection == LayoutDirection.Rtl) 1f else -1f
                    translationX = direction * size.width * 0.25f * coverProgress
                    alpha = 1f - 0.1f * coverProgress
                },
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                if (!WindowInsets.isImeVisible) {
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
                    val context = LocalContext.current
                    SettingsScreen(
                        onNavigateToMe = {
                            val intent = Intent(context, MeActivity::class.java)
                            context.startActivity(intent)
                        },
                        onNavigateToChangelog = {
                            val intent = Intent(context, ChangelogActivity::class.java)
                            context.startActivity(intent)
                        },
                        onNavigateToLab = {
                            labBackProgress = 0f
                            showLab = true
                        }
                    )
                }
            }
        }

        if (showLab) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.5f * labEnterProgress.value
                    }
                    .background(Color.Black)
            )

            LabScreen(
                enterProgress = labEnterProgress.value,
                onBackProgressChange = { labBackProgress = it },
                onBack = { showLab = false }
            )
        }
    }
}

/** 与参考项目 MiuixDefault 相同的 500ms 程序化入场缓动。 */
private val LabEnterEasing = Easing { fraction ->
    val response = 0.8
    val damping = 0.95
    val omega = 2.0 * PI / response
    val stiffness = omega * omega
    val dampingCoefficient = damping * 4.0 * PI / response
    val decayRate = -dampingCoefficient / 2.0
    val dampedFrequency = sqrt(4.0 * stiffness - dampingCoefficient * dampingCoefficient) / 2.0
    val phase = decayRate / dampedFrequency
    val time = fraction.toDouble()
    (exp(decayRate * time) * (-cos(dampedFrequency * time) + phase * sin(dampedFrequency * time)) + 1.0).toFloat()
}
