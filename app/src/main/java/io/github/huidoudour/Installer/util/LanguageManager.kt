package io.github.huidoudour.Installer.util

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 语言管理工具类
 * 使用 AppCompatDelegate.setApplicationLocales() 实现可靠的语言切换
 */
object LanguageManager {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val TAG = "LanguageManager"

    const val LANGUAGE_FOLLOW_SYSTEM = "system"
    const val LANGUAGE_SIMPLIFIED_CHINESE = "zh-rCN"
    const val LANGUAGE_TRADITIONAL_CHINESE = "zh-rTW"
    const val LANGUAGE_HONGKONG_CHINESE = "zh-rHK"
    const val LANGUAGE_ENGLISH = "en-rUS"
    const val LANGUAGE_JAPANESE = "ja-rJP"
    const val LANGUAGE_RUSSIAN = "ru-rRU"

    /**
     * 应用用户选择的语言偏好（在 Activity.onCreate 的 super.onCreate 之前调用）
     */
    fun applyUserLanguagePreference(context: Context) {
        try {
            val language = getUserLanguage(context)
            applyLanguage(language)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply language preference: ${e.message}")
        }
    }

    /**
     * 应用指定语言
     * 当提供 Activity 时，直接配置 locale + 手动重建 + 淡入淡出过渡动画
     * 否则回退到 AppCompatDelegate.setApplicationLocales() 自动重建
     * @param activity 可选，用于手动重建并设置过渡动画
     */
    fun applyLanguage(languageCode: String, activity: Activity? = null) {
        try {
            val localeList = when (languageCode) {
                LANGUAGE_FOLLOW_SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                else -> {
                    val locale = languageCodeToLocale(languageCode)
                    LocaleListCompat.create(locale)
                }
            }

            if (activity != null) {
                // 手动重建路径：直接应用 locale，避免 AppCompat 自动重建导致黑屏
                val locale = if (localeList.isEmpty) {
                    java.util.Locale.getDefault()
                } else {
                    localeList[0]!!
                }

                // 更新应用全局 Context 的 locale 配置
                @Suppress("DEPRECATION")
                val appContext = activity.applicationContext
                @Suppress("DEPRECATION")
                val appConfig = appContext.resources.configuration
                @Suppress("DEPRECATION")
                appConfig.setLocale(locale)
                @Suppress("DEPRECATION")
                appContext.resources.updateConfiguration(appConfig, appContext.resources.displayMetrics)

                // 更新当前 Activity 的 base context locale
                @Suppress("DEPRECATION")
                val baseConfig = activity.baseContext.resources.configuration
                @Suppress("DEPRECATION")
                baseConfig.setLocale(locale)
                @Suppress("DEPRECATION")
                activity.baseContext.resources.updateConfiguration(baseConfig, activity.baseContext.resources.displayMetrics)

                // 手动重建 Activity，带淡入淡出动画
                val intent = Intent(activity, activity::class.java)
                @Suppress("DEPRECATION")
                val options = ActivityOptions.makeCustomAnimation(
                    activity,
                    io.github.huidoudour.Installer.R.anim.fade_in,
                    io.github.huidoudour.Installer.R.anim.fade_out
                ).toBundle()
                activity.startActivity(intent, options)
                activity.finish()
            } else {
                // 无 Activity 时回退到 AppCompat 自动重建
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply language: ${e.message}")
        }
    }

    /**
     * 将语言代码转换为 Locale 对象
     */
    private fun languageCodeToLocale(languageCode: String): Locale {
        return when (languageCode) {
            LANGUAGE_SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
            LANGUAGE_TRADITIONAL_CHINESE -> Locale.TRADITIONAL_CHINESE
            LANGUAGE_HONGKONG_CHINESE -> Locale.Builder().setLanguage("zh").setRegion("HK").build()
            LANGUAGE_ENGLISH -> Locale.Builder().setLanguage("en").setRegion("US").build()
            LANGUAGE_JAPANESE -> Locale.Builder().setLanguage("ja").setRegion("JP").build()
            LANGUAGE_RUSSIAN -> Locale.Builder().setLanguage("ru").setRegion("RU").build()
            else -> Locale.getDefault()
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
     * 获取当前应用的语言代码
     */
    fun getCurrentLanguageCode(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return LANGUAGE_FOLLOW_SYSTEM
        val locale = locales[0] ?: return LANGUAGE_FOLLOW_SYSTEM
        val language = locale.language
        val region = locale.country
        return if (region.isNotEmpty()) "${language}-r$region" else language
    }

    /**
     * 获取语言显示名称
     */
    fun getLanguageDisplayName(context: Context, languageCode: String): String {
        return when (languageCode) {
            LANGUAGE_FOLLOW_SYSTEM -> context.getString(io.github.huidoudour.Installer.R.string.follow_system)
            LANGUAGE_SIMPLIFIED_CHINESE -> context.getString(io.github.huidoudour.Installer.R.string.simplified_chinese)
            LANGUAGE_TRADITIONAL_CHINESE -> "繁體中文"
            LANGUAGE_HONGKONG_CHINESE -> context.getString(io.github.huidoudour.Installer.R.string.lang_name_meow)
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
            LANGUAGE_HONGKONG_CHINESE to "喵語中文",
            LANGUAGE_ENGLISH to "English",
            LANGUAGE_JAPANESE to "日本語",
            LANGUAGE_RUSSIAN to "Русский"
        )
    }
}
