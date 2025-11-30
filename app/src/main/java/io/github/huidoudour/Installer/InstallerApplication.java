package io.github.huidoudour.Installer;

import android.app.Application;
import android.util.Log;

import io.github.huidoudour.Installer.utils.LanguageManager;

/**
 * 自定义Application类
 * 用于全局应用语言设置
 */
public class InstallerApplication extends Application {
    private static final String TAG = "InstallerApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 应用用户选择的语言设置
        try {
            LanguageManager.applyUserLanguagePreference(this);
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.apply_language_preference_failed, e.getMessage()));
        }
    }
}