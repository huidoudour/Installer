package io.github.huidoudour.Installer.ui.theme;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.ColorInt;

/**
 * 主题颜色管理器
 */
public class ThemeColorManager {
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_SEED_COLOR = "seed_color";
    private static final String KEY_USE_DYNAMIC_COLOR = "use_dynamic_color";
    private static final int DEFAULT_COLOR = 0xFF4A672D;

    @ColorInt
    public static int getSeedColor(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_SEED_COLOR, DEFAULT_COLOR);
    }

    public static void saveSeedColor(Context context, int color) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_SEED_COLOR, color)
                .apply();
    }

    public static boolean isUseDynamicColor(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_USE_DYNAMIC_COLOR, true);
    }

    public static void setUseDynamicColor(Context context, boolean use) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_USE_DYNAMIC_COLOR, use)
                .apply();
    }
}
