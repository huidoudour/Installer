package dev.huidoudour.installer.install

import android.content.Context
import android.util.Log
import dev.huidoudour.installer.R
import dev.huidoudour.installer.util.LogManager
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 安装包容器（XAPK / APKS / APKM / 包含 APK 的 ZIP）工具类。
 *
 * 判定以压缩包内容为准：只要压缩包内含有 `.apk` 条目即视为安装包容器。
 * 普通 APK 也是 ZIP，但根目录一定存在 `AndroidManifest.xml`，以此区分，
 * 这样即使来自文件管理器的 ZIP 被改了扩展名也能正确识别。
 */
object XapkInstaller {

    private const val TAG = "XapkInstaller"
    private val logManager get() = LogManager.getInstance()

    /** 解压产物根目录（与 [InstallCacheCleaner] 中保持一致）。 */
    private const val TEMP_DIR = "xapk_temp"

    /** 普通 APK 根目录必有的清单文件。 */
    private const val ROOT_MANIFEST = "AndroidManifest.xml"

    /**
     * 检测文件是否为需要解压后再安装的包容器（XAPK/APKS/APKM/含 APK 的 ZIP）。
     *
     * @return true 表示是容器；普通 APK 与无法读取的文件返回 false
     */
    fun isXapkFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.isFile) return false

            ZipFile(file).use { zip ->
                val hasApkEntry = zip.entries().asSequence().any { entry ->
                    !entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)
                }
                if (!hasApkEntry) {
                    false
                } else if (file.extension.equals("apk", ignoreCase = true)) {
                    // 被改名的容器没有根清单；正常 APK 一定存在根清单
                    zip.getEntry(ROOT_MANIFEST) == null
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking package container: ${e.message}")
            logManager.addLog("Failed to identify package type for $filePath: ${e.message}", "XAPK")
            false
        }
    }

    /**
     * 判定是否为普通 APK（根目录存在 AndroidManifest.xml）。
     *
     * 与扩展名无关，因此改名的容器不会被误判为 APK。
     */
    fun isApkFile(filePath: String): Boolean {
        return try {
            ZipFile(File(filePath)).use { zip -> zip.getEntry(ROOT_MANIFEST) != null }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取文件类型描述
     */
    fun getFileTypeDescription(context: Context, filePath: String): String {
        val file = File(filePath)
        val extension = file.extension.lowercase()

        return when (extension) {
            "xapk" -> "XAPK"
            "apks" -> "APKS"
            "apkm" -> "APKM"
            "zip" -> "ZIP"
            "apk" -> if (isXapkFile(filePath)) "ZIP" else "APK"
            else -> {
                if (isXapkFile(filePath)) "XAPK" else context.getString(R.string.unknown_file_format)
            }
        }
    }

    /**
     * 解压容器内的全部 APK。
     *
     * 每次解压都使用独立目录，避免上一次安装残留的 APK 混入本次安装；
     * 解压失败时回收已产生的文件。
     */
    fun extractXapk(context: Context, xapkPath: String): List<File> {
        val extractedApks = mutableListOf<File>()
        val tempDir = File(
            File(context.cacheDir, TEMP_DIR),
            "extract_${System.currentTimeMillis()}_${System.nanoTime()}"
        )

        try {
            logManager.addLog("Starting package extraction: $xapkPath", "XAPK")
            if (!tempDir.mkdirs()) {
                logManager.addLog("Could not create extraction directory: ${tempDir.absolutePath}", "XAPK")
                return emptyList()
            }

            val usedNames = mutableSetOf<String>()
            ZipFile(File(xapkPath)).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory || !entry.name.endsWith(".apk", ignoreCase = true)) {
                        continue
                    }

                    val apkName = uniqueEntryName(File(entry.name).name, usedNames)
                    val outputFile = File(tempDir, apkName)

                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    extractedApks.add(outputFile)
                    logManager.addLog("Extracted APK: ${outputFile.name}", "XAPK")
                }
            }
            logManager.addLog("Extraction completed: ${extractedApks.size} APK(s)", "XAPK")
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting package: ${e.message}")
            logManager.addLog("Extraction failed: ${e.message}", "XAPK")
            extractedApks.forEach { runCatching { it.delete() } }
            runCatching { tempDir.deleteRecursively() }
            extractedApks.clear()
        }

        return extractedApks
    }

    /**
     * 从容器中取出用于读取应用信息的基准 APK（base / base-master）。
     *
     * 返回的文件位于安装缓存目录，调用方读取完信息后应自行删除。
     * 无法定位或解压时返回 null。
     */
    fun extractBaseApkForInfo(context: Context, containerPath: String): File? {
        var outputFile: File? = null
        return try {
            ZipFile(File(containerPath)).use { zip ->
                val apkEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                    .toList()
                if (apkEntries.isEmpty()) return null

                val baseEntry = selectBaseEntry(apkEntries, readPackageNameFromMetadata(zip))
                outputFile = File(
                    InstallCacheCleaner.installCacheDir(context),
                    "info_${System.currentTimeMillis()}_${System.nanoTime()}.apk"
                )
                zip.getInputStream(baseEntry).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                logManager.addLog("Extracted base APK for info: ${baseEntry.name}", "XAPK")
                outputFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting base APK for info: ${e.message}")
            logManager.addLog("Failed to read package info from $containerPath: ${e.message}", "XAPK")
            outputFile?.delete()
            null
        }
    }

    /**
     * 清理解压产物及其独立目录。
     */
    fun cleanupTempFiles(files: List<File>) {
        val parentDirs = files.mapNotNull { it.parentFile }.toSet()

        for (file in files) {
            try {
                file.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting temp file: ${e.message}")
                logManager.addLog("Failed to remove extracted APK ${file.name}: ${e.message}", "XAPK")
            }
        }

        for (dir in parentDirs) {
            runCatching { dir.deleteRecursively() }
            // xapk_temp 根目录在最后一个解压目录删除后一并回收
            val root = dir.parentFile
            if (root != null && root.name == TEMP_DIR && root.list()?.isEmpty() == true) {
                runCatching { root.delete() }
            }
        }
    }

    /**
     * 选择能代表整个安装包的基准 APK：
     * base.apk → base-master.apk（APKS）→ 与包名同名（XAPK）→ 体积最大。
     */
    private fun selectBaseEntry(entries: List<ZipEntry>, packageName: String?): ZipEntry {
        fun baseName(entry: ZipEntry) = entry.name.substringAfterLast('/').lowercase()

        val preferredNames = buildList {
            add("base.apk")
            add("base-master.apk")
            if (!packageName.isNullOrBlank()) {
                add("${packageName.lowercase()}.apk")
            }
        }

        for (preferred in preferredNames) {
            entries.firstOrNull { baseName(it) == preferred }?.let { return it }
        }

        // 部分 APKS 把 base 放在 splits/ 下且命名不同，优先取含 base 的条目
        entries.firstOrNull { baseName(it).contains("base") }?.let { return it }

        return entries.maxByOrNull { it.size.takeIf { size -> size > 0 } ?: 0L } ?: entries.first()
    }

    /**
     * 读取 XAPK manifest.json / APKM info.json 中的包名，用于定位基准 APK。
     */
    private fun readPackageNameFromMetadata(zip: ZipFile): String? {
        val entry = zip.getEntry("manifest.json")
            ?: zip.getEntry("info.json")
            ?: zip.getEntry("meta.sai_v2.json")
            ?: return null

        return try {
            val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            when {
                json.has("package_name") -> json.optString("package_name")
                json.has("pname") -> json.optString("pname")
                json.has("package") -> json.optString("package")
                else -> null
            }?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
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
