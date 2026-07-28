package io.github.huidoudour.Installer.auth

import android.content.Context
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageManager
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ServiceManager
import io.github.huidoudour.Installer.install.LocalIntentReceiver
import io.github.huidoudour.Installer.install.XapkInstaller
import rikka.shizuku.ShizukuBinderWrapper
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Shizuku APK 安装辅助类。
 *
 * 主方案使用 [ShizukuBinderWrapper] 包装 IPackageInstaller Binder，
 * 直接通过 Android PackageInstaller.Session API 安装（与 InstallerX 一致）。
 * 这样可以支持 setOriginatingUid（请求者参数）。
 *
 * 回退方案保留 shell pm 命令方式（无 originating UID 支持）。
 */
object ShizukuInstallHelper {

    interface InstallCallback {
        fun onProgress(message: String)
        fun onSuccess(message: String)
        fun onError(error: String)
    }

    // ==================== Shell 命令方法（回退方案 + 其他用途） ====================

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

    @Throws(Exception::class)
    fun executeCommand(context: Context, command: String): String {
        return executeCommand(command)
    }

    // ==================== Binder-based Session API（主方案） ====================

    /**
     * 安装单个 APK。
     * 优先使用 ShizukuBinderWrapper + PackageInstaller.Session API，
     * 失败时回退到 shell 命令方式。
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
                callback.onProgress("Starting APK installation (Shizuku Binder)...")

                // 主方案：通过 ShizukuBinderWrapper + PackageInstaller.Session API
                installViaPackageInstaller(context, listOf(apkFile), callback)
                callback.onSuccess("Installation successful!")
            } catch (binderError: Exception) {
                android.util.Log.w("ShizukuInstallHelper",
                    "Binder approach failed, falling back to shell: ${binderError.message}")
                try {
                    installSingleApkViaShell(context, apkFile, replaceExisting, grantPermissions, callback)
                } catch (shellError: Exception) {
                    callback.onError("Install exception: ${shellError.message}")
                }
            }
        }.start()
    }

    /**
     * 安装 XAPK (多个 APK)。
     * 优先使用 ShizukuBinderWrapper + PackageInstaller.Session API。
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

                // 主方案：通过 ShizukuBinderWrapper + PackageInstaller.Session API
                installViaPackageInstaller(context, extractedApks, callback)
                callback.onSuccess("XAPK installation successful! ${extractedApks.size} APKs installed")
            } catch (binderError: Exception) {
                android.util.Log.w("ShizukuInstallHelper",
                    "Binder approach failed, falling back to shell: ${binderError.message}")
                try {
                    installXapkViaShell(context, xapkPath, replaceExisting, grantPermissions, callback)
                } catch (shellError: Exception) {
                    callback.onError("XAPK install exception: ${shellError.message}")
                }
            } finally {
                extractedApks?.let { XapkInstaller.cleanupTempFiles(it) }
            }
        }.start()
    }

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

    // ==================== Binder-based PackageInstaller.Session 实现 ====================

    /**
     * 通过 ShizukuBinderWrapper 包装 IPackageInstaller Binder，
     * 使用 PackageInstaller.Session API 安装 APK。
     *
     * 设计参考：InstallerX-Revived 的 ShizukuAppInstallerRepoImpl / IBinderAppInstallerRepoImpl
     */
    @Throws(Exception::class)
    private fun installViaPackageInstaller(
        context: Context,
        apkFiles: List<File>,
        callback: InstallCallback
    ) {
        callback.onProgress("Using Shizuku Binder approach...")

        // 1. 获取 IPackageInstaller（通过 ShizukuBinderWrapper）
        val packageInstaller = getPackageInstaller(context)

        // 2. 创建 SessionParams
        val params = if (apkFiles.size == 1) {
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        } else {
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_INHERIT_EXISTING)
        }

        // 3. 设置安装标志
        var flags = 0
        // INSTALL_REPLACE_EXISTING = 0x00000002
        flags = flags or 0x00000002
        if (isAllowTestPackages(context)) {
            // INSTALL_ALLOW_TEST = 0x00000004
            flags = flags or 0x00000004
        }
        val installFlagsField = PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")
        installFlagsField.isAccessible = true
        installFlagsField.setInt(params, flags)

