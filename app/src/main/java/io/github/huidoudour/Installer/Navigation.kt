package io.github.huidoudour.Installer

import android.net.Uri

/**
 * 导航路由定义
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Shell : Screen("shell")
    data object Logs : Screen("logs")
    data object Settings : Screen("settings")
    data object Me : Screen("me")
    data object Install : Screen("install/{uri}") {
        fun createRoute(uri: String): String = "install/${Uri.encode(uri)}"
    }
}
