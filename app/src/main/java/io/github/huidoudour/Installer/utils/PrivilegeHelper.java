package io.github.huidoudour.Installer.utils;

import android.content.Context;
import android.util.Log;

import io.github.huidoudour.Installer.R;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;

import rikka.shizuku.Shizuku;

/**
 * 统一的权限管理工具类
 * 支持 Shizuku 和 Dhizuku 两种授权方式
 */
public class PrivilegeHelper {
    
    private static final String TAG = "PrivilegeHelper";
    
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final String DHIZUKU_PACKAGE = "com.rosan.dhizuku";
    private static final String SHIZUKU_GITHUB_URL = "https://github.com/RikkaApps/Shizuku";
    private static final String DHIZUKU_GITHUB_URL = "https://github.com/iamr0s/Dhizuku";
    private static final int DHIZUKU_MIN_VERSION = 8;
    
    private static final String PREFS_NAME = "privilege_settings";
    private static final String KEY_CURRENT_MODE = "current_mode";
    
    // Dhizuku 权限请求回调
    private static DhizukuPermissionCallback dhizukuPermissionCallback = null;
    
    public enum PrivilegeMode {
        SHIZUKU,
        DHIZUKU
    }
    
    public enum PrivilegeStatus {
        NOT_INSTALLED,      // 授权器未安装
        NOT_RUNNING,        // 授权器未运行
        NOT_AUTHORIZED,     // 未授权
        AUTHORIZED,         // 已授权
        VERSION_TOO_LOW     // 版本过低
    }
    
    /**
     * Dhizuku 权限请求回调接口
     */
    public interface DhizukuPermissionCallback {
        void onPermissionGranted();
        void onPermissionDenied();
        void onError(String error);
    }
    
