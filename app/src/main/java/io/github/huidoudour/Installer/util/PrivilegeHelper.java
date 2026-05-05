package io.github.huidoudour.Installer.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.rosan.dhizuku.api.Dhizuku;

import rikka.shizuku.Shizuku;

/**
 * 统一的权限管理工具类
 * 支持 Shizuku 授权方式
 */
public class PrivilegeHelper {
    
    private static final String TAG = "PrivilegeHelper";
    
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final String SHIZUKU_GITHUB_URL = "https://github.com/RikkaApps/Shizuku";
    
    private static final String DHIZUKU_PACKAGE = "com.rosan.dhizuku";
    private static final String DHIZUKU_GITHUB_URL = "https://github.com/iamr0s/Dhizuku";
    
    private static final String PREFS_NAME = "privilege_settings";
    private static final String KEY_CURRENT_MODE = "current_mode";



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
     * 检查指定授权器是否已安装
     */
    public static boolean isInstalled(Context context, PrivilegeMode mode) {
        String packageName;
        switch (mode) {
            case SHIZUKU:
                packageName = SHIZUKU_PACKAGE;
                break;
            case DHIZUKU:
                packageName = DHIZUKU_PACKAGE;
                break;
            default:
                packageName = SHIZUKU_PACKAGE;
                break;
        }
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
     * 注意：必须传入 context 参数调用 Dhizuku.init()，
     * 无参版本依赖 ActivityThread.currentActivityThread() 可能在某些情况下失败
     */
    public static PrivilegeStatus checkDhizukuStatus(Context context) {
        try {
            // 使用带 context 的 init 方法，避免无参 init 获取不到正确 context
            if (!Dhizuku.init(context.getApplicationContext())) {
                return PrivilegeStatus.NOT_RUNNING;
            }
            
            // 检查权限状态
            if (Dhizuku.isPermissionGranted()) {
                return PrivilegeStatus.AUTHORIZED;
            } else {
                return PrivilegeStatus.NOT_AUTHORIZED;
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "checkDhizukuStatus error: " + e.getMessage());
            return PrivilegeStatus.NOT_RUNNING;
        }
    }
    
    /**
     * 检查 Dhizuku 状态（无 context 版本，兼容旧调用）
     * @deprecated 使用 {@link #checkDhizukuStatus(Context)} 代替
     */
    @Deprecated
    public static PrivilegeStatus checkDhizukuStatus() {
        try {
            if (!Dhizuku.init()) {
                return PrivilegeStatus.NOT_RUNNING;
            }
            
            if (Dhizuku.isPermissionGranted()) {
                return PrivilegeStatus.AUTHORIZED;
            } else {
                return PrivilegeStatus.NOT_AUTHORIZED;
            }
        } catch (Exception e) {
            return PrivilegeStatus.NOT_RUNNING;
        }
    }

    

    

    /**
     * 获取指定授权器的状态
     */
    public static PrivilegeStatus getStatus(Context context, PrivilegeMode mode) {
        if (!isInstalled(context, mode)) {
            return PrivilegeStatus.NOT_INSTALLED;
        }
        
        switch (mode) {
            case SHIZUKU:
                return checkShizukuStatus();
            case DHIZUKU:
                return checkDhizukuStatus(context);
            default:
                return checkShizukuStatus();
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
     * 请求 Dhizuku 授权
     * 必须传入 context，确保 Dhizuku.init() 能正确初始化
     */
    public static void requestDhizukuPermission(Context context) {
        try {
            // 先确保初始化成功
            if (!Dhizuku.init(context.getApplicationContext())) {
                android.util.Log.e(TAG, "Dhizuku init failed when requesting permission");
                return;
            }
            
            if (!Dhizuku.isPermissionGranted()) {
                // 使用 Dhizuku API 请求权限，会弹出授权对话框
                Dhizuku.requestPermission(new com.rosan.dhizuku.api.DhizukuRequestPermissionListener() {
                    @Override
                    public void onRequestPermission(int grantResult) {
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            android.util.Log.i(TAG, "Dhizuku permission granted");
                        } else {
                            android.util.Log.w(TAG, "Dhizuku permission denied: " + grantResult);
                        }
                    }
                });
            } else {
                android.util.Log.i(TAG, "Dhizuku permission already granted");
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error requesting Dhizuku permission: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 请求 Dhizuku 授权（无 context 版本，兼容旧调用）
     * @deprecated 使用 {@link #requestDhizukuPermission(Context)} 代替
     */
    @Deprecated
    public static void requestDhizukuPermission() {
        try {
            if (!Dhizuku.isPermissionGranted()) {
                Dhizuku.requestPermission(new com.rosan.dhizuku.api.DhizukuRequestPermissionListener() {
                    @Override
                    public void onRequestPermission(int grantResult) {
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            android.util.Log.i(TAG, "Dhizuku permission granted");
                        } else {
                            android.util.Log.w(TAG, "Dhizuku permission denied: " + grantResult);
                        }
                    }
                });
            } else {
                android.util.Log.i(TAG, "Dhizuku permission already granted");
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error requesting Dhizuku permission: " + e.getMessage());
            e.printStackTrace();
        }
    }

    

    

    
    /**
     * 打开指定授权器应用
     */
    public static void openPrivilegeApp(Context context, PrivilegeMode mode) {
        String packageName;
        switch (mode) {
            case SHIZUKU:
                packageName = SHIZUKU_PACKAGE;
                break;
            case DHIZUKU:
                packageName = DHIZUKU_PACKAGE;
                break;
            default:
                packageName = SHIZUKU_PACKAGE;
                break;
        }
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
        String url;
        switch (mode) {
            case SHIZUKU:
                url = SHIZUKU_GITHUB_URL;
                break;
            case DHIZUKU:
                url = DHIZUKU_GITHUB_URL;
                break;
            default:
                url = SHIZUKU_GITHUB_URL;
                break;
        }
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
        PrivilegeMode newMode;
        
        // 在 Shizuku 和 Dhizuku 之间切换
        if (currentMode == PrivilegeMode.SHIZUKU) {
            newMode = PrivilegeMode.DHIZUKU;
        } else {
            newMode = PrivilegeMode.SHIZUKU;
        }
        
        saveCurrentMode(context, newMode);
        return newMode;
    }
    
    /**
     * 获取授权器名称
     */
    public static String getModeName(PrivilegeMode mode) {
        switch (mode) {
            case SHIZUKU:
                return "Shizuku";
            case DHIZUKU:
                return "Dhizuku";
            default:
                return "Shizuku";
        }
    }
    

    

    
    /**
     * 初始化权限系统（建议在应用启动时调用）
     */
    public static void initialize(Context context) {
        // 检查并缓存权限器状态
        PrivilegeMode currentMode = getCurrentMode(context);
        getStatus(context, currentMode);
    }
}
