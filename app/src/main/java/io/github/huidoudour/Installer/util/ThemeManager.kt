package io.github.huidoudour.Installer.util

import android.content.Context
import android.util.Log
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.ui.theme.GlobalThemeStore
import io.github.huidoudour.Installer.ui.theme.ThemeMode

/**
 * 主题管理工具类
 */
object ThemeManager {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_APP_THEME = "app_theme"
    private const val TAG = "ThemeManager"

    const val THEME_FOLLOW_SYSTEM = -1
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    /**
     * 应用用户选择的主题偏好
     */
    fun applyUserThemePreference(context: Context) {
        try {
            val themeMode = getUserTheme(context)
            applyTheme(themeMode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply theme preference: ${e.message}")
        }
    }

    /**
     * 应用主题模式 — 同步到 Compose 全局主题状态
     * 不再使用 AppCompatDelegate（ComponentActivity 无效），改由 Compose MaterialTheme 控制
     */
    fun applyTheme(themeMode: Int) {
        GlobalThemeStore.syncFromThemeManager(themeMode)
    }

    /**
     * 保存用户选择的主题
     */
    fun saveUserTheme(context: Context, themeMode: Int) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_APP_THEME, themeMode).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save theme setting: ${e.message}")
        }
    }

    /**
     * 获取用户选择的主题
     */
    fun getUserTheme(context: Context): Int {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getInt(KEY_APP_THEME, THEME_FOLLOW_SYSTEM)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get theme setting: ${e.message}")
            THEME_FOLLOW_SYSTEM
        }
    }

    /**
     * 获取主题显示名称
     */
    fun getThemeDisplayName(context: Context, themeMode: Int): String {
        return when (themeMode) {
            THEME_LIGHT -> context.getString(R.string.light_theme)
            THEME_DARK -> context.getString(R.string.dark_theme)
            THEME_FOLLOW_SYSTEM -> context.getString(R.string.follow_system)
            else -> context.getString(R.string.follow_system)
        }
    }
}
