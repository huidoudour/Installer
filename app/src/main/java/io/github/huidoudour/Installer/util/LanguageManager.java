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
    private static final String TAG = "LanguageManager";

    /**
     * 应用用户选择的语言偏好
     * @param context 上下文
     */
    public static void applyUserLanguagePreference(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String languageCode = prefs.getString(KEY_APP_LANGUAGE, "system");
            
            Locale locale;
            if ("system".equals(languageCode)) {
                // 跟随系统，不设置特定locale
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
     * @param languageCode 语言代码 ("system", "zh", "zh-TW", "en", "ru", "ja")
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
     * @return 语言代码 ("system", "zh", "zh-TW", "en", "ru", "ja")
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
            case "zh":
            default:
                return context.getString(R.string.simplified_chinese);
        }
    }
}
