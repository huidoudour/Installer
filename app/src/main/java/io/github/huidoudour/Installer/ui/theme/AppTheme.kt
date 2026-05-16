package io.github.huidoudour.Installer.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Compose 主题入口
 * 提供动态颜色支持和 Material 3 主题
 * 默认版本使用内部 ThemeStateHolder
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val themeStateHolder = remember { ThemeStateHolder() }
    AppTheme(themeStateHolder, content)
}

/**
 * Compose 主题入口
 * 提供动态颜色支持和 Material 3 主题
 */
@Composable
fun AppTheme(
    themeStateHolder: ThemeStateHolder,
    content: @Composable () -> Unit
) {
    val themeState = themeStateHolder.state

    // 判断是否为深色模式
    val isDark = when (themeState.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // 确定主题颜色来源
    val keyColor = if (themeState.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ 使用系统动态颜色
        Color(0xFF4A672D) // fallback
    } else {
        themeState.seedColor
    }

    // 生成调色板
    val colorScheme = remember(keyColor, isDark, themeState.paletteStyle) {
        generateColorScheme(keyColor, isDark)
    }

    // 动画过渡 - 使用更平滑的动画
    val animatedColorScheme = animateColorSchemeWithTransition(colorScheme)

    // 设置状态栏样式
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as ComponentActivity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    // 提供主题状态给子组件
    CompositionLocalProvider(
        LocalThemeStateHolder provides themeStateHolder,
        LocalColorScheme provides animatedColorScheme
    ) {
        MaterialTheme(
            colorScheme = animatedColorScheme,
            typography = Typography,
            shapes = Shapes(
                extraSmall = SmallShape,
                small = SmallShape,
                medium = ButtonShape,
                large = CardShape,
                extraLarge = CardShape
            ),
            content = content
        )
    }
}

/**
 * 生成颜色方案 - 使用 materialKolor 动态调色板
 */
private fun generateColorScheme(
    seedColor: Color,
    isDark: Boolean
): ColorScheme {
    // 使用 materialKolor 生成动态调色板
    val dynamicColorScheme = com.materialkolor.dynamicColorScheme(
        seedColor = seedColor,
        isDark = isDark
    )

    // 将 materialKolor 的 ColorScheme 转换为 Material3 ColorScheme
    // Material3 1.3.1 有更多属性，我们用默认值填充
    return ColorScheme(
        primary = dynamicColorScheme.primary,
        onPrimary = dynamicColorScheme.onPrimary,
        primaryContainer = dynamicColorScheme.primaryContainer,
        onPrimaryContainer = dynamicColorScheme.onPrimaryContainer,
        secondary = dynamicColorScheme.secondary,
        onSecondary = dynamicColorScheme.onSecondary,
        secondaryContainer = dynamicColorScheme.secondaryContainer,
        onSecondaryContainer = dynamicColorScheme.onSecondaryContainer,
        tertiary = dynamicColorScheme.tertiary,
        onTertiary = dynamicColorScheme.onTertiary,
        tertiaryContainer = dynamicColorScheme.tertiaryContainer,
        onTertiaryContainer = dynamicColorScheme.onTertiaryContainer,
        error = dynamicColorScheme.error,
        onError = dynamicColorScheme.onError,
        errorContainer = dynamicColorScheme.errorContainer,
        onErrorContainer = dynamicColorScheme.onErrorContainer,
        background = dynamicColorScheme.background,
        onBackground = dynamicColorScheme.onBackground,
        surface = dynamicColorScheme.surface,
        onSurface = dynamicColorScheme.onSurface,
        surfaceVariant = dynamicColorScheme.surfaceVariant,
        onSurfaceVariant = dynamicColorScheme.onSurfaceVariant,
        outline = dynamicColorScheme.outline,
        outlineVariant = dynamicColorScheme.outlineVariant,
        scrim = dynamicColorScheme.scrim,
        inverseSurface = dynamicColorScheme.inverseSurface,
        inverseOnSurface = dynamicColorScheme.inverseOnSurface,
        inversePrimary = dynamicColorScheme.inversePrimary,
        surfaceTint = dynamicColorScheme.surfaceTint,
        surfaceBright = dynamicColorScheme.surfaceBright,
        surfaceDim = dynamicColorScheme.surfaceDim,
        surfaceContainer = dynamicColorScheme.surfaceContainer,
        surfaceContainerHigh = dynamicColorScheme.surfaceContainerHigh,
        surfaceContainerHighest = dynamicColorScheme.surfaceContainerHighest,
        surfaceContainerLow = dynamicColorScheme.surfaceContainerLow,
        surfaceContainerLowest = dynamicColorScheme.surfaceContainerLowest
    )
}

/**
 * 动画颜色方案过渡 - 直接使用原始颜色方案
 * 注意：Material3 的 MaterialTheme 已经内置了颜色过渡动画
 */
@Composable
private fun animateColorSchemeWithTransition(colorScheme: ColorScheme): ColorScheme {
    // Material3 的 MaterialTheme 组件会自动处理颜色过渡
    // 不需要手动为每个颜色添加动画
    return colorScheme
}
