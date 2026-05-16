package io.github.huidoudour.Installer.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * 主题管理工具类
 * 用于管理应用的主题设置(跟随系统/浅色/深色)
 */
public class ThemeManager {
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_APP_THEME = "app_theme";
    private static final String TAG = "ThemeManager";
    
    // 主题模式常量
    public static final int THEME_FOLLOW_SYSTEM = -1;  // 跟随系统
    public static final int THEME_LIGHT = 1;            // 浅色主题
    public static final int THEME_DARK = 2;             // 深色主题
    
    /**
     * 应用用户选择的主题偏好
     * @param context 上下文
     */
    public static void applyUserThemePreference(Context context) {
        try {
            int themeMode = getUserTheme(context);
            applyTheme(themeMode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply theme preference: " + e.getMessage());
        }
    }
    
    /**
     * 应用主题模式
     * @param themeMode 主题模式
     */
    public static void applyTheme(int themeMode) {
        switch (themeMode) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_FOLLOW_SYSTEM:
            default:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
                }
                break;
        }
    }
    
    /**
     * 保存用户选择的主题
     * @param context 上下文
     * @param themeMode 主题模式 (THEME_FOLLOW_SYSTEM, THEME_LIGHT, THEME_DARK)
     */
    public static void saveUserTheme(Context context, int themeMode) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putInt(KEY_APP_THEME, themeMode).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save theme setting: " + e.getMessage());
        }
    }
    
    /**
     * 获取用户选择的主题
     * @param context 上下文
     * @return 主题模式,默认为跟随系统
     */
    public static int getUserTheme(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getInt(KEY_APP_THEME, THEME_FOLLOW_SYSTEM);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get theme setting: " + e.getMessage());
            return THEME_FOLLOW_SYSTEM;
        }
    }
    
    /**
     * 获取主题显示名称
     * @param context 上下文
     * @param themeMode 主题模式
     * @return 主题显示名称
     */
    public static String getThemeDisplayName(Context context, int themeMode) {
        switch (themeMode) {
            case THEME_LIGHT:
                return context.getString(io.github.huidoudour.Installer.R.string.light_theme);
            case THEME_DARK:
                return context.getString(io.github.huidoudour.Installer.R.string.dark_theme);
            case THEME_FOLLOW_SYSTEM:
            default:
                return context.getString(io.github.huidoudour.Installer.R.string.follow_system);
        }
    }
}