        // 4. 设置安装者包名
        val installerPackage = getInstallerPackage(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && installerPackage.isNotEmpty()) {
            try {
                val setInstallerMethod = PackageInstaller.SessionParams::class.java
                    .getDeclaredMethod("setInstallerPackageName", String::class.java)
                setInstallerMethod.isAccessible = true
                setInstallerMethod.invoke(params, installerPackage)
                callback.onProgress("Installer: $installerPackage")
            } catch (_: Exception) {}
        }

        // 5. 设置请求者 UID（originating Uid）—— 与 InstallerX 一致
        applyRequesterUid(context, params, callback)

        // 6. 设置 APK 大小
        val totalSize = apkFiles.sumOf { it.length() }
        params.setSize(totalSize)

        callback.onProgress("Creating install session via Shizuku Binder...")

        // 7. 创建会话
        val sessionId = packageInstaller.createSession(params)
        callback.onProgress("Session ID: $sessionId")

        // 8. 打开会话并包装 session 内部 Binder（关键！否则 write/commit 会因权限不足失败）
        val session = packageInstaller.openSession(sessionId)
        wrapSessionBinder(session)

        // 9. 写入 APK 数据
        for ((index, apkFile) in apkFiles.withIndex()) {
            val name = apkFile.name
            callback.onProgress("[${index + 1}/${apkFiles.size}] Writing $name...")
            session.openWrite(name, 0, apkFile.length()).use { outputStream ->
                FileInputStream(apkFile).use { input ->
                    input.copyTo(outputStream)
                }
                session.fsync(outputStream)
            }
        }

        // 9. 提交安装
        callback.onProgress("Submitting install...")
        val receiver = LocalIntentReceiver()
        session.commit(receiver.getIntentSender())

