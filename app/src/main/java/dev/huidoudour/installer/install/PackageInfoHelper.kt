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
     * 解析安装文件的包名：
     * 普通 APK 直接解析；XAPK/APKS/APKM/ZIP 容器先取出基准 APK 再解析。
     */
    fun getInstallPackageName(context: Context, filePath: String): String? {
        if (!XapkInstaller.isXapkFile(filePath)) {
            return getApkPackageName(context, filePath)
        }

        val baseApk = XapkInstaller.extractBaseApkForInfo(context, filePath) ?: return null
        return try {
            getApkPackageName(context, baseApk.absolutePath)
        } finally {
            baseApk.delete()
        }
    }

    /**
     * 判断 APK / 安装包容器对应的应用是否已安装（无法解析包名时视为未安装）。
     */
    fun isApkInstalled(context: Context, apkPath: String): Boolean {
        val packageName = getInstallPackageName(context, apkPath) ?: return false
        return isAppInstalled(context, packageName)
    }

}
