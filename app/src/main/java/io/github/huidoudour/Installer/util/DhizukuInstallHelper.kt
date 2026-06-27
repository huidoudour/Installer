package io.github.huidoudour.Installer.util

import android.content.Context
import com.rosan.dhizuku.api.Dhizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Dhizuku APK 安装辅助类
 */
object DhizukuInstallHelper {

    interface InstallCallback {
        fun onProgress(message: String)
        fun onSuccess(message: String)
        fun onError(error: String)
    }

    /**
     * 执行 Dhizuku 命令
     */
    @Throws(Exception::class)
    fun executeCommand(context: Context, command: String): String {
        return try {
            if (!Dhizuku.init(context.applicationContext)) {
                throw Exception("Dhizuku not initialized")
            }

            val process = Dhizuku.newProcess(arrayOf("sh", "-c", "$command 2>&1"), null, null)
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            throw Exception("执行命令失败: ${e.message}", e)
        }
    }

    /**
     * 判断是否为华为/HarmonyOS设备上的NPE错误（AppOpsService.checkPackage缺陷）
     */
    private fun isHuaweiNpeError(message: String): Boolean {
        return message.contains("NullPointerException") &&
                message.contains("AppOpsService.checkPackage")
    }

    /**
     * 判断是否为华为/HarmonyOS设备上的SecurityException（UID不匹配）
     */
    private fun isHuaweiSecurityError(message: String): Boolean {
        return message.contains("SecurityException") &&
                message.contains("does not belong to")
    }

    /**
     * 使用单命令 `pm install` 安装 APK（华为设备回退方案）
     * 先复制 APK 到 /data/local/tmp/，再执行 pm install
     */
    @Throws(Exception::class)
    private fun installWithSingleCommand(
        context: Context,
        apkFile: File,
        replaceExisting: Boolean,
        grantPermissions: Boolean
    ) {
        val tempName = "install_temp_${System.currentTimeMillis()}.apk"
        val tempPath = "/data/local/tmp/$tempName"

        // 复制 APK 到临时路径
        val copyCmd = "cp \"${apkFile.absolutePath}\" $tempPath"
        val copyOutput = executeCommand(context, copyCmd)
        if (copyOutput.isNotEmpty() && copyOutput.lowercase().contains("error")) {
            throw Exception("Failed to copy APK to temp: $copyOutput")
        }

        // 设置权限
        executeCommand(context, "chmod 644 $tempPath")

        try {
            // 执行 pm install
            val installCmd = StringBuilder("pm install")
            if (replaceExisting) installCmd.append(" -r")
            if (grantPermissions) installCmd.append(" -g")
            installCmd.append(" --user 0 $tempPath")

            val installOutput = executeCommand(context, installCmd.toString())

            if (!installOutput.lowercase().contains("success")) {
                throw Exception("Install failed: $installOutput")
            }
        } finally {
            // 清理临时文件
            executeCommand(context, "rm -f $tempPath")
        }
    }

