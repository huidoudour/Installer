package dev.huidoudour.installer.install

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

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

    /**
     * 判断 APK 是否为拆分 APK（split）。
     *
     * 基准 APK 没有 split 名，拆分 APK 的 splitNames 非空；
     * 由此可判断一组 APK 是否构成完整安装包，与文件名无关。
     */
    fun isSplitApk(context: Context, apkPath: String): Boolean {
        val archiveInfo = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkPath, 0)
        } catch (_: Exception) {
            null
        } ?: return false
        return !archiveInfo.splitNames.isNullOrEmpty()
    }

    /**
     * 判断解压出的 APK 集合是否包含基准 APK。
     *
     * 包含基准 APK 时安装会话必须使用 MODE_FULL_INSTALL，
     * 否则系统会以 INSTALL_FAILED_INVALID_APK: Missing existing base package 失败。
     */
    fun containsBaseApk(context: Context, apkFiles: List<File>): Boolean =
        apkFiles.any { !isSplitApk(context, it.absolutePath) }

}
