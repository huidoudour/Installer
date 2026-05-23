package io.github.huidoudour.Installer.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 调色板风格枚举
 */
enum class PaletteStyle(val displayName: String) {
    TONAL_SPOT("Tonal Spot"),
    VIBRANT("Vibrant"),
    EXPRESSIVE("Expressive"),
    NEUTRAL("Neutral"),
    RAINBOW("Rainbow"),
    FRUIT_SALAD("Fruit Salad")
}

/**
 * 主题模式
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * 主题状态数据类
 */
@Immutable
data class ThemeState(
    val isLoaded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val paletteStyle: PaletteStyle = PaletteStyle.TONAL_SPOT,
    val useDynamicColor: Boolean = true,
    val seedColor: Color = DEFAULT_SEED_COLOR
)

/**
 * 本地主题状态持有者
 * 用于在 Compose 树中共享主题状态
 */
class ThemeStateHolder(initialState: ThemeState = ThemeState()) {
    var state by mutableStateOf(initialState)
        private set

    fun updateState(newState: ThemeState) {
        state = newState
    }

    fun setSeedColor(color: Color) {
        state = state.copy(seedColor = color)
    }

    fun setUseDynamicColor(use: Boolean) {
        state = state.copy(useDynamicColor = use)
    }

    fun setThemeMode(mode: ThemeMode) {
        state = state.copy(themeMode = mode)
    }

    fun setPaletteStyle(style: PaletteStyle) {
        state = state.copy(paletteStyle = style)
    }

    fun setLoaded(loaded: Boolean) {
        state = state.copy(isLoaded = loaded)
    }
}

/**
 * Local Composition key for theme state
 */
val LocalThemeStateHolder = compositionLocalOf<ThemeStateHolder> {
    error("No ThemeStateHolder provided")
}

/**
 * Local Composition key for color scheme
 */
val LocalColorScheme = compositionLocalOf<androidx.compose.material3.ColorScheme> {
    error("No ColorScheme provided")
}

/**
 * 全局主题状态存储 — 跨 Compose 树和 Activity 共享
 * 用于解决 AppTheme 与 ThemeManager 之间的状态同步问题
 */
object GlobalThemeStore {
    private val _themeMode = mutableStateOf(ThemeMode.SYSTEM)
    val themeMode: ThemeMode get() = _themeMode.value

    var onThemeChanged: ((ThemeMode) -> Unit)? = null

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        onThemeChanged?.invoke(mode)
    }

    /** 从 ThemeManager 同步到 Compose */
    fun syncFromThemeManager(themeValue: Int) {
        _themeMode.value = when (themeValue) {
            1 -> ThemeMode.LIGHT   // THEME_LIGHT
            2 -> ThemeMode.DARK    // THEME_DARK
            else -> ThemeMode.SYSTEM
        }
    }

    /** 同步到 ThemeManager */
    fun themeValueForManager(): Int = when (_themeMode.value) {
        ThemeMode.LIGHT -> 1
        ThemeMode.DARK -> 2
        ThemeMode.SYSTEM -> -1
    }
}
