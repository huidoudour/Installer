package io.github.huidoudour.Installer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import java.util.Locale;

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
            String languageCode = prefs.getString(KEY_APP_LANGUAGE, Locale.getDefault().getLanguage());
            
            Locale locale;
            if ("en".equals(languageCode)) {
                locale = Locale.ENGLISH;
            } else {
                locale = Locale.SIMPLIFIED_CHINESE;
            }
            
            Locale.setDefault(locale);
            
            Resources resources = context.getResources();
            Configuration config = resources.getConfiguration();
            config.setLocale(locale);
            resources.updateConfiguration(config, resources.getDisplayMetrics());
        } catch (Exception e) {
            Log.e(TAG, "应用语言偏好失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存用户选择的语言
     * @param context 上下文
     * @param languageCode 语言代码 ("zh" 或 "en")
     */
    public static void saveUserLanguage(Context context, String languageCode) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_APP_LANGUAGE, languageCode).apply();
        } catch (Exception e) {
            Log.e(TAG, "保存语言设置失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取用户选择的语言
     * @param context 上下文
     * @return 语言代码 ("zh" 或 "en")
     */
    public static String getUserLanguage(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getString(KEY_APP_LANGUAGE, Locale.getDefault().getLanguage());
        } catch (Exception e) {
            Log.e(TAG, "获取语言设置失败: " + e.getMessage());
            return Locale.getDefault().getLanguage();
        }
    }
    
    /**
     * 获取语言显示名称
     * @param languageCode 语言代码
     * @return 语言显示名称
     */
    public static String getLanguageDisplayName(String languageCode) {
        if ("en".equals(languageCode)) {
            return "English";
        } else {
            return "简体中文";
        }
    }
}