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

    /**
     * 按包名将 APK 分组，保持原始顺序。
     *
     * 一个容器可能打包了多个独立应用（包名不同），而同一个安装会话
     * 只能承载同一个包的 base + splits；若把多个基准 APK 写入同一个会话，
     * 系统会以 INSTALL_FAILED_INVALID_APK: Split null was defined multiple times 拒绝。
     * 无法解析包名的 APK 各自单独成组。
     *
     * @return 包名 -> 该包的 APK 列表（LinkedHashMap，安装顺序与解压顺序一致）
     */
    fun groupByPackageName(context: Context, apkFiles: List<File>): Map<String, List<File>> {
        val groups = LinkedHashMap<String, MutableList<File>>()
        for (apkFile in apkFiles) {
            val key = getApkPackageName(context, apkFile.absolutePath)
                ?: "unknown(${apkFile.name})"
            groups.getOrPut(key) { mutableListOf() }.add(apkFile)
        }
        return groups.mapValues { it.value.toList() }
    }

}
