package io.github.huidoudour.Installer.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * 后台管理器
 * 参考GKD项目的实现思路，提供更灵活的后台隐藏策略
 */
public class BackgroundManager {
    private static final String TAG = "BackgroundManager";
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_BACKGROUND_DISPLAY = "background_display";
    private static final String KEY_HIDE_ON_PAUSE = "hide_on_pause";
    private static final String KEY_HIDE_ON_MINIMIZE = "hide_on_minimize";
    private static final String KEY_HIDE_IN_TARGET_APPS = "hide_in_target_apps";
    
    private WeakReference<Activity> activityRef;
    private SharedPreferences sharedPreferences;
    
    public BackgroundManager(Activity activity) {
        this.activityRef = new WeakReference<>(activity);
        if (activity != null) {
            this.sharedPreferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }
    
    /**
     * 检查是否应该隐藏应用
     * @return true 如果应该隐藏应用
     */
    public boolean shouldHideApp() {
        if (sharedPreferences == null) return false;
        
        // 主要的后台显示设置
        boolean isBackgroundDisplayEnabled = sharedPreferences.getBoolean(KEY_BACKGROUND_DISPLAY, true);
        if (!isBackgroundDisplayEnabled) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 在应用暂停时处理隐藏逻辑
     */
    public void handleOnPause() {
        if (sharedPreferences == null) return;
        
        boolean hideOnPause = sharedPreferences.getBoolean(KEY_HIDE_ON_PAUSE, false);
        if (hideOnPause) {
            hideAppFromRecents();
        }
    }
    
    /**
     * 在应用恢复时处理显示逻辑
     */
    public void handleOnResume() {
        if (sharedPreferences == null) return;
        
        boolean hideOnPause = sharedPreferences.getBoolean(KEY_HIDE_ON_PAUSE, false);
        if (hideOnPause) {
            showAppInRecents();
        }
    }
    
    /**
     * 在应用最小化时处理隐藏逻辑
     */
    public void handleOnMinimize() {
        if (sharedPreferences == null) return;
        
        boolean hideOnMinimize = sharedPreferences.getBoolean(KEY_HIDE_ON_MINIMIZE, false);
        if (hideOnMinimize) {
            hideAppFromRecents();
        }
    }
    
    /**
     * 在特定应用中运行时处理隐藏逻辑
     * @param targetPackageName 目标应用包名
     */
    public void handleInTargetApp(String targetPackageName) {
        if (sharedPreferences == null) return;
        
        boolean hideInTargetApps = sharedPreferences.getBoolean(KEY_HIDE_IN_TARGET_APPS, false);
        if (hideInTargetApps) {
            // 检查是否在目标应用中
            // 这里可以添加具体的检查逻辑
            hideAppFromRecents();
        }
    }
    
    /**
     * 从最近任务列表中隐藏应用
     */
    private void hideAppFromRecents() {
        Activity activity = activityRef.get();
        if (activity == null) return;
        
        try {
            // 使用系统API隐藏应用
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activity.moveTaskToBack(true);
                Log.d(TAG, "应用已隐藏到后台");
            }
        } catch (Exception e) {
            Log.e(TAG, "隐藏应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 在最近任务列表中显示应用
     */
    private void showAppInRecents() {
        // 注意：Android系统限制了应用主动将自己添加到最近任务列表
        // 这里主要是为了恢复应用的正常状态
        Log.d(TAG, "应用恢复正常显示状态");
    }
    
    /**
     * 设置应用是否在最近任务列表中显示
     * @param show true显示，false隐藏
     */
    public void setShowInRecents(boolean show) {
        if (show) {
            showAppInRecents();
        } else {
            hideAppFromRecents();
        }
    }
    
    /**
     * 获取当前的后台显示设置
     * @return true 如果允许在最近任务列表中显示
     */
    public boolean isBackgroundDisplayEnabled() {
        if (sharedPreferences == null) return true;
        return sharedPreferences.getBoolean(KEY_BACKGROUND_DISPLAY, true);
    }
    
    /**
     * 获取暂停时隐藏设置
     * @return true 如果启用了暂停时隐藏
     */
    public boolean isHideOnPauseEnabled() {
        if (sharedPreferences == null) return false;
        return sharedPreferences.getBoolean(KEY_HIDE_ON_PAUSE, false);
    }
    
    /**
     * 获取最小化时隐藏设置
     * @return true 如果启用了最小化时隐藏
     */
    public boolean isHideOnMinimizeEnabled() {
        if (sharedPreferences == null) return false;
        return sharedPreferences.getBoolean(KEY_HIDE_ON_MINIMIZE, false);
    }
    
    /**
     * 获取在特定应用中隐藏设置
     * @return true 如果启用了在特定应用中隐藏
     */
    public boolean isHideInTargetAppsEnabled() {
        if (sharedPreferences == null) return false;
        return sharedPreferences.getBoolean(KEY_HIDE_IN_TARGET_APPS, false);
    }
}