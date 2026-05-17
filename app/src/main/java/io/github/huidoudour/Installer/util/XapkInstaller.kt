package io.github.huidoudour.Installer.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * XAPK 安装器工具类
 */
object XapkInstaller {

    private const val TAG = "XapkInstaller"

    /**
     * 检测文件是否为 XAPK 文件
     */
    fun isXapkFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) return false

            val extension = file.extension.lowercase()
            if (extension == "xapk" || extension == "apks") {
                return true
            }

            // 检查文件内容是否为 ZIP 格式
            if (extension == "apk") {
                return false
            }

            // 对于其他扩展名，检查是否为 ZIP 格式
            try {
                ZipFile(file).use { zip ->
                    // 检查是否包含 APK 文件
                    zip.entries().asSequence().any { entry ->
                        entry.name.endsWith(".apk", ignoreCase = true)
                    }
                }
            } catch (e: Exception) {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking XAPK file: ${e.message}")
            false
        }
    }

    /**
     * 获取文件类型描述
     */
    fun getFileTypeDescription(filePath: String): String {
        val file = File(filePath)
        val extension = file.extension.lowercase()

        return when (extension) {
            "xapk" -> "XAPK"
            "apks" -> "APKS"
            "apk" -> "APK"
            else -> {
                if (isXapkFile(filePath)) "XAPK" else "Unknown"
            }
        }
    }

    /**
     * 获取 APK 数量（用于 XAPK）
     */
    fun getApkCount(context: Context, filePath: String): Int {
        return try {
            val file = File(filePath)
            if (!file.exists()) return 0

            val extension = file.extension.lowercase()
            if (extension == "apk") return 1

            ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .filter { entry ->
                        !entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)
                    }
                    .count()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting APK count: ${e.message}")
            0
        }
    }

    /**
     * 解压 XAPK 文件
     */
    fun extractXapk(context: Context, xapkPath: String): List<File> {
        val extractedApks = mutableListOf<File>()
        val tempDir = File(context.cacheDir, "xapk_temp")

        try {
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }

            val xapkFile = File(xapkPath)
            ZipFile(xapkFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        val apkName = File(entry.name).name
                        val outputFile = File(tempDir, apkName)

                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outputFile).use { output ->
                                input.copyTo(output)
                            }
                        }

                        extractedApks.add(outputFile)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting XAPK: ${e.message}")
        }

        return extractedApks
    }

    /**
     * 清理临时文件
     */
    fun cleanupTempFiles(files: List<File>) {
        for (file in files) {
            try {
                file.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting temp file: ${e.message}")
            }
        }

        // 删除临时目录
        val tempDir = files.firstOrNull()?.parentFile
        if (tempDir != null && tempDir.name == "xapk_temp") {
            try {
                tempDir.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting temp dir: ${e.message}")
            }
        }
    }
}
