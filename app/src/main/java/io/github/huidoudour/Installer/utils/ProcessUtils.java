package io.github.huidoudour.Installer.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

/**
 * 进程工具类
 * 参考GKD项目的实现思路，用于检测和管理进程状态
 */
public class ProcessUtils {
    private static final String TAG = "ProcessUtils";
    
    /**
     * 检查当前应用是否在前台运行
     * @param context 上下文
     * @return true 如果应用在前台运行
     */
    public static boolean isAppInForeground(Context context) {
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) return false;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Android 5.0及以上版本
                List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
                if (appProcesses == null) return false;
                
                String packageName = context.getPackageName();
                for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
                    if (appProcess.processName.equals(packageName) &&
                        appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        return true;
                    }
                }
            } else {
                // Android 5.0以下版本
                List<ActivityManager.RunningTaskInfo> taskInfo = activityManager.getRunningTasks(1);
                if (taskInfo != null && !taskInfo.isEmpty()) {
                    ComponentName componentName = taskInfo.get(0).topActivity;
                    if (componentName != null && context.getPackageName().equals(componentName.getPackageName())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查应用是否在前台运行失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 获取当前运行的Activity名称
     * @param activity Activity实例
     * @return Activity名称
     */
    public static String getCurrentActivityName(Activity activity) {
        if (activity == null) return "";
        
        try {
            return activity.getClass().getSimpleName();
        } catch (Exception e) {
            Log.e(TAG, "获取当前Activity名称失败: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 检查是否在特定应用中运行
     * @param context 上下文
     * @param targetPackageName 目标应用包名
     * @return true 如果在目标应用中运行
     */
    public static boolean isInTargetApp(Context context, String targetPackageName) {
        try {
            // 获取当前前台应用包名
            String foregroundApp = getForegroundAppPackageName(context);
            return targetPackageName.equals(foregroundApp);
        } catch (Exception e) {
            Log.e(TAG, "检查是否在特定应用中运行失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取前台应用包名
     * @param context 上下文
     * @return 前台应用包名
     */
    private static String getForegroundAppPackageName(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 使用UsageStatsManager需要特殊权限，这里使用替代方法
                ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager != null) {
                    List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
                    if (processes != null) {
                        for (ActivityManager.RunningAppProcessInfo process : processes) {
                            if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                                return process.processName;
                            }
                        }
                    }
                }
            } else {
                // 读取系统文件获取前台应用信息
                return readForegroundAppFromProc();
            }
        } catch (Exception e) {
            Log.e(TAG, "获取前台应用包名失败: " + e.getMessage());
        }
        
        return "";
    }
    
    /**
     * 从/proc文件系统读取前台应用信息
     * @return 前台应用包名
     */
    private static String readForegroundAppFromProc() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/window_policy"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("foreground")) {
                    // 解析包含foreground的行，提取包名
                    // 这是一个简化的实现，实际实现可能更复杂
                    String[] parts = line.split(" ");
                    if (parts.length > 1) {
                        return parts[1];
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "读取/proc文件失败: " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "关闭文件读取器失败: " + e.getMessage());
                }
            }
        }
        
        return "";
    }
    
    /**
     * 检查系统是否支持高级隐藏功能
     * @return true 如果支持
     */
    public static boolean isAdvancedHidingSupported() {
        // 检查是否为特定厂商定制的系统
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        String brand = Build.BRAND.toLowerCase();
        
        // 一些厂商可能对隐藏功能有更好的支持
        return manufacturer.contains("xiaomi") || 
               manufacturer.contains("huawei") || 
               manufacturer.contains("samsung") || 
               brand.contains("mi") || 
               brand.contains("huawei") || 
               brand.contains("samsung");
    }
}