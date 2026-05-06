package io.github.huidoudour.Installer.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import java.util.Locale;

import io.github.huidoudour.Installer.R;

/**
 * 语言管理工具类
 * 用于管理应用的语言设置
 */
public class LanguageManager {
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_APP_LANGUAGE = "app_language";
    private static final String KEY_SYSTEM_DEFAULT_LANGUAGE = "system_default_language";
    private static final String KEY_SYSTEM_DEFAULT_COUNTRY = "system_default_country";
    private static final String TAG = "LanguageManager";

    /**
     * 保存系统原始语言（在应用首次启动时调用）
     * @param context 上下文
     */
    public static void saveSystemDefaultLanguage(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedSystemLang = prefs.getString(KEY_SYSTEM_DEFAULT_LANGUAGE, null);
            
            // 只在首次保存（如果没有保存过系统语言）
            if (savedSystemLang == null) {
                Locale systemLocale = Locale.getDefault();
                prefs.edit()
                    .putString(KEY_SYSTEM_DEFAULT_LANGUAGE, systemLocale.getLanguage())
                    .putString(KEY_SYSTEM_DEFAULT_COUNTRY, systemLocale.getCountry())
                    .apply();
                Log.d(TAG, "已保存系统原始语言: " + systemLocale.getLanguage() + "_" + systemLocale.getCountry());
            } else {
                Log.d(TAG, "系统原始语言已保存: " + savedSystemLang + "_" + prefs.getString(KEY_SYSTEM_DEFAULT_COUNTRY, ""));
            }
        } catch (Exception e) {
            Log.e(TAG, "保存系统语言失败: " + e.getMessage());
        }
    }

    /**
     * 获取保存的系统原始语言
     * @param context 上下文
     * @return 系统原始语言，如果没有则返回当前 Locale.getDefault()
     */
    public static Locale getSavedSystemLocale(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String lang = prefs.getString(KEY_SYSTEM_DEFAULT_LANGUAGE, null);
            String country = prefs.getString(KEY_SYSTEM_DEFAULT_COUNTRY, "");
            
            if (lang != null) {
                Locale savedLocale = country.isEmpty() ? new Locale(lang) : new Locale(lang, country);
                Log.d(TAG, "获取保存的系统语言: " + lang + "_" + country);
                return savedLocale;
            }
        } catch (Exception e) {
            Log.e(TAG, "获取系统语言失败: " + e.getMessage());
        }
        return Locale.getDefault();
    }
    
    /**
     * 应用用户选择的语言偏好
     * @param context 上下文
     */
    public static void applyUserLanguagePreference(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String languageCode = prefs.getString(KEY_APP_LANGUAGE, "system");
            
            Log.d(TAG, "applyUserLanguagePreference: languageCode=" + languageCode);
            
            Locale locale;
            if ("system".equals(languageCode)) {
                // 跟随系统：使用保存的系统原始语言（而不是可能被修改的 Locale.getDefault()）
                Log.d(TAG, "跟随系统模式，恢复系统原始语言");
                Resources resources = context.getResources();
                Configuration config = resources.getConfiguration();
                
                // 获取保存的系统原始语言
                Locale systemLocale = getSavedSystemLocale(context);
                Log.d(TAG, "系统原始语言: " + systemLocale.getLanguage() + "_" + systemLocale.getCountry());
                
                // 恢复系统原始语言
                Locale.setDefault(systemLocale);
                config.setLocale(null); // null 表示使用系统默认
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    config.setLocales(new android.os.LocaleList(systemLocale));
                }
                resources.updateConfiguration(config, resources.getDisplayMetrics());
                
                Log.d(TAG, "已恢复系统原始语言配置");
                return;
            }
            
            switch (languageCode) {
                case "en":
                    locale = Locale.ENGLISH;
                    break;
                case "zh-TW":
                    locale = Locale.TRADITIONAL_CHINESE;
                    break;
                case "ru":
                    locale = new Locale("ru");
                    break;
                case "ja":
                    locale = Locale.JAPANESE;
                    break;
                case "zh-HK":
                    // 喵语言 - 使用香港中文地区
                    locale = new Locale("zh", "HK");
                    break;
                case "zh":
                default:
                    locale = Locale.SIMPLIFIED_CHINESE;
                    break;
            }
            
            Locale.setDefault(locale);
            
            Resources resources = context.getResources();
            Configuration config = resources.getConfiguration();
            config.setLocale(locale);
            resources.updateConfiguration(config, resources.getDisplayMetrics());
        } catch (Exception e) {
            Log.e(TAG, context.getString(R.string.apply_language_preference_failed_simple, e.getMessage()));
        }
    }
    
    /**
     * 保存用户选择的语言
     * @param context 上下文
     * @param languageCode 语言代码 ("system", "zh", "zh-TW", "zh-HK", "en", "ru", "ja")
     */
    public static void saveUserLanguage(Context context, String languageCode) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_APP_LANGUAGE, languageCode).apply();
        } catch (Exception e) {
            Log.e(TAG, context.getString(R.string.save_language_setting_failed, e.getMessage()));
        }
    }
    
    /**
     * 获取用户选择的语言
     * @param context 上下文
     * @return 语言代码 ("system", "zh", "zh-TW", "zh-HK", "en", "ru", "ja")
     */
    public static String getUserLanguage(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getString(KEY_APP_LANGUAGE, "system");
        } catch (Exception e) {
            Log.e(TAG, context.getString(R.string.get_language_setting_failed, e.getMessage()));
            return "system";
        }
    }
    
    /**
     * 获取语言显示名称
     * @param context 上下文
     * @param languageCode 语言代码
     * @return 语言显示名称
     */
    public static String getLanguageDisplayName(Context context, String languageCode) {
        if ("system".equals(languageCode)) {
            return context.getString(R.string.follow_system);
        }
        
        switch (languageCode) {
            case "en":
                return "English";
            case "zh-TW":
                return context.getString(R.string.traditional_chinese);
            case "ru":
                return context.getString(R.string.russian);
            case "ja":
                return context.getString(R.string.japanese);
            case "zh-HK":
                return context.getString(R.string.meow_language);
            case "zh":
            default:
                return context.getString(R.string.simplified_chinese);
        }
    }
}
