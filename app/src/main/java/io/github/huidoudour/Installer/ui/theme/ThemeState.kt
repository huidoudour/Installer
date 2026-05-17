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
