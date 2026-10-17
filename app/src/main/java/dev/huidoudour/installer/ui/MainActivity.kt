package dev.huidoudour.installer.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.LayoutDirection
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
    var overlayBackProgress by rememberSaveable { mutableFloatStateOf(0f) }
    val overlayEnterProgress = remember { Animatable(0f) }
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(overlayPage) {
        if (overlayPage != OverlayPage.None) {
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
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 鍙犲姞椤甸潰鎶婁笅灞傞〉闈㈡帹寮€ 25%锛涜繑鍥炴墜鍔?/ 椤堕儴杩斿洖鎸夐挳鐨勮繘搴︿細鎶婂畠骞虫粦褰掍綅銆?
                    val settle = overlayBackProgress.coerceIn(0f, 1f)
                    val coverProgress = if (overlayPage != OverlayPage.None) {
                        overlayEnterProgress.value * (1f - settle)
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
                                        // 搴曢儴瀵艰埅鏄钩绾у垏鎹細鍒囪蛋鏃舵妸鍥為€€鏍堟暣涓竻绌猴紙鐘舵€佸瓨璧锋潵浠ヤ究鎭㈠锛夛紝
                                        // 杩欐牱鍦ㄤ换浣?tab 涓婅繑鍥為兘鏄€岄€€鍑哄埌妗岄潰銆嶏紝鑰屼笉鏄€€鍥炲畨瑁呴〉銆?
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
                // 鏄惧紡澹版槑绾贰鍏ユ贰鍑鸿浆鍦猴細搴曢儴瀵艰埅鍚勯〉闈㈤棿杩斿洖鏃朵笉瑕佺缉鏀?浣嶇Щ鏁堟灉銆?
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
                            overlayBackProgress = 0f
                            overlayPage = OverlayPage.Me
                        },
                        onNavigateToChangelog = {
                            overlayBackProgress = 0f
                            overlayPage = OverlayPage.Changelog
                        },
                        onNavigateToAdvanced = {
                            overlayBackProgress = 0f
                            overlayPage = OverlayPage.Advanced
                        },
                        onNavigateToExperimental = {
                            overlayBackProgress = 0f
                            overlayPage = OverlayPage.Experimental
                        },
                        onNavigateToInstallSettings = {
                            overlayBackProgress = 0f
                            overlayPage = OverlayPage.InstallSettings
                        }
                    )
                }
            }
        }

        // 杩欓噷**涓?*娉ㄥ唽杩斿洖澶勭悊鍣細搴曢儴瀵艰埅鏄钩绾ч〉闈紝鍥為€€鏍堢敱涓婇潰鐨?popUpTo(graph.id) 淇濇寔涓?1锛?
        // NavHost 鑷甫鐨勮繑鍥炲洖璋冨洜姝ゅ浜庣鐢ㄧ姸鎬侊紝绯荤粺杩斿洖锛堟墜鍔?鎸夐敭锛変細鐩存帴缁撴潫 Activity
        // 鈫?閫€鍒版闈€備笉鍐嶅嚭鐜般€屽厛鎾斁绯荤粺鐨勭缉鏀鹃瑙堛€佸啀閫€鍥炲畨瑁呴〉銆嶇殑缁勫悎鏁堟灉銆?
        // 鍙犲姞椤甸潰鎵撳紑鏃朵笉鍦ㄦ澶勬嫤鎴紝鐢卞彔鍔犻〉闈㈣嚜宸辩殑棰勬祴鎬ц繑鍥炴帴绠★細涓€娆¤繑鍥炲彧鍏抽棴瀛愰〉闈€?

        if (overlayPage != OverlayPage.None) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 瀛愰〉闈㈤€€鍦猴紙鎵嬪娍杩涘害鎴栭《閮ㄨ繑鍥炴寜閽殑婊戝嚭锛夋椂锛岄伄缃╀竴璧锋贰鍒?0銆?
                        val settle = overlayBackProgress.coerceIn(0f, 1f)
                        alpha = 0.5f * overlayEnterProgress.value * (1f - settle)
                    }
                    .background(Color.Black)
            )

            when (overlayPage) {
                OverlayPage.Advanced -> AdvancedSettingsScreen(
                    enterProgress = overlayEnterProgress.value,
                    onBackProgressChange = { overlayBackProgress = it },
                    onBack = { overlayPage = OverlayPage.None }
                )

                OverlayPage.Experimental -> ExperimentalScreen(
                    enterProgress = overlayEnterProgress.value,
                    onBackProgressChange = { overlayBackProgress = it },
                    onBack = { overlayPage = OverlayPage.None }
                )

                OverlayPage.Me -> PredictiveBackPage(
                    enterProgress = overlayEnterProgress.value,
                    onBackProgressChange = { overlayBackProgress = it },
                    onBack = { overlayPage = OverlayPage.None }
                ) {
                    MeScreen()
                }

                OverlayPage.Changelog -> PredictiveBackPage(
                    enterProgress = overlayEnterProgress.value,
                    onBackProgressChange = { overlayBackProgress = it },
                    onBack = { overlayPage = OverlayPage.None }
                ) { requestBack ->
                    ChangelogScreen(onNavigateBack = requestBack)
                }

                OverlayPage.InstallSettings -> PredictiveBackPage(
                    enterProgress = overlayEnterProgress.value,
                    onBackProgressChange = { overlayBackProgress = it },
                    onBack = { overlayPage = OverlayPage.None }
                ) { requestBack ->
                    InstallSettingsScreen(onBack = requestBack)
                }

                OverlayPage.None -> Unit
            }
        }
    }
}

/** 涓庡弬鑰冮」鐩?MiuixDefault 鐩稿悓鐨?500ms 绋嬪簭鍖栧叆鍦虹紦鍔ㄣ€?*/
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
