package io.github.huidoudour.Installer;

import android.app.Application;
import android.util.Log;

import io.github.huidoudour.Installer.utils.LanguageManager;
import io.github.huidoudour.Installer.utils.NotificationHelper;
import io.github.huidoudour.Installer.R;

/**
 * 自定义Application类
 * 用于全局应用语言设置
 */
public class InstallerApplication extends Application {
    private static final String TAG = "InstallerApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 初始化 Dhizuku（如果已安装）
        try {
            Class<?> dhizukuClass = Class.forName("com.rosan.dhizuku.api.Dhizuku");
            
            // 查找 init 方法，尝试多种方法签名
            java.lang.reflect.Method initMethod = null;
            
            // 尝试1: init(Application)
            try {
                initMethod = dhizukuClass.getMethod("init", Application.class);
            } catch (NoSuchMethodException e1) {
                // 尝试2: init(Context)
                try {
                    initMethod = dhizukuClass.getMethod("init", android.content.Context.class);
                } catch (NoSuchMethodException e2) {
                    // 尝试3: 遍历所有 init 方法
                    for (java.lang.reflect.Method method : dhizukuClass.getDeclaredMethods()) {
                        if (method.getName().equals("init")) {
                            Class<?>[] paramTypes = method.getParameterTypes();
                            if (paramTypes.length == 1) {
                                initMethod = method;
                                Log.d(TAG, "找到 init 方法，参数类型: " + paramTypes[0].getName());
                                break;
                            }
                        }
                    }
                }
            }
            
            if (initMethod != null) {
                initMethod.setAccessible(true);
                initMethod.invoke(null, this);
                Log.d(TAG, "Dhizuku 初始化成功");
            } else {
                Log.w(TAG, "找不到 Dhizuku init 方法");
            }
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "Dhizuku 未安装，跳过初始化");
        } catch (Exception e) {
            Log.e(TAG, "Dhizuku 初始化失败: " + e.getMessage(), e);
        }
        
        // 应用用户选择的语言设置
        try {
            LanguageManager.applyUserLanguagePreference(this);
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.apply_language_preference_failed, e.getMessage()));
        }
        
        // 初始化通知渠道
        try {
            NotificationHelper.createNotificationChannels(this);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create notification channels: " + e.getMessage());
        }
    }
}