package dev.huidoudour.installer.util

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.huidoudour.installer.R
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
     * API 33+ 直接跳过：per-app locale 由系统 LocaleManager 自动应用与持久化，
     * 手动应用本地保存的偏好会覆盖用户在系统设置里改的语言。
     */
    fun applyUserLanguagePreference(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return
        }
        try {
            val language = getUserLanguage(context)
            applyLanguage(language)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply language preference: ${e.message}")
        }
    }

    /**
     * 应用指定语言
     * API 33+：统一走 AppCompatDelegate.setApplicationLocales()，内部委托系统
     * LocaleManager，由系统持久化并同步到系统设置的“应用语言”入口
     * 低于 API 33：当提供 Activity 时，直接配置 locale + 手动重建 + 淡入淡出过渡动画，
     * 否则回退到 AppCompatDelegate.setApplicationLocales() 自动重建
     * @param activity 可选，用于手动重建并设置过渡动画（仅低版本生效）
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

            // API 33+ 必须经由 AppCompatDelegate 同步给系统，
            // 手动 updateConfiguration 不会写入系统，导致设置页显示与实际不一致
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AppCompatDelegate.setApplicationLocales(localeList)
                return
            }

            if (activity != null) {
                // 手动重建路径：直接应用 locale，避免 AppCompat 自动重建导致黑屏
                val locale = if (localeList.isEmpty) {
                    Locale.getDefault()
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
                    R.anim.fade_in,
                    R.anim.fade_out
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
     * API 33+ 以系统 per-app locale 为准（用户在系统设置里改过后，app 内显示同步），
     * 低于 API 33 读本地保存的偏好
     */
    fun getUserLanguage(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = AppCompatDelegate.getApplicationLocales()
            return if (locales.isEmpty) {
                LANGUAGE_FOLLOW_SYSTEM
            } else {
                localeToLanguageCode(locales[0])
            }
        }
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_APP_LANGUAGE, LANGUAGE_FOLLOW_SYSTEM) ?: LANGUAGE_FOLLOW_SYSTEM
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get language setting: ${e.message}")
            LANGUAGE_FOLLOW_SYSTEM
        }
    }

    /**
     * 将系统 Locale 反解析为 app 内使用的语言代码（languageCodeToLocale 的逆映射）
     */
    private fun localeToLanguageCode(locale: Locale?): String {
        return when (locale?.language) {
            "zh" -> when (locale.country) {
                "TW" -> LANGUAGE_TRADITIONAL_CHINESE
                "HK" -> LANGUAGE_HONGKONG_CHINESE
                else -> LANGUAGE_SIMPLIFIED_CHINESE
            }
            "en" -> LANGUAGE_ENGLISH
            "ja" -> LANGUAGE_JAPANESE
            "ru" -> LANGUAGE_RUSSIAN
            else -> LANGUAGE_FOLLOW_SYSTEM
        }
    }

    /**
     * 获取语言显示名称
     */
    fun getLanguageDisplayName(context: Context, languageCode: String): String {
        return when (languageCode) {
            LANGUAGE_FOLLOW_SYSTEM -> context.getString(R.string.follow_system)
            LANGUAGE_SIMPLIFIED_CHINESE -> context.getString(R.string.simplified_chinese)
            LANGUAGE_TRADITIONAL_CHINESE -> "繁體中文"
            LANGUAGE_HONGKONG_CHINESE -> context.getString(R.string.lang_name_meow)
            LANGUAGE_ENGLISH -> "English"
            LANGUAGE_JAPANESE -> "日本語"
            LANGUAGE_RUSSIAN -> "Русский"
            else -> context.getString(R.string.follow_system)
        }
    }

}