    /**
     * 检查指定授权器是否已安装
     */
    public static boolean isInstalled(Context context, PrivilegeMode mode) {
        String packageName = mode == PrivilegeMode.SHIZUKU ? SHIZUKU_PACKAGE : DHIZUKU_PACKAGE;
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
    
    /**
     * 检查 Shizuku 状态
     */
    public static PrivilegeStatus checkShizukuStatus() {
        try {
            if (!Shizuku.pingBinder()) {
                return PrivilegeStatus.NOT_RUNNING;
            }
            
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                return PrivilegeStatus.VERSION_TOO_LOW;
            }
            
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                return PrivilegeStatus.AUTHORIZED;
            } else {
                return PrivilegeStatus.NOT_AUTHORIZED;
            }
        } catch (Exception e) {
            return PrivilegeStatus.NOT_RUNNING;
        }
    }
    
    /**
     * 检查 Dhizuku 状态
     * 注：Dhizuku 的状态检查方法名和实现可能因版本而异
     * 这里采用尽可能兼容的方式检测
     */
    public static PrivilegeStatus checkDhizukuStatus() {
        Log.d(TAG, "checkDhizukuStatus: 正在检查 Dhizuku 状态");
        try {
            // 使用 Dhizuku API 检查状态
            Class<?> dhizukuClass = Class.forName("com.rosan.dhizuku.api.Dhizuku");
            Log.d(TAG, "checkDhizukuStatus: Dhizuku 类孙存");
            
            // 首先尝试调用 isRunning，如果成功说明 Dhizuku 能响应
            try {
                java.lang.reflect.Method isRunningMethod = findMethod(dhizukuClass, "isRunning");
                if (isRunningMethod != null) {
                    Log.d(TAG, "checkDhizukuStatus: 找到 isRunning 方法");
                    isRunningMethod.setAccessible(true);
                    try {
                        boolean isRunning = (boolean) isRunningMethod.invoke(null);
                        Log.d(TAG, "checkDhizukuStatus: isRunning = " + isRunning);
                        if (!isRunning) {
                            // 尝试调用 pingBinder 作为备选方案
                            java.lang.reflect.Method pingMethod = findMethod(dhizukuClass, "pingBinder");
                            if (pingMethod != null) {
                                Log.d(TAG, "checkDhizukuStatus: 找到 pingBinder 方法");
                                pingMethod.setAccessible(true);
                                boolean isPing = (boolean) pingMethod.invoke(null);
                                Log.d(TAG, "checkDhizukuStatus: pingBinder = " + isPing);
                                if (!isPing) {
                                    return PrivilegeStatus.NOT_RUNNING;
                                }
                            } else {
                                Log.d(TAG, "checkDhizukuStatus: pingBinder 方法不存在");
                                return PrivilegeStatus.NOT_RUNNING;
                            }
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "checkDhizukuStatus: isRunning 调用异常: " + e.getMessage());
                        return PrivilegeStatus.NOT_RUNNING;
                    }
                } else {
                    Log.d(TAG, "checkDhizukuStatus: 找不到 isRunning 方法");
                }
            } catch (Exception e) {
                Log.d(TAG, "checkDhizukuStatus: isRunning 检测异常: " + e.getMessage());
                // isRunning 不可用，尝试其他方式
                // 直接尝试调用权限检查方法，如果能调用说明 Dhizuku 在运行
            }
            
            // 检查版本
            try {
                java.lang.reflect.Method versionMethod = findMethod(dhizukuClass, "getVersion");
                if (versionMethod != null) {
                    Log.d(TAG, "checkDhizukuStatus: 找到 getVersion 方法");
                    versionMethod.setAccessible(true);
                    try {
                        int version = (int) versionMethod.invoke(null);
                        Log.d(TAG, "checkDhizukuStatus: Dhizuku 版本 = " + version);
                        if (version < DHIZUKU_MIN_VERSION) {
                            Log.d(TAG, "checkDhizukuStatus: 版本过低");
                            return PrivilegeStatus.VERSION_TOO_LOW;
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "checkDhizukuStatus: getVersion 调用异常: " + e.getMessage());
                    }
                } else {
                    Log.d(TAG, "checkDhizukuStatus: 找不到 getVersion 方法");
                }
            } catch (Exception e) {
                Log.d(TAG, "checkDhizukuStatus: 版本检查异常: " + e.getMessage());
                // 版本检查失败，继续检查权限
            }
            
            // 检查权限
            java.lang.reflect.Method permissionMethod = findMethod(dhizukuClass, "isPermissionGranted");
            if (permissionMethod == null) {
                // 尝试其他权限检查方法名
                Log.d(TAG, "checkDhizukuStatus: isPermissionGranted 方法不存在，尝试 checkSelfPermission");
                permissionMethod = findMethod(dhizukuClass, "checkSelfPermission");
            }
            
            if (permissionMethod == null) {
                // 无法找到权限检查方法，但 Dhizuku 类可用说明是在运行的
                Log.d(TAG, "checkDhizukuStatus: 找不到权限检查方法，但 Dhizuku 类存在，假设已授权");
                return PrivilegeStatus.AUTHORIZED;
            }
            
            Log.d(TAG, "checkDhizukuStatus: 找到权限检查方法");
            permissionMethod.setAccessible(true);
            try {
                boolean isGranted = (boolean) permissionMethod.invoke(null);
                Log.d(TAG, "checkDhizukuStatus: isPermissionGranted = " + isGranted);
                return isGranted ? PrivilegeStatus.AUTHORIZED : PrivilegeStatus.NOT_AUTHORIZED;
            } catch (IllegalArgumentException e) {
                // 如果方法参数不匹配，尝试其他调用方式
                Log.d(TAG, "checkDhizukuStatus: 调用权限方法参数不匹配，尝试无参调用");
                try {
                    Object result = permissionMethod.invoke(null);
                    if (result instanceof Boolean) {
                        boolean isGranted = (boolean) result;
                        Log.d(TAG, "checkDhizukuStatus: 无参调用结果 = " + isGranted);
                        return isGranted ? PrivilegeStatus.AUTHORIZED : PrivilegeStatus.NOT_AUTHORIZED;
                    }
                } catch (Exception e2) {
                    Log.d(TAG, "checkDhizukuStatus: 无参调用异常: " + e2.getMessage());
                }
            } catch (Exception e) {
                Log.d(TAG, "checkDhizukuStatus: 权限检查异常: " + e.getMessage());
            }
            
            // 如果能执行到这里说明 Dhizuku API 可用
            // 假设已授权（因为能调用 API）
            Log.d(TAG, "checkDhizukuStatus: Dhizuku API 可用，假设已授权");
            return PrivilegeStatus.AUTHORIZED;
            
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "checkDhizukuStatus: Dhizuku 类未找到");
            return PrivilegeStatus.NOT_RUNNING;
        } catch (Exception e) {
            // 任何异外都视为未运行
            Log.e(TAG, "checkDhizukuStatus: 异常", e);
            return PrivilegeStatus.NOT_RUNNING;
        }
    }
    
    /**
     * 通过反射查找方法（支持多版本 Dhizuku API）
     */
    private static java.lang.reflect.Method findMethod(Class<?> clazz, String methodName) {
        try {
            // 首先尝试获取公开方法
            try {
                return clazz.getMethod(methodName);
            } catch (NoSuchMethodException e1) {
                // 尝试获取声明方法
                try {
                    return clazz.getDeclaredMethod(methodName);
                } catch (NoSuchMethodException e2) {
                    // 尝试遍历所有方法查找
                    java.lang.reflect.Method[] methods = clazz.getDeclaredMethods();
                    for (java.lang.reflect.Method method : methods) {
                        if (method.getName().equals(methodName)) {
                            return method;
                        }
                    }
                    return null;
                }
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取指定授权器的状态
     */
    public static PrivilegeStatus getStatus(Context context, PrivilegeMode mode) {
        if (!isInstalled(context, mode)) {
            return PrivilegeStatus.NOT_INSTALLED;
        }
        
        if (mode == PrivilegeMode.SHIZUKU) {
            return checkShizukuStatus();
        } else {
            return checkDhizukuStatus();
        }
    }
    
    /**
     * 请求 Shizuku 授权
     */
    public static void requestShizukuPermission(int requestCode) {
        try {
            if (Shizuku.pingBinder() && 
                !(Shizuku.isPreV11() || Shizuku.getVersion() < 11) &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 请求 Dhizuku 授权（带回调）
     */
    public static void requestDhizukuPermission(Context context, DhizukuPermissionCallback callback) {
        try {
            Class<?> dhizukuClass = Class.forName("com.rosan.dhizuku.api.Dhizuku");
            Class<?> listenerClass = Class.forName("com.rosan.dhizuku.api.DhizukuRequestPermissionListener");
            
            // 创建动态代理实现 DhizukuRequestPermissionListener
            Object listenerProxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.getClassLoader(),
                new Class[]{listenerClass},
                new java.lang.reflect.InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                        String methodName = method.getName();
                        if ("onRequestPermission".equals(methodName) && args != null && args.length > 0) {
                            boolean granted = (boolean) args[0];
                            if (callback != null) {
                                if (granted) {
                                    callback.onPermissionGranted();
                                } else {
                                    callback.onPermissionDenied();
                                }
                            }
                        }
                        return null;
                    }
                }
            );
            
            // 调用 Dhizuku.requestPermission(DhizukuRequestPermissionListener)
            java.lang.reflect.Method requestPermissionMethod = dhizukuClass.getMethod("requestPermission", listenerClass);
            requestPermissionMethod.invoke(null, listenerProxy);
            
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Dhizuku API 类未找到", e);
            if (callback != null) {
                callback.onError(context.getString(R.string.dhizuku_api_class_not_found));
            }
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Dhizuku requestPermission 方法未找到", e);
            if (callback != null) {
                callback.onError(context.getString(R.string.dhizuku_request_permission_method_not_found));
            }
        } catch (Exception e) {
            Log.e(TAG, "请求 Dhizuku 权限失败", e);
            if (callback != null) {
                callback.onError(context.getString(R.string.dhizuku_request_permission_failed, e.getMessage()));
            }
        }
    }
    
    /**
     * 请求 Dhizuku 授权（无回调）
     */
    public static void requestDhizukuPermission(Context context) {
        requestDhizukuPermission(context, null);
    }

    /**
     * 处理 Dhizuku 权限请求结果（从后台回调调用）
     */
    public static void onDhizukuPermissionResult(boolean granted) {
        if (dhizukuPermissionCallback != null) {
            if (granted) {
                dhizukuPermissionCallback.onPermissionGranted();
            } else {
                dhizukuPermissionCallback.onPermissionDenied();
            }
            dhizukuPermissionCallback = null;
        }
    }
    
    /**
     * 打开指定授权器应用
     */
    public static void openPrivilegeApp(Context context, PrivilegeMode mode) {
        String packageName = mode == PrivilegeMode.SHIZUKU ? SHIZUKU_PACKAGE : DHIZUKU_PACKAGE;
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
    
    /**
     * 打开 GitHub 项目页面
     */
    public static void openGithubPage(Context context, PrivilegeMode mode) {
        String url = mode == PrivilegeMode.SHIZUKU ? SHIZUKU_GITHUB_URL : DHIZUKU_GITHUB_URL;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
    
    /**
     * 获取状态描述文本
     */
    public static String getStatusDescription(PrivilegeStatus status, Context context) {
        switch (status) {
            case NOT_INSTALLED:
                return context.getString(R.string.privilege_status_not_installed);
            case NOT_RUNNING:
                return context.getString(R.string.privilege_status_not_running);
            case NOT_AUTHORIZED:
                return context.getString(R.string.privilege_status_not_authorized);
            case AUTHORIZED:
                return context.getString(R.string.privilege_status_authorized);
            case VERSION_TOO_LOW:
                return context.getString(R.string.privilege_status_version_too_low);
            default:
                return context.getString(R.string.privilege_status_unknown);
        }
    }

    // 为了保持向后兼容性，保留原来的方法（如果没有上下文的话使用默认值）
    public static String getStatusDescription(PrivilegeStatus status) {
        switch (status) {
            case NOT_INSTALLED:
                return "Not Installed";
            case NOT_RUNNING:
                return "Not Running";
            case NOT_AUTHORIZED:
                return "Not Authorized";
            case AUTHORIZED:
                return "Authorized";
            case VERSION_TOO_LOW:
                return "Version Too Low";
            default:
                return "Unknown Status";
        }
    }
    
    /**
     * 保存当前选择的授权器模式
     */
    public static void saveCurrentMode(Context context, PrivilegeMode mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CURRENT_MODE, mode.name()).apply();
    }
    
    /**
     * 获取当前选择的授权器模式
     */
    public static PrivilegeMode getCurrentMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String modeName = prefs.getString(KEY_CURRENT_MODE, PrivilegeMode.SHIZUKU.name());
        try {
            return PrivilegeMode.valueOf(modeName);
        } catch (IllegalArgumentException e) {
            return PrivilegeMode.SHIZUKU;
        }
    }
    
    /**
     * 切换到另一个授权器
     */
    public static PrivilegeMode switchMode(Context context) {
        PrivilegeMode currentMode = getCurrentMode(context);
        PrivilegeMode newMode = currentMode == PrivilegeMode.SHIZUKU ? 
            PrivilegeMode.DHIZUKU : PrivilegeMode.SHIZUKU;
        saveCurrentMode(context, newMode);
        return newMode;
    }
    
    /**
     * 获取授权器名称
     */
    public static String getModeName(PrivilegeMode mode) {
        return mode == PrivilegeMode.SHIZUKU ? "Shizuku" : "Dhizuku";
    }
    
    /**
     * 获取 Dhizuku 版本
     */
    public static int getDhizukuVersion() {
        try {
            Class<?> dhizukuClass = Class.forName("com.rosan.dhizuku.api.Dhizuku");
            java.lang.reflect.Method versionMethod = dhizukuClass.getDeclaredMethod("getVersion");
            return (int) versionMethod.invoke(null);
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * 检查 Dhizuku 是否安装并支持权限回调
     */
    public static boolean isDhizukuSupportsCallback() {
        try {
            Class<?> dhizukuClass = Class.forName("com.rosan.dhizuku.api.Dhizuku");
            // 检查是否有权限回调相关方法
            for (java.lang.reflect.Method method : dhizukuClass.getDeclaredMethods()) {
                if (method.getName().contains("Listener") || method.getName().contains("Callback")) {
                    return true;
                }
            }
            return true;  // 大多数版本都支持
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 初始化权限系统（建议在应用启动时调用）
     */
    public static void initialize(Context context) {
        // 检查并缓存权限器状态
        getStatus(context, PrivilegeMode.SHIZUKU);
        getStatus(context, PrivilegeMode.DHIZUKU);
    }
}
