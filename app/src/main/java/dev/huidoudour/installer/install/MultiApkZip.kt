package dev.huidoudour.installer.install

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import dev.huidoudour.installer.util.LogManager
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * 包含多个“独立 APK”的 ZIP 容器识别与解压。
 *
 * 与 XAPK/APKS/APKM 这类“单应用 + 拆分 APK”的容器不同，这里的压缩包里每个 APK
 * 都是可以直接单独安装的基准包（不含 split），因此必须逐个安装，而不是塞进同一个
 * 安装会话。判定只看 APK 清单里的 split 信息，与压缩包扩展名、条目命名无关。
 */
object MultiApkZip {

    private const val TAG = "MultiApkZip"

    /** 与 [XapkInstaller] 共用解压根目录，回收逻辑保持一致。 */
    private const val TEMP_DIR = "xapk_temp"

    /** 这些元数据文件代表“单应用 + 拆分 APK”容器，直接交给原有容器流程处理。 */
    private val BUNDLE_METADATA_FILES = listOf(
        "manifest.json",     // XAPK（APKPure）
        "info.json",         // APKM（APKMirror）
        "meta.sai_v2.json",  // SAI
        "toc.pb",            // APKS（bundletool）
    )

    /** 已知的单应用拆分格式扩展名，无需再逐 APK 解析。 */
    private val BUNDLE_EXTENSIONS = setOf("xapk", "apks", "apkm")

    private val logManager get() = LogManager.getInstance()

    /**
     * 压缩包内的单个独立 APK。
     *
     * @param isSplit 是否为拆分 APK（splitNames 非空），仅用于判定容器类型
     */
    data class Entry(
        val file: File,
        val entryName: String,
        val packageName: String?,
        val label: String?,
        val versionName: String?,
        val versionCode: Long,
        val size: Long,
        val icon: Drawable?,
        val isSplit: Boolean,
    )

    /**
     * 识别并解压“包含多个独立 APK”的压缩包。
     *
     * 解压产物位于 `cacheDir/xapk_temp/multi_*`，由 [InstallCacheCleaner] 统一回收；
     * 调用方读取完信息、安装结束后也可通过 [cleanup] 立即回收。
     *
     * @return 解压后的 APK 列表；不是多 APK 压缩包（少于 2 个 APK、含拆分 APK、
     *         含单应用容器元数据、解析失败）时返回 null，已产生的临时文件会被删除
     */
    fun extract(context: Context, containerPath: String): List<Entry>? {
        val container = File(containerPath)
        if (!container.isFile) return null
        if (container.extension.lowercase() in BUNDLE_EXTENSIONS) return null

        var workDir: File? = null
        var extracted = emptyList<Entry>()

        try {
            ZipFile(container).use { zip ->
                val apkEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                    .toList()
                if (apkEntries.size < 2) return null

                if (BUNDLE_METADATA_FILES.any { zip.getEntry(it) != null }) {
                    logManager.addLog("Archive declares bundle metadata; keep split-package flow", "Multi APK")
                    return null
                }

                workDir = File(
                    File(context.cacheDir, TEMP_DIR),
                    "multi_${System.currentTimeMillis()}_${System.nanoTime()}"
                )
                if (!workDir.mkdirs()) {
                    logManager.addLog(
                        "Could not create extraction directory: ${workDir.absolutePath}",
                        "Multi APK"
                    )
                    return null
                }

                val usedNames = mutableSetOf<String>()
                val entries = mutableListOf<Entry>()
                for (zipEntry in apkEntries) {
                    val outputFile = File(
                        workDir,
                        uniqueEntryName(zipEntry.name.substringAfterLast('/'), usedNames)
                    )
                    zip.getInputStream(zipEntry).use { input ->
                        FileOutputStream(outputFile).use { output -> input.copyTo(output) }
                    }
                    entries += readEntry(context, outputFile, zipEntry.name)
                }
                extracted = entries
            }

            // 只有“每个 APK 都能解析且都不是拆分 APK”才能按独立 APK 逐个安装
            val allParsed = extracted.all { it.packageName != null }
            val allIndependent = extracted.none { it.isSplit }
            if (!allParsed || !allIndependent) {
                logManager.addLog(
                    "Archive contains split or unreadable APKs; keep split-package flow",
                    "Multi APK"
                )
                cleanup(extracted)
                runCatching { workDir?.deleteRecursively() }
                return null
            }

            logManager.addLog(
                "Detected multi-APK archive: ${extracted.size} independent APKs",
                "Multi APK"
            )
            return extracted
        } catch (e: Exception) {
            Log.e(TAG, "Error reading multi-APK archive: ${e.message}")
            logManager.addLog("Failed to read multi-APK archive: ${e.message}", "Multi APK")
            cleanup(extracted)
            runCatching { workDir?.deleteRecursively() }
            return null
        }
    }

    /** 回收解压产物及其目录。 */
    fun cleanup(entries: List<Entry>) {
        XapkInstaller.cleanupTempFiles(entries.map { it.file })
    }

    private fun readEntry(context: Context, file: File, entryName: String): Entry {
        val packageManager = context.packageManager
        val packageInfo = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing APK info for $entryName: ${e.message}")
            null
        }
        val appInfo = packageInfo?.applicationInfo
            ?: return Entry(file, entryName, null, null, null, 0L, file.length(), null, false)

        // 读取 APK 内的应用名与图标需要指向真实文件路径
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath

        val label = runCatching { packageManager.getApplicationLabel(appInfo).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        val icon = runCatching { packageManager.getApplicationIcon(appInfo) }.getOrNull()

        return Entry(
            file = file,
            entryName = entryName,
            packageName = packageInfo.packageName,
            label = label,
            versionName = packageInfo.versionName,
            versionCode = packageInfo.longVersionCode,
            size = file.length(),
            icon = icon,
            isSplit = !packageInfo.splitNames.isNullOrEmpty(),
        )
    }

    private fun uniqueEntryName(name: String, usedNames: MutableSet<String>): String {
        if (usedNames.add(name)) return name

        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val suffix = if (dot > 0) name.substring(dot) else ""
        var index = 1
        var candidate: String
        do {
            candidate = "${stem}_$index$suffix"
            index++
        } while (!usedNames.add(candidate))
        return candidate
    }
}