    /**
     * 安装单个 APK
     */
    fun installSingleApk(
        context: Context,
        apkFile: File,
        replaceExisting: Boolean,
        grantPermissions: Boolean,
        callback: InstallCallback
    ) {
        Thread {
            try {
                callback.onProgress("Starting APK installation...")

                val createCmd = StringBuilder("pm install-create --user 0")
                if (replaceExisting) createCmd.append(" -r")
                if (grantPermissions) createCmd.append(" -g")

                callback.onProgress("Creating install session: $createCmd")
                val createOutput = executeCommand(context, createCmd.toString())

                if (!createOutput.contains("Success")) {
                    // 华为设备特殊处理：NPE 或 SecurityException 时回退到单命令安装
                    if (isHuaweiNpeError(createOutput) || isHuaweiSecurityError(createOutput)) {
                        callback.onProgress("Detected Huawei device, using alternative install method...")
                        installWithSingleCommand(context, apkFile, replaceExisting, grantPermissions)
                        callback.onSuccess("Installation successful!")
                        return@Thread
                    }
                    throw Exception("Install failed: $createOutput")
                }

                val sessionId = createOutput.substring(
                    createOutput.indexOf("[") + 1,
                    createOutput.indexOf("]")
                )
                callback.onProgress("Session ID: $sessionId")

                // 写入 APK
                val writeCmd = "pm install-write -S ${apkFile.length()} $sessionId base.apk -"
                callback.onProgress("Writing APK data...")

                if (!Dhizuku.init(context.applicationContext)) {
                    throw Exception("Dhizuku not initialized")
                }
                val process = Dhizuku.newProcess(arrayOf("sh", "-c", writeCmd), null, null)
                val fis = java.io.FileInputStream(apkFile)
                val os = process.outputStream

                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    os.write(buffer, 0, bytesRead)
                }
                os.flush()
                os.close()
                fis.close()

                val writeOutput = BufferedReader(InputStreamReader(process.inputStream)).readText()
                process.waitFor()

                if (!writeOutput.contains("Success")) {
                    throw Exception("Install failed: $writeOutput")
                }

                // 提交安装
                callback.onProgress("Submitting install...")
                val commitOutput = executeCommand(context, "pm install-commit $sessionId")

                if (commitOutput.lowercase().contains("success")) {
                    callback.onSuccess("Installation successful!")
                } else {
                    callback.onError("Install failed: $commitOutput")
                }

            } catch (e: Exception) {
                callback.onError("Install exception: ${e.message}")
            }
        }.start()
    }

    /**
     * 安装 XAPK (多个 APK)
     */
    fun installXapk(
        context: Context,
        xapkPath: String,
        replaceExisting: Boolean,
        grantPermissions: Boolean,
        callback: InstallCallback
    ) {
        Thread {
            var extractedApks: List<File>? = null
            try {
                callback.onProgress("Extracting XAPK...")

                extractedApks = XapkInstaller.extractXapk(context, xapkPath)
                callback.onProgress("Extraction complete, ${extractedApks.size} APKs found")

                val createCmd = StringBuilder("pm install-create --user 0")
                if (replaceExisting) createCmd.append(" -r")
                if (grantPermissions) createCmd.append(" -g")

                callback.onProgress("Creating install session")
                val createOutput = executeCommand(context, createCmd.toString())

                if (!createOutput.contains("Success")) {
                    throw Exception("Install failed: $createOutput")
                }

                val sessionId = createOutput.substring(
                    createOutput.indexOf("[") + 1,
                    createOutput.indexOf("]")
                )
                callback.onProgress("Session ID: $sessionId")

                var current = 0
                for (apkFile in extractedApks) {
                    current++
                    callback.onProgress("[$current/${extractedApks.size}] ${apkFile.name}")

                    val writeCmd = "pm install-write -S ${apkFile.length()} $sessionId ${apkFile.name} -"

                    if (!Dhizuku.init(context.applicationContext)) {
                        throw Exception("Dhizuku not initialized")
                    }
                    val process = Dhizuku.newProcess(arrayOf("sh", "-c", writeCmd), null, null)
                    val fis = java.io.FileInputStream(apkFile)
                    val os = process.outputStream

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                    }
                    os.flush()
                    os.close()
                    fis.close()

                    val writeOutput = BufferedReader(InputStreamReader(process.inputStream)).readText()
                    process.waitFor()

                    if (!writeOutput.contains("Success")) {
                        throw Exception("Install failed: ${apkFile.name} failed: $writeOutput")
                    }
                }

                callback.onProgress("Submitting install...")
                val commitOutput = executeCommand(context, "pm install-commit $sessionId")

                if (commitOutput.lowercase().contains("success")) {
                    callback.onSuccess("XAPK installation successful! ${extractedApks.size} APKs installed")
                } else {
                    callback.onError("Install failed: $commitOutput")
                }

            } catch (e: Exception) {
                callback.onError("XAPK install exception: ${e.message}")
            } finally {
                extractedApks?.let { XapkInstaller.cleanupTempFiles(it) }
            }
        }.start()
    }
}