        val resultIntent = receiver.getResult()
        val status = resultIntent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )

        if (status != PackageInstaller.STATUS_SUCCESS) {
            val msg = resultIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            throw Exception("Install failed (status=$status): $msg")
        }
    }

    /**
     * 获取通过 ShizukuBinderWrapper 包装的 PackageInstaller 实例。
     */
    @Throws(Exception::class)
    private fun getPackageInstaller(context: Context): PackageInstaller {
        // 获取 package 服务并包装 Binder
        val packageBinder = ServiceManager.getService("package")
        val wrappedBinder = ShizukuBinderWrapper(packageBinder)
        val packageManager = IPackageManager.Stub.asInterface(wrappedBinder)

        // 获取 IPackageInstaller 并包装
        val iPackageInstaller = IPackageInstaller.Stub.asInterface(
            ShizukuBinderWrapper(packageManager.packageInstaller.asBinder())
        )

        // 获取 installer package name（用于 PackageInstaller 构造函数）
        val installerPackageName = getInstallerPackage(context).ifEmpty { "com.android.shell" }
        val userId = android.os.Process.myUid() / 100000

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ctor = PackageInstaller::class.java.getDeclaredConstructor(
                IPackageInstaller::class.java,
                String::class.java,
                String::class.java,
                Int::class.java
            )
            ctor.isAccessible = true
            ctor.newInstance(iPackageInstaller, installerPackageName, null, userId)
        } else {
            val ctor = PackageInstaller::class.java.getDeclaredConstructor(
                IPackageInstaller::class.java,
                String::class.java,
                Int::class.java
            )
            ctor.isAccessible = true
            ctor.newInstance(iPackageInstaller, installerPackageName, userId)
        }
    }

    /**
     * 根据用户配置的请求者包名，设置 SessionParams 的 originating UID。
     * 与 InstallerX 的 config.callingFromUid?.let { params.setOriginatingUid(it) } 一致。
     */
    private fun applyRequesterUid(
        context: Context,
        params: PackageInstaller.SessionParams,
        callback: InstallCallback
    ) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val enableCustomRequester = prefs.getBoolean("enable_custom_requester_package", false)

        if (!enableCustomRequester) {
            return
        }

        val requesterPackage = prefs.getString("requester_package", null)?.ifEmpty { null } ?: return

        try {
            val uid = context.packageManager.getPackageUid(requesterPackage, 0)
            if (uid < 0) {
                callback.onProgress("Requester: $requesterPackage UID not found (not installed?)")
                return
            }

            // 方式一：直接设置 originatingUid 字段（public @hide field）
            try {
                val originatingUidField = PackageInstaller.SessionParams::class.java
                    .getDeclaredField("originatingUid")
                originatingUidField.isAccessible = true
                originatingUidField.setInt(params, uid)
                callback.onProgress("Requester UID set: $requesterPackage -> uid=$uid")
                return
            } catch (_: Exception) {}

            // 方式二：回退到 setOriginatingUid 方法
            try {
                val method = PackageInstaller.SessionParams::class.java
                    .getDeclaredMethod("setOriginatingUid", Int::class.javaPrimitiveType!!)
                method.isAccessible = true
                method.invoke(params, uid)
                callback.onProgress("Requester UID set: $requesterPackage -> uid=$uid")
            } catch (e: Exception) {
                callback.onProgress("Requester: setOriginatingUid not available: ${e.message}")
            }
        } catch (e: PackageManager.NameNotFoundException) {
            callback.onProgress("Requester: '$requesterPackage' not installed on this device")
        } catch (e: Exception) {
            callback.onProgress("Requester: failed for $requesterPackage: ${e.message}")
        }
    }

    /**
     * 通过反射替换 Session 内部的 mSession Binder，
     * 使其操作也经过 ShizukuBinderWrapper（确保 write/commit 等操作拥有系统权限）。
     *
     * 与 InstallerX 的 setSessionIBinder 一致。
     */
    @Throws(Exception::class)
    private fun wrapSessionBinder(session: PackageInstaller.Session) {
        val sessionClass = session.javaClass
        val mSessionField = sessionClass.getDeclaredField("mSession")
        mSessionField.isAccessible = true
        val originalInterface = mSessionField.get(session) as? android.os.IInterface ?: return
        val originalBinder = originalInterface.asBinder()
        val wrappedBinder = ShizukuBinderWrapper(originalBinder)
        val wrappedSession = android.content.pm.IPackageInstallerSession.Stub.asInterface(wrappedBinder)
        mSessionField.set(session, wrappedSession)
    }

    // ==================== Shell 命令回退方案 ====================

    @Throws(Exception::class)
    private fun installSingleApkViaShell(
        context: Context,
        apkFile: File,
        replaceExisting: Boolean,
        grantPermissions: Boolean,
        callback: InstallCallback
    ) {
        callback.onProgress("Falling back to shell pm install...")

        val createCmd = StringBuilder("pm install-create --user 0")
        if (replaceExisting) createCmd.append(" -r")
        if (grantPermissions) createCmd.append(" -g")
        if (isAllowTestPackages(context)) createCmd.append(" -t")

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

        val writeCmd = "pm install-write -S ${apkFile.length()} $sessionId base.apk -"
        callback.onProgress("Writing APK data...")
        val writeOutput = executeCommandWithInput(writeCmd, apkFile)

        if (!writeOutput.contains("Success")) {
            throw Exception("Install failed: $writeOutput")
        }

        callback.onProgress("Submitting install...")
        val commitOutput = executeCommand(context, "pm install-commit $sessionId")

        if (!commitOutput.lowercase().contains("success")) {
            throw Exception("Install failed: $commitOutput")
        }
    }

    @Throws(Exception::class)
    private fun installXapkViaShell(
        context: Context,
        xapkPath: String,
        replaceExisting: Boolean,
        grantPermissions: Boolean,
        callback: InstallCallback
    ) {
        val extractedApks = XapkInstaller.extractXapk(context, xapkPath)
        callback.onProgress("Extraction complete, ${extractedApks.size} APKs found (shell fallback)")

        val createCmd = StringBuilder("pm install-create --user 0")
        if (replaceExisting) createCmd.append(" -r")
        if (grantPermissions) createCmd.append(" -g")
        if (isAllowTestPackages(context)) createCmd.append(" -t")

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

        for ((index, apkFile) in extractedApks.withIndex()) {
            callback.onProgress("[${index + 1}/${extractedApks.size}] ${apkFile.name}")
            val writeCmd = "pm install-write -S ${apkFile.length()} $sessionId ${apkFile.name} -"
            val writeOutput = executeCommandWithInput(writeCmd, apkFile)
            if (!writeOutput.contains("Success")) {
                throw Exception("Install failed: ${apkFile.name} failed: $writeOutput")
            }
        }

        callback.onProgress("Submitting install...")
        val commitOutput = executeCommand(context, "pm install-commit $sessionId")

        if (!commitOutput.lowercase().contains("success")) {
            throw Exception("Install failed: $commitOutput")
        }
    }

    // ==================== 配置读取 ====================

    private fun isAllowTestPackages(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("allow_test_packages", false)
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
