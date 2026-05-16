package io.github.huidoudour.Installer.util

import android.content.Context
import com.rosan.dhizuku.api.Dhizuku
import io.github.huidoudour.Installer.R
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
    fun executeCommand(command: String): String {
        return try {
            if (!Dhizuku.init()) {
                throw Exception("Dhizuku not initialized")
            }

            val process = Dhizuku.newProcess(arrayOf("sh", "-c", command), null, null)
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
                callback.onProgress(context.getString(R.string.start_apk_install))

                val createCmd = StringBuilder("pm install-create --user 0")
                if (replaceExisting) createCmd.append(" -r")
                if (grantPermissions) createCmd.append(" -g")

                val installerPackage = getInstallerPackage(context)
                if (installerPackage.isNotEmpty()) {
                    createCmd.append(" -i ").append(installerPackage)
                }

                callback.onProgress(context.getString(R.string.create_session, createCmd.toString()))
                val createOutput = executeCommand(createCmd.toString())

                if (!createOutput.contains("Success")) {
                    throw Exception(context.getString(R.string.install_failed_error, createOutput))
                }

                val sessionId = createOutput.substring(
                    createOutput.indexOf("[") + 1,
                    createOutput.indexOf("]")
                )
                callback.onProgress(context.getString(R.string.session_id, sessionId))

                // 写入 APK
                val writeCmd = "pm install-write -S ${apkFile.length()} $sessionId base.apk -"
                callback.onProgress(context.getString(R.string.write_apk_data))

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
                    throw Exception(context.getString(R.string.install_failed_error, writeOutput))
                }

                // 提交安装
                callback.onProgress(context.getString(R.string.submit_install))
                val commitOutput = executeCommand("pm install-commit $sessionId")

                if (commitOutput.lowercase().contains("success")) {
                    callback.onSuccess(context.getString(R.string.install_success_simple))
                } else {
                    callback.onError(context.getString(R.string.install_failed_error, commitOutput))
                }

            } catch (e: Exception) {
                callback.onError(context.getString(R.string.install_exception, e.message))
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
                callback.onProgress(context.getString(R.string.extract_xapk))

                extractedApks = XapkInstaller.extractXapk(context, xapkPath)
                callback.onProgress(context.getString(R.string.extract_complete, extractedApks.size))

                val createCmd = StringBuilder("pm install-create --user 0")
                if (replaceExisting) createCmd.append(" -r")
                if (grantPermissions) createCmd.append(" -g")

                val installerPackage = getInstallerPackage(context)
                if (installerPackage.isNotEmpty()) {
                    createCmd.append(" -i ").append(installerPackage)
                }

                callback.onProgress(context.getString(R.string.create_session, ""))
                val createOutput = executeCommand(createCmd.toString())

                if (!createOutput.contains("Success")) {
                    throw Exception(context.getString(R.string.install_failed_error, createOutput))
                }

                val sessionId = createOutput.substring(
                    createOutput.indexOf("[") + 1,
                    createOutput.indexOf("]")
                )
                callback.onProgress(context.getString(R.string.session_id, sessionId))

                var current = 0
                for (apkFile in extractedApks) {
                    current++
                    callback.onProgress(
                        context.getString(
                            R.string.apk_progress,
                            current,
                            extractedApks.size,
                            apkFile.name
                        )
                    )

                    val writeCmd = "pm install-write -S ${apkFile.length()} $sessionId ${apkFile.name} -"

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
                        throw Exception(
                            context.getString(
                                R.string.install_failed_error,
                                context.getString(R.string.apk_name_failed, apkFile.name, writeOutput)
                            )
                        )
                    }
                }

                callback.onProgress(context.getString(R.string.submitting_install))
                val commitOutput = executeCommand("pm install-commit $sessionId")

                if (commitOutput.lowercase().contains("success")) {
                    callback.onSuccess(context.getString(R.string.xapk_install_success_msg, extractedApks.size))
                } else {
                    callback.onError(context.getString(R.string.install_failed_error, commitOutput))
                }

            } catch (e: Exception) {
                callback.onError(context.getString(R.string.xapk_install_exception, e.message))
            } finally {
                extractedApks?.let { XapkInstaller.cleanupTempFiles(it) }
            }
        }.start()
    }

    private fun getInstallerPackage(context: Context): String {
        val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val enableCustomPackageName = sharedPreferences.getBoolean("enable_custom_package_name", true)

        if (!enableCustomPackageName) {
            return "com.android.shell"
        }

        val installerPackage = sharedPreferences.getString("installer_package", "")
        if (installerPackage.isNullOrEmpty()) {
            return "com.android.shell"
        }

        return installerPackage
    }
}
