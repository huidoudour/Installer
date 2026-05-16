package io.github.huidoudour.Installer.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import java.util.Locale

/**
 * 语言管理工具类
 */
object LanguageManager {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val TAG = "LanguageManager"

    const val LANGUAGE_FOLLOW_SYSTEM = "system"
    const val LANGUAGE_SIMPLIFIED_CHINESE = "zh-rCN"
    const val LANGUAGE_TRADITIONAL_CHINESE = "zh-rTW"
    const val LANGUAGE_HONGKONG_CHINESE = "zh-rHK"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_JAPANESE = "ja"
    const val LANGUAGE_RUSSIAN = "ru"

    /**
     * 应用用户选择的语言偏好
     */
    fun applyUserLanguagePreference(context: Context) {
        try {
            val language = getUserLanguage(context)
            applyLanguage(context, language)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply language preference: ${e.message}")
        }
    }

    /**
     * 应用指定语言
     */
    fun applyLanguage(context: Context, languageCode: String) {
        try {
            val locale = when (languageCode) {
                LANGUAGE_FOLLOW_SYSTEM -> Locale.getDefault()
                LANGUAGE_SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
                LANGUAGE_TRADITIONAL_CHINESE -> Locale.TRADITIONAL_CHINESE
                LANGUAGE_HONGKONG_CHINESE -> Locale("zh", "HK")
                LANGUAGE_ENGLISH -> Locale.ENGLISH
                LANGUAGE_JAPANESE -> Locale.JAPANESE
                LANGUAGE_RUSSIAN -> Locale("ru")
                else -> Locale.getDefault()
            }

            Locale.setDefault(locale)

            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)

            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply language: ${e.message}")
        }
    }

    /**
     * 保存用户选择的语言
     */
    fun saveUserLanguage(context: Context, languageCode: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_APP_LANGUAGE, languageCode).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save language setting: ${e.message}")
        }
    }

    /**
     * 获取用户选择的语言
     */
    fun getUserLanguage(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_APP_LANGUAGE, LANGUAGE_FOLLOW_SYSTEM) ?: LANGUAGE_FOLLOW_SYSTEM
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get language setting: ${e.message}")
            LANGUAGE_FOLLOW_SYSTEM
        }
    }

    /**
     * 获取语言显示名称
     */
    fun getLanguageDisplayName(context: Context, languageCode: String): String {
        return when (languageCode) {
            LANGUAGE_FOLLOW_SYSTEM -> context.getString(io.github.huidoudour.Installer.R.string.follow_system)
            LANGUAGE_SIMPLIFIED_CHINESE -> context.getString(io.github.huidoudour.Installer.R.string.simplified_chinese)
            LANGUAGE_TRADITIONAL_CHINESE -> "繁體中文"
            LANGUAGE_HONGKONG_CHINESE -> "繁體中文（香港）"
            LANGUAGE_ENGLISH -> "English"
            LANGUAGE_JAPANESE -> "日本語"
            LANGUAGE_RUSSIAN -> "Русский"
            else -> context.getString(io.github.huidoudour.Installer.R.string.follow_system)
        }
    }

    /**
     * 获取所有支持的语言列表
     */
    fun getSupportedLanguages(): List<Pair<String, String>> {
        return listOf(
            LANGUAGE_FOLLOW_SYSTEM to "System Default",
            LANGUAGE_SIMPLIFIED_CHINESE to "简体中文",
            LANGUAGE_TRADITIONAL_CHINESE to "繁體中文",
            LANGUAGE_HONGKONG_CHINESE to "繁體中文（香港）",
            LANGUAGE_ENGLISH to "English",
            LANGUAGE_JAPANESE to "日本語",
            LANGUAGE_RUSSIAN to "Русский"
        )
    }
}
