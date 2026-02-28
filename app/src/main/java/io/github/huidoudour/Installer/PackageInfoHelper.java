package io.github.huidoudour.Installer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 增强版包信息检查工具类
 * 专门用于解决Android 11+包可见性限制和权限问题
 */
public class PackageInfoHelper {
    private static final String TAG = "PackageInfoHelper";
    
    /**
     * 获取已安装应用的详细信息
     * @param context 上下文
     * @param packageName 包名
     * @return PackageInfo对象，如果未安装则返回null
     */
    public static PackageInfo getInstalledAppInfo(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) {
            Log.w(TAG, "无效的参数: context=" + context + ", packageName=" + packageName);
            return null;
        }
        
        PackageManager pm = context.getPackageManager();
        
        try {
            // 方法1: 直接查询（适用于有明确权限的情况）
            PackageInfo info = pm.getPackageInfo(packageName, 
                PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_PERMISSIONS);
            Log.d(TAG, "成功获取包信息: " + packageName);
            return info;
            
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "应用未安装: " + packageName);
            return null;
        } catch (SecurityException e) {
            Log.w(TAG, "权限不足，尝试备用方案: " + packageName + ", 错误: " + e.getMessage());
            // 权限不足，尝试备用方案
            return getInstalledAppInfoFallback(context, packageName);
        } catch (Exception e) {
            Log.e(TAG, "获取包信息时发生未知错误: " + packageName, e);
            return null;
        }
    }
    
    /**
     * 备用方案：通过查询所有已安装包来获取信息
     * @param context 上下文
     * @param packageName 包名
     * @return PackageInfo对象
     */
    private static PackageInfo getInstalledAppInfoFallback(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        
        try {
            // 方法2: 查询所有已安装包（需要QUERY_ALL_PACKAGES权限）
            List<PackageInfo> packages = pm.getInstalledPackages(
                PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_PERMISSIONS);
            
            for (PackageInfo pkg : packages) {
                if (packageName.equals(pkg.packageName)) {
                    Log.d(TAG, "通过备用方案找到包: " + packageName);
                    return pkg;
                }
            }
            
            Log.d(TAG, "备用方案也未找到包: " + packageName);
            return null;
            
        } catch (SecurityException e) {
            Log.e(TAG, "备用方案权限不足: " + packageName, e);
            // 如果连备用方案都失败，尝试最基本的查询
            return getMinimalPackageInfo(context, packageName);
        } catch (Exception e) {
            Log.e(TAG, "备用方案执行失败: " + packageName, e);
            return null;
        }
    }
    
    /**
     * 最简方案：只获取最基本的应用信息
     * @param context 上下文
     * @param packageName 包名
     * @return PackageInfo对象
     */
    private static PackageInfo getMinimalPackageInfo(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        
        try {
            // 方法3: 最基本的查询，不带任何标志
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            Log.d(TAG, "通过最简方案获取到包信息: " + packageName);
            return info;
        } catch (Exception e) {
            Log.e(TAG, "所有方案都失败了: " + packageName, e);
            return null;
        }
    }
    
    /**
     * 比较两个版本号
     * @param version1 版本号1
     * @param version2 版本号2
     * @return 1 if version1 > version2, -1 if version1 < version2, 0 if equal
     */
    public static int compareVersions(String version1, String version2) {
        if (version1 == null && version2 == null) return 0;
        if (version1 == null) return -1;
        if (version2 == null) return 1;
        
        try {
            // 分割版本号
            String[] parts1 = version1.split("[.-]");
            String[] parts2 = version2.split("[.-]");
            
            int maxLength = Math.max(parts1.length, parts2.length);
            
            for (int i = 0; i < maxLength; i++) {
                int v1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
                int v2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
                
                if (v1 > v2) return 1;
                if (v1 < v2) return -1;
            }
            
            return 0;
        } catch (Exception e) {
            Log.e(TAG, "版本号比较失败: " + version1 + " vs " + version2, e);
            // 字符串比较作为后备
            return version1.compareTo(version2);
        }
    }
    
    /**
     * 解析版本号部分
     */
    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * 获取应用的基本信息（名称、图标等）
     */
    public static ApplicationInfo getApplicationInfo(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        } catch (Exception e) {
            Log.e(TAG, "获取应用信息失败: " + packageName, e);
            return null;
        }
    }
    
    /**
     * 检查应用是否已安装
     */
    public static boolean isAppInstalled(Context context, String packageName) {
        return getInstalledAppInfo(context, packageName) != null;
    }
    
    /**
     * 获取所有已安装包的包名列表（用于调试）
     */
    public static List<String> getAllInstalledPackageNames(Context context) {
        List<String> packageNames = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            List<PackageInfo> packages = pm.getInstalledPackages(0);
            for (PackageInfo pkg : packages) {
                packageNames.add(pkg.packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取已安装包列表失败", e);
        }
        return packageNames;
    }
    
    /**
     * 格式化SDK版本信息显示
     */
    public static String formatSdkInfo(int minSdk, int targetSdk) {
        StringBuilder sb = new StringBuilder();
        sb.append("Min SDK: ").append(minSdk);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sb.append(" (Android ").append(getAndroidVersionName(minSdk)).append(")");
        }
        sb.append("\nTarget SDK: ").append(targetSdk);
        sb.append(" (Android ").append(getAndroidVersionName(targetSdk)).append(")");
        return sb.toString();
    }
    
    /**
     * 获取Android版本名称
     */
    private static String getAndroidVersionName(int sdkVersion) {
        switch (sdkVersion) {
            case 21: return "5.0";
            case 22: return "5.1";
            case 23: return "6.0";
            case 24: return "7.0";
            case 25: return "7.1";
            case 26: return "8.0";
            case 27: return "8.1";
            case 28: return "9.0";
            case 29: return "10.0";
            case 30: return "11.0";
            case 31: return "12.0";
            case 32: return "12L";
            case 33: return "13.0";
            case 34: return "14.0";
            default: return String.valueOf(sdkVersion);
        }
    }
}