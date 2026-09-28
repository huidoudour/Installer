package dev.huidoudour.installer.ui

/**
 * 导航路由定义
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Shell : Screen("shell")
    data object Logs : Screen("logs")
    data object Settings : Screen("settings")
    data object Install : Screen("install/{uri}")
}
