package io.github.huidoudour.Installer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 预设颜色数据类
 */
data class RawColor(
    val key: String,
    val color: Color,
    val displayName: String
)

/**
 * 18 种预设主题颜色
 * 源自 Material Design 3 调色板
 */
val PresetColors: List<RawColor> = listOf(
    RawColor("default", Color(0xFF4A672D), "Default Green"),
    RawColor("pink", Color(0xFFB94073), "Pink"),
    RawColor("red", Color(0xFFBA1A1A), "Red"),
    RawColor("orange", Color(0xFF944A00), "Orange"),
    RawColor("amber", Color(0xFF8C5300), "Amber"),
    RawColor("yellow", Color(0xFF795900), "Yellow"),
    RawColor("lime", Color(0xFF5E6400), "Lime"),
    RawColor("green", Color(0xFF006D39), "Green"),
    RawColor("cyan", Color(0xFF006A64), "Cyan"),
    RawColor("teal", Color(0xFF006874), "Teal"),
    RawColor("light_blue", Color(0xFF00639B), "Light Blue"),
    RawColor("blue", Color(0xFF335BBC), "Blue"),
    RawColor("indigo", Color(0xFF5355A9), "Indigo"),
    RawColor("purple", Color(0xFF6750A4), "Purple"),
    RawColor("deep_purple", Color(0xFF7E42A4), "Deep Purple"),
    RawColor("blue_grey", Color(0xFF575D7E), "Blue Grey"),
    RawColor("brown", Color(0xFF7D524A), "Brown"),
    RawColor("grey", Color(0xFF5F6162), "Grey")
)

/**
 * 默认主题颜色
 */
val DEFAULT_SEED_COLOR: Color = PresetColors[0].color
