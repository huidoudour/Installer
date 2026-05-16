package io.github.huidoudour.Installer.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * 包信息助手类
 */
object PackageInfoHelper {

    /**
     * 获取应用信息
     */
    fun getPackageInfo(context: Context, packageName: String): PackageInfo? {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * 获取应用标签（名称）
     */
    fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /**
     * 获取应用图标
     */
    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * 检查应用是否已安装
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 获取已安装应用的包名列表
     */
    fun getInstalledPackages(context: Context): List<String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstalledPackages(0)
            }.map { it.packageName }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取第三方应用包名列表
     */
    fun getThirdPartyPackages(context: Context): List<String> {
        return try {
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstalledPackages(0)
            }

            packages
                .filter { !isSystemApp(it.applicationInfo) }
                .map { it.packageName }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 检查是否为系统应用
     */
    private fun isSystemApp(appInfo: ApplicationInfo?): Boolean {
        if (appInfo == null) return false
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    /**
     * 获取应用版本信息
     */
    fun getVersionInfo(context: Context, packageName: String): Pair<String, Long>? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "Unknown"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            Pair(versionName, versionCode)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
