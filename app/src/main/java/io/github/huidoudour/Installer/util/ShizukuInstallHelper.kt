package io.github.huidoudour.Installer.util

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Shizuku APK 安装辅助类
 */
object ShizukuInstallHelper {

    interface InstallCallback {
        fun onProgress(message: String)
        fun onSuccess(message: String)
        fun onError(error: String)
    }

    /**
     * 执行 Shizuku 命令
     */
    @Throws(Exception::class)
    fun executeCommand(command: String): String {
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true

            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

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
     * 执行 Shizuku 命令并传入文件数据
     */
    @Throws(Exception::class)
    fun executeCommandWithInput(command: String, inputFile: File): String {
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true

            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            // 写入文件数据
            val fis = FileInputStream(inputFile)
            val os = process.outputStream

            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                os.write(buffer, 0, bytesRead)
            }
            os.flush()
            os.close()
            fis.close()

            // 读取输出
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
     * 执行 Shizuku 命令（带上下文）
     */
    @Throws(Exception::class)
    fun executeCommand(context: Context, command: String): String {
        return executeCommand(command)
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

                // 创建安装会话
                val createCmd = StringBuilder("pm install-create --user 0")
                if (replaceExisting) createCmd.append(" -r")
                if (grantPermissions) createCmd.append(" -g")

                // 添加安装请求者参数
                val installerPackage = getInstallerPackage(context)
                if (installerPackage.isNotEmpty()) {
                    createCmd.append(" -i ").append(installerPackage)
                }

                callback.onProgress("Creating install session: $createCmd")
                val createOutput = executeCommand(context, createCmd.toString())

                if (!createOutput.contains("Success")) {
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
                val writeOutput = executeCommandWithInput(writeCmd, apkFile)

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

                // 解压 XAPK
                extractedApks = XapkInstaller.extractXapk(context, xapkPath)
                callback.onProgress("Extraction complete, ${extractedApks.size} APKs found")

                // 创建安装会话
                val createCmd = StringBuilder("pm install-create --user 0")
                if (replaceExisting) createCmd.append(" -r")
                if (grantPermissions) createCmd.append(" -g")

                val installerPackage = getInstallerPackage(context)
                if (installerPackage.isNotEmpty()) {
                    createCmd.append(" -i ").append(installerPackage)
                }

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

                // 写入所有 APK
                var current = 0
                for (apkFile in extractedApks) {
                    current++
                    callback.onProgress("[$current/${extractedApks.size}] ${apkFile.name}")

                    val writeCmd = "pm install-write -S ${apkFile.length()} $sessionId ${apkFile.name} -"

                    val writeOutput = executeCommandWithInput(writeCmd, apkFile)

                    if (!writeOutput.contains("Success")) {
                        throw Exception("Install failed: ${apkFile.name} failed: $writeOutput")
                    }
                }

                // 提交安装
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
                // 清理临时文件
                extractedApks?.let { XapkInstaller.cleanupTempFiles(it) }
            }
        }.start()
    }

    /**
     * 安装 APK 文件
     */
    fun installApk(
        context: Context,
        apkPath: String,
        replaceExisting: Boolean,
        grantPermissions: Boolean,
        callback: InstallCallback
    ) {
        try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                callback.onError("APK file does not exist")
                return
            }

            installSingleApk(context, apkFile, replaceExisting, grantPermissions, callback)
        } catch (e: Exception) {
            callback.onError("Install exception: ${e.message}")
        }
    }

    /**
     * 获取用户设置的安装请求者包名
     */
    private fun getInstallerPackage(context: Context): String {
        val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // 检查是否启用了自定义包名
        val enableCustomPackageName = sharedPreferences.getBoolean("enable_custom_package_name", true)

        if (!enableCustomPackageName) {
            return "com.android.shell"
        }

        // 开启时使用用户选择的包名
        val installerPackage = sharedPreferences.getString("installer_package", "")

        // 如果用户没有设置，默认使用 com.android.shell
        if (installerPackage.isNullOrEmpty()) {
            return "com.android.shell"
        }

        return installerPackage
    }
}
