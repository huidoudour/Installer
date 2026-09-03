package io.github.huidoudour.installer.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * 文件选择器辅助工具
 * 优先使用自定义文件管理器 (FileManager)，不存在时回退到系统 SAF
 */
object FilePickerHelper {

    /** 自定义文件管理器的包名 */
    const val FILE_MANAGER_PACKAGE = "me.huidoudour.file.manager"

    /**
     * 检查自定义文件管理器是否已安装
     */
    fun isFileManagerInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(FILE_MANAGER_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 创建文件选择 Intent
     * 如果 FileManager 已安装，优先通过显式 Intent 调用它；
     * 否则回退到系统 SAF (ACTION_GET_CONTENT)。
     */
    fun createFilePickerIntent(context: Context, mimeType: String = "*/*"): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (isFileManagerInstalled(context)) {
                setPackage(FILE_MANAGER_PACKAGE)
            }
        }
    }
}
