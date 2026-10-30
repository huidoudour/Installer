package dev.huidoudour.installer.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * 文件选择与导出辅助工具
 * 文件选择优先使用自定义文件管理器 (FileManager)，不存在时回退到系统 SAF；
 * 日志等文件导出同样优先投递给自定义文件管理器，失败时由调用方回退到系统分享
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

    /**
     * 构建日志导出的投递 Intent，交由调用方的 ActivityResultLauncher 启动
     *
     * 优先 ACTION_SEND（文件管理器接收并保存），其次 ACTION_VIEW（直接打开）；
     * 文件管理器未安装或两种请求都无法响应时返回 null，由调用方回退到系统分享面板。
     */
    fun createExportDeliverIntent(
        context: Context,
        uri: Uri,
        mimeType: String,
        subject: String,
        text: String
    ): Intent? {
        if (!isFileManagerInstalled(context)) return null

        val clipData = ClipData.newRawUri(subject, uri)
        val candidates = listOf(
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_STREAM, uri)
                this.clipData = clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage(FILE_MANAGER_PACKAGE)
            },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                this.clipData = clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage(FILE_MANAGER_PACKAGE)
            }
        )

        return candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }
    }
}
