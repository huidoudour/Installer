package io.github.huidoudour.installer.util

import android.content.Context

/**
 * 安装对话框加载动画模式
 */
enum class LoaderAnimationMode {
    /** 图形加载动画（Material 3 包含式指示器，中心稳定） */
    GRAPHIC,

    /** 线性波浪进度条（Material 3 Expressive LinearWavyProgressIndicator） */
    WAVE
}

/**
 * 安装对话框加载动画偏好管理
 */
object LoaderAnimationPrefs {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_LOADER_MODE = "loader_animation_mode"

    /**
     * 获取当前加载动画模式（默认图形动画）
     */
    fun getMode(context: Context): LoaderAnimationMode {
        return try {
            val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LOADER_MODE, null)
            LoaderAnimationMode.valueOf(
                saved ?: LoaderAnimationMode.GRAPHIC.name
            )
        } catch (e: Exception) {
            LoaderAnimationMode.GRAPHIC
        }
    }

    /**
     * 保存加载动画模式
     */
    fun saveMode(context: Context, mode: LoaderAnimationMode) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LOADER_MODE, mode.name)
                .apply()
        } catch (e: Exception) {
            // ignore
        }
    }
}
