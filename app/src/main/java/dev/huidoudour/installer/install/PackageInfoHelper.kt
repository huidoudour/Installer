package dev.huidoudour.installer.install

import android.content.Context
import android.content.pm.PackageManager

/**
 * 包信息助手类
 */
object PackageInfoHelper {

    /**
     * 检查应用是否已安装
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 解析 APK 文件内的包名（无法解析时返回 null，如 XAPK/APKS 容器）。
     */
    fun getApkPackageName(context: Context, apkPath: String): String? {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkPath, 0)?.packageName
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 判断 APK 对应的应用是否已安装（无法解析包名时视为未安装）。
     */
    fun isApkInstalled(context: Context, apkPath: String): Boolean {
        val packageName = getApkPackageName(context, apkPath) ?: return false
        return isAppInstalled(context, packageName)
    }

}
