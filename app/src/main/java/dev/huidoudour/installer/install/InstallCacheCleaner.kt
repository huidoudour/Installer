package dev.huidoudour.installer.install

import android.content.Context
import android.text.format.Formatter
import java.io.File

/**
 * 安装临时文件（安装包副本、XAPK 解压产物）的统一收口与回收。
 *
 * 所有副本统一落在 `cacheDir/[INSTALL_CACHE_DIR]` 下，便于按目录整体回收，
 * 不会误伤崩溃日志等其他缓存；`file_paths.xml` 的 `<cache-path path="." />`
 * 覆盖整个 cacheDir（含子目录），因此换目录也不会破坏 FileProvider 分享。
 *
 * 清理只应在「应用启动」「导入新安装包前」与「用户手动清理」时触发：
 * 这些时机不可能有安装正在进行，也不会打断系统安装器（Authorizer.None）
 * 对文件的异步读取。
 */
object InstallCacheCleaner {

    /** 安装包副本与解压产物所在的子目录名。 */
    private const val INSTALL_CACHE_DIR = "install_cache"

    /** XAPK 解压目录。 */
    private const val XAPK_TEMP_DIR = "xapk_temp"

    /** 外部安装流程的临时副本前缀（历史遗留，位于 cacheDir 根目录）。 */
    private const val TEMP_INSTALL_PREFIX = "temp_install_"

    /** 更早版本遗留的临时副本名。 */
    private const val LEGACY_TEMP_APK = "temp_apk.apk"

    /** 安装缓存目录，不存在时创建。 */
    fun installCacheDir(context: Context): File =
        File(context.cacheDir, INSTALL_CACHE_DIR).apply { if (!exists()) mkdirs() }

    /**
     * 回收上次遗留的安装临时文件。
     *
     * @return 被回收的字节数
     */
    fun deleteStaleInstallFiles(context: Context): Long {
        val freed = currentSize(context)
        runCatching {
            File(context.cacheDir, INSTALL_CACHE_DIR).deleteRecursively()
            File(context.cacheDir, XAPK_TEMP_DIR).deleteRecursively()
            staleRootFiles(context).forEach { it.delete() }
        }
        return freed
    }

    /** 当前安装缓存占用字节数。 */
    fun currentSize(context: Context): Long {
        val roots = mutableListOf(
            File(context.cacheDir, INSTALL_CACHE_DIR),
            File(context.cacheDir, XAPK_TEMP_DIR)
        )
        roots += staleRootFiles(context)
        return roots.sumOf { it.sizeRecursively() }
    }

    /** 人类可读的体积文本（`Formatter` 会按系统语言本地化）。 */
    fun formatSize(context: Context, bytes: Long): String =
        Formatter.formatFileSize(context, bytes)

    /** cacheDir 根目录下历史遗留的安装临时文件。 */
    private fun staleRootFiles(context: Context): List<File> =
        context.cacheDir.listFiles { file ->
            file.isFile &&
                (file.name.startsWith(TEMP_INSTALL_PREFIX) || file.name == LEGACY_TEMP_APK)
        }?.toList().orEmpty()

    private fun File.sizeRecursively(): Long =
        if (isDirectory) listFiles()?.sumOf { it.sizeRecursively() } ?: 0L else length()
}
