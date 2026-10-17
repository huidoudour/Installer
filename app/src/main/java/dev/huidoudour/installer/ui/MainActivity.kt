package dev.huidoudour.installer.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.huidoudour.installer.R
import dev.huidoudour.installer.ui.changelog.ChangelogScreen
import dev.huidoudour.installer.ui.components.PredictiveBackPage
import dev.huidoudour.installer.ui.installer.InstallerScreen
import dev.huidoudour.installer.ui.lab.AdvancedSettingsScreen
import dev.huidoudour.installer.ui.lab.ExperimentalScreen
import dev.huidoudour.installer.ui.logs.LogsScreen
import dev.huidoudour.installer.ui.me.MeScreen
import dev.huidoudour.installer.ui.settings.InstallSettingsScreen
import dev.huidoudour.installer.ui.settings.SettingsScreen
import dev.huidoudour.installer.ui.theme.AppTheme
import dev.huidoudour.installer.util.LanguageManager
import dev.huidoudour.installer.util.ThemeManager
import dev.huidoudour.terminal.ShellScreen
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

private enum class OverlayPage {
    None,
    Advanced,
    Experimental,
    Me,
    Changelog,
    InstallSettings,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var overlayPage by rememberSaveable { mutableStateOf(OverlayPage.None) }
    // 盖在下层页面上的遮罩强度（1 = 常态压暗，0 = 完全揭开）。由叠加页面在上报：
    // 手势期间恒为 1，只有真正松手确认返回后才淡到 0。
    var overlayScrim by rememberSaveable { mutableFloatStateOf(1f) }
    val overlayEnterProgress = remember { Animatable(0f) }

    LaunchedEffect(overlayPage) {
        if (overlayPage != OverlayPage.None) {
            overlayScrim = 1f
            overlayEnterProgress.snapTo(0f)
            overlayEnterProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = LabEnterEasing
                )
            )
        } else {
            overlayEnterProgress.snapTo(0f)
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
            // 下层页面保持静止：叠加页面打开/返回时都不位移、不缩放、不淡入。
            // 只由上面那层遮罩负责“压暗 / 揭开”，返回到的页面就是「直接显示」。
            modifier = Modifier.fillMaxSize(),
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
                                        // 底部导航是平级切换：切走时把回退栈整个清空（状态存起来以便恢复），
                                        // 这样在任何 tab 上返回都是「退出到桌面」，而不是退回安装页。
                                        popUpTo(navController.graph.id) {
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
                modifier = Modifier.padding(innerPadding),
                // 显式声明纯淡入淡出转场：底部导航各页面间不要缩放/位移效果。
                enterTransition = { fadeIn(animationSpec = tween(220)) },
                exitTransition = { fadeOut(animationSpec = tween(220)) },
                popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                popExitTransition = { fadeOut(animationSpec = tween(220)) }
            ) {
                composable(Screen.Home.route) {
                    InstallerScreen(
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
                        onNavigateToMe = {
                            overlayPage = OverlayPage.Me
                        },
                        onNavigateToChangelog = {
                            overlayPage = OverlayPage.Changelog
                        },
                        onNavigateToAdvanced = {
                            overlayPage = OverlayPage.Advanced
                        },
                        onNavigateToExperimental = {
                            overlayPage = OverlayPage.Experimental
                        },
                        onNavigateToInstallSettings = {
                            overlayPage = OverlayPage.InstallSettings
                        }
                    )
                }
            }
        }

        // 这里**不**注册返回处理器：底部导航是平级页面，回退栈由上面的 popUpTo(graph.id) 保持为 1，
        // NavHost 自带的返回回调因此处于禁用状态，系统返回（手势/按键）会直接结束 Activity
        // → 退到桌面。不会出现「先播放系统缩放预览、再退回安装页」的组合效果。
        // 叠加页面打开时不在此处拦截，由叠加页面自己的预测性返回接管：一次返回只关闭子页面。

        if (overlayPage != OverlayPage.None) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 遮罩：进场时随 overlayEnterProgress 压暗；
                        // 返回时由叠加页面自己上报 —— 手势期间恒为 1，确认返回后才淡到 0。
                        alpha = 0.5f * overlayEnterProgress.value * overlayScrim.coerceIn(0f, 1f)
                    }
                    .background(Color.Black)
            )

            when (overlayPage) {
                OverlayPage.Advanced -> AdvancedSettingsScreen(
                    enterProgress = overlayEnterProgress.value,
                    onScrimChange = { overlayScrim = it },
                    onBack = { overlayPage = OverlayPage.None }
                )

                OverlayPage.Experimental -> ExperimentalScreen(
                    enterProgress = overlayEnterProgress.value,
                    onScrimChange = { overlayScrim = it },
                    onBack = { overlayPage = OverlayPage.None }
                )

                OverlayPage.Me -> PredictiveBackPage(
                    enterProgress = overlayEnterProgress.value,
                    onScrimChange = { overlayScrim = it },
                    onBack = { overlayPage = OverlayPage.None }
                ) {
                    MeScreen()
                }

                OverlayPage.Changelog -> PredictiveBackPage(
                    enterProgress = overlayEnterProgress.value,
                    onScrimChange = { overlayScrim = it },
                    onBack = { overlayPage = OverlayPage.None }
                ) { requestBack ->
                    ChangelogScreen(onNavigateBack = requestBack)
                }

                OverlayPage.InstallSettings -> PredictiveBackPage(
                    enterProgress = overlayEnterProgress.value,
                    onScrimChange = { overlayScrim = it },
                    onBack = { overlayPage = OverlayPage.None }
                ) { requestBack ->
                    InstallSettingsScreen(onBack = requestBack)
                }

                OverlayPage.None -> Unit
            }
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
