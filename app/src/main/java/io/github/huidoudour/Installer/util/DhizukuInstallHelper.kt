package io.github.huidoudour.Installer.util

import android.content.Context
import android.content.Intent
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageManager
import android.content.pm.PackageInstaller
import android.os.IBinder
import android.os.ServiceManager
import com.rosan.dhizuku.api.Dhizuku
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Dhizuku APK 安装辅助类。
 *
 * 主方案使用 [Dhizuku.binderWrapper] 包装 IPackageInstaller Binder，
 * 直接通过 Android PackageInstaller.Session API 安装，无需 shell 命令。
 * 这是 InstallerX-Revived 采用的方式，彻底规避华为设备上 pm 命令的 NPE/SecurityException。
 *
 * 回退方案保留 shell 命令方式，添加 -i 参数指定安装者身份。
 */
object DhizukuInstallHelper {

    interface InstallCallback {
        fun onProgress(message: String)
        fun onSuccess(message: String)
        fun onError(error: String)
    }

    /**
     * 执行 Dhizuku shell 命令（回退方案使用）
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
     * 安装单个 APK。
     * 优先使用 IBinder + PackageInstaller.Session API（同 InstallerX），
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
                callback.onProgress("Starting APK installation...")

                if (!Dhizuku.init(context.applicationContext)) {
                    throw Exception("Dhizuku not initialized")
                }

                // 主方案：通过 IBinder 包装直接使用 PackageInstaller.Session API
                installViaPackageInstaller(context, apkFile, replaceExisting, grantPermissions, callback)
            } catch (binderError: Exception) {
                // 回退方案：shell 命令（带 -i 参数修复华为 NPE）
                android.util.Log.w("DhizukuInstallHelper",
                    "Binder approach failed, falling back to shell: ${binderError.message}")
                try {
                    installViaShell(context, apkFile, replaceExisting, grantPermissions, callback)
                } catch (shellError: Exception) {
                    callback.onError("Install exception: ${shellError.message}")
                }
            }
        }.start()
    }

    // ==================== IBinder + PackageInstaller.Session 方案 ====================

    /**
     * 通过 Dhizuku.binderWrapper() 包装 IPackageInstaller Binder，
     * 直接使用 Android PackageInstaller.Session API 安装 APK。
     *
     * 设计参考：InstallerX-Revived 的 IBinderAppInstallerRepoImpl
     */
    @Throws(Exception::class)
    private fun installViaPackageInstaller(
        context: Context,
        apkFile: File,
        replaceExisting: Boolean,
        grantPermissions: Boolean,
        callback: InstallCallback
    ) {
        callback.onProgress("Using Dhizuku Binder approach...")

        // 1. 获取 package 服务并包装 Binder
        val packageBinder = ServiceManager.getService("package")
        val wrappedBinder = Dhizuku.binderWrapper(packageBinder)
        val packageManager = IPackageManager.Stub.asInterface(wrappedBinder)

        // 2. 获取 IPackageInstaller
        val iPackageInstaller = IPackageInstaller.Stub.asInterface(
            Dhizuku.binderWrapper(packageManager.packageInstaller.asBinder())
        )

        // 3. 通过反射创建 PackageInstaller 实例（隐藏构造函数）
        val installerPackageName = "com.rosan.dhizuku"
        val userId = android.os.Process.myUid() / 100000

        val packageInstaller: PackageInstaller = try {
            // Android 12+ 四参数构造函数
            val ctor = PackageInstaller::class.java.getDeclaredConstructor(
                IPackageInstaller::class.java,
                String::class.java,
                String::class.java,
                Int::class.java
            )
            ctor.isAccessible = true
            ctor.newInstance(iPackageInstaller, installerPackageName, null, userId)
        } catch (e: NoSuchMethodException) {
            // Android 11 三参数构造函数
            val ctor = PackageInstaller::class.java.getDeclaredConstructor(
                IPackageInstaller::class.java,
                String::class.java,
                Int::class.java
            )
            ctor.isAccessible = true
            ctor.newInstance(iPackageInstaller, installerPackageName, userId)
        }

        // 4. 创建 SessionParams
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )

        // 设置安装标志
        var flags = 0
        if (replaceExisting) {
            // PackageManagerHidden.INSTALL_REPLACE_EXISTING = 0x00000002
            flags = flags or 0x00000002
        }
        if (grantPermissions) {
            // InstallOption.GrantAllRequestedPermissions = 0x00000040
            flags = flags or 0x00000040
        }

        // 通过反射设置 installFlags（隐藏字段）
        val installFlagsField = PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")
        installFlagsField.isAccessible = true
        installFlagsField.setInt(params, flags)

        // 设置安装者包名（API 34+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val setInstallerMethod = PackageInstaller.SessionParams::class.java
                    .getDeclaredMethod("setInstallerPackageName", String::class.java)
                setInstallerMethod.isAccessible = true
                setInstallerMethod.invoke(params, installerPackageName)
            } catch (_: Exception) {
                // 忽略，部分设备可能不支持
            }
        }

        // 设置 APK 大小
        params.setSize(apkFile.length())

        callback.onProgress("Creating install session via Binder...")

        // 5. 创建会话
        val sessionId = packageInstaller.createSession(params)
        callback.onProgress("Session ID: $sessionId")

        // 6. 通过反射替换 session 内的 Binder（关键步骤：确保 session 操作也走 Dhizuku）
        val session = packageInstaller.openSession(sessionId)
        wrapSessionBinder(session)

        // 7. 写入 APK 数据
        callback.onProgress("Writing APK data...")
        session.openWrite("base.apk", 0, apkFile.length()).use { outputStream ->
            FileInputStream(apkFile).use { input ->
                input.copyTo(outputStream)
            }
            session.fsync(outputStream)
        }

        // 8. 提交安装
        callback.onProgress("Submitting install...")
        val receiver = LocalIntentReceiver()
        session.commit(receiver.getIntentSender())

        // 9. 等待并验证结果
        val resultIntent = receiver.getResult()
        val status = resultIntent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )

        if (status == PackageInstaller.STATUS_SUCCESS) {
            callback.onSuccess("Installation successful!")
        } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // 需要用户确认（如安装未知来源应用），启动确认界面
            @Suppress("DEPRECATION")
            val confirmIntent = resultIntent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
            if (confirmIntent != null) {
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmIntent)
                // 重新等待结果
                val retryIntent = receiver.getResult()
                val retryStatus = retryIntent.getIntExtra(
                    PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE
                )
                if (retryStatus == PackageInstaller.STATUS_SUCCESS) {
                    callback.onSuccess("Installation successful!")
                } else {
                    val msg = retryIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    callback.onError("Install failed: $msg")
                }
            } else {
                val msg = resultIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                callback.onError("Install requires user action: $msg")
            }
        } else {
            val msg = resultIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            callback.onError("Install failed (status=$status): $msg")
        }
    }

    /**
     * 通过反射替换 Session 内部的 mSession Binder，
     * 使其操作也经过 Dhizuku 包装（确保 write/commit 等操作拥有设备所有者权限）。
     */
    @Throws(Exception::class)
    private fun wrapSessionBinder(session: PackageInstaller.Session) {
        val sessionClass = session.javaClass
        val mSessionField = sessionClass.getDeclaredField("mSession")
        mSessionField.isAccessible = true
        val originalBinder = (mSessionField.get(session) as? android.os.IInterface)?.asBinder()
            ?: return
        val wrappedBinder = Dhizuku.binderWrapper(originalBinder)
        val wrappedSession = android.content.pm.IPackageInstallerSession.Stub.asInterface(wrappedBinder)
        mSessionField.set(session, wrappedSession)
    }

    // ==================== Shell 命令回退方案 ====================

    /**
     * Shell 命令回退方案。
     * 优先尝试单命令 pm install（无 pipe 问题），失败后回退到会话模式。
     */
    @Throws(Exception::class)
    private fun installViaShell(
        context: Context,
        apkFile: File,
        replaceExisting: Boolean,
        grantPermissions: Boolean,
        callback: InstallCallback
    ) {
        if (!Dhizuku.init(context.applicationContext)) {
            throw Exception("Dhizuku not initialized")
        }

        // 方案 2a：单命令 pm install（最可靠，无 pipe 问题）
        try {
            callback.onProgress("Trying single-command install via shell...")
            installWithSingleCommand(context, apkFile, replaceExisting, grantPermissions)
            callback.onSuccess("Installation successful!")
            return
        } catch (e: Exception) {
            android.util.Log.w("DhizukuInstallHelper",
                "Single-command failed, trying session-based: ${e.message}")
        }

        // 方案 2b：会话模式 pm install-create / install-write / install-commit
        callback.onProgress("Trying session-based install via shell...")
        val apkSize = apkFile.length()

        // 构建 install-create 命令，添加 -S（总大小）和 -i 参数
        val createCmd = StringBuilder("pm install-create")
        createCmd.append(" -S $apkSize")
        createCmd.append(" --user 0")
        if (replaceExisting) createCmd.append(" -r")
        if (grantPermissions) createCmd.append(" -g")
        createCmd.append(" -i com.rosan.dhizuku")

        callback.onProgress("Creating install session (shell): $createCmd")
        val createOutput = executeCommand(context, createCmd.toString())

        if (!createOutput.contains("Success")) {
            throw Exception("Session create failed: $createOutput")
        }

        val sessionId = createOutput.substring(
            createOutput.indexOf("[") + 1,
            createOutput.indexOf("]")
        )
        callback.onProgress("Session ID: $sessionId")

        // 写入 APK — 直接通过 pm 进程 stdin（不用 sh -c 避免缓冲问题）
        val writeCmd = "pm install-write -S $apkSize $sessionId base.apk -"
        callback.onProgress("Writing APK data ($apkSize bytes)...")

        try {
            val process = Dhizuku.newProcess(arrayOf("pm", "install-write",
                "-S", apkSize.toString(), sessionId.toString(), "base.apk", "-"), null, null)
            val os = process.outputStream
            FileInputStream(apkFile).use { fis ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    os.write(buffer, 0, bytesRead)
                }
            }
            os.flush()
            os.close()

            val writeOutput = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0 || !writeOutput.contains("Success")) {
                throw Exception("Write failed (exit=$exitCode): $writeOutput")
            }
        } catch (e: java.io.IOException) {
            // EPIPE 等 IO 异常视为写失败
            throw Exception("Write failed (pipe error): ${e.message}")
        }

        // 提交安装
        callback.onProgress("Submitting install...")
        val commitOutput = executeCommand(context, "pm install-commit $sessionId")

        if (commitOutput.lowercase().contains("success")) {
            callback.onSuccess("Installation successful!")
        } else {
            callback.onError("Install failed: $commitOutput")
        }
    }

    /**
     * 单命令 `pm install` 回退方案。
     * 先尝试原始路径，失败后复制到 /data/local/tmp/ 重试。
     * 添加 -i 参数避免华为设备 NPE。
     */
    @Throws(Exception::class)
    private fun installWithSingleCommand(
        context: Context,
        apkFile: File,
        replaceExisting: Boolean,
        grantPermissions: Boolean
    ) {
        fun buildInstallCmd(path: String): String {
            val cmd = StringBuilder("pm install")
            if (replaceExisting) cmd.append(" -r")
            if (grantPermissions) cmd.append(" -g")
            cmd.append(" -i com.rosan.dhizuku")
            cmd.append(" --user 0")
            cmd.append(" \"$path\"")
            return cmd.toString()
        }

        // 尝试 1：原始文件路径（app 私有目录，Dhizuku shell 可能可读）
        val installOutput = executeCommand(context, buildInstallCmd(apkFile.absolutePath))
        if (installOutput.lowercase().contains("success")) {
            return
        }

        // 尝试 2：复制到 /data/local/tmp/ 后安装
        if (installOutput.contains("Unable to open file") ||
            installOutput.contains("Can't open file") ||
            installOutput.contains("No such file") ||
            installOutput.contains("Permission denied")) {

            val tempName = "install_${System.currentTimeMillis()}.apk"
            val tempPath = "/data/local/tmp/$tempName"

            val copyOutput = executeCommand(context,
                "cp \"${apkFile.absolutePath}\" $tempPath && chmod 644 $tempPath")
            if (copyOutput.isNotEmpty() && !copyOutput.lowercase().contains("error")) {
                // cp 成功（无输出或正常），也可能没有错误输出
            }

            try {
                val retryOutput = executeCommand(context, buildInstallCmd(tempPath))
                if (!retryOutput.lowercase().contains("success")) {
                    throw Exception("Install failed: $retryOutput")
                }
            } finally {
                executeCommand(context, "rm -f $tempPath")
            }
        } else {
            throw Exception("Install failed: $installOutput")
        }
    }

    // ==================== XAPK 安装 ====================

    /**
     * 安装 XAPK（多个 APK）。
     * 使用 IBinder + PackageInstaller.Session API。
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

                if (!Dhizuku.init(context.applicationContext)) {
                    throw Exception("Dhizuku not initialized")
                }

                // 1. 获取 PackageInstaller
                val packageBinder = ServiceManager.getService("package")
                val wrappedBinder = Dhizuku.binderWrapper(packageBinder)
                val packageManager = IPackageManager.Stub.asInterface(wrappedBinder)
                val iPackageInstaller = IPackageInstaller.Stub.asInterface(
                    Dhizuku.binderWrapper(packageManager.packageInstaller.asBinder())
                )

                val installerPackageName = "com.rosan.dhizuku"
                val userId = android.os.Process.myUid() / 100000

                val packageInstaller: PackageInstaller = try {
                    val ctor = PackageInstaller::class.java.getDeclaredConstructor(
                        IPackageInstaller::class.java, String::class.java, String::class.java, Int::class.java
                    )
                    ctor.isAccessible = true
                    ctor.newInstance(iPackageInstaller, installerPackageName, null, userId)
                } catch (e: NoSuchMethodException) {
                    val ctor = PackageInstaller::class.java.getDeclaredConstructor(
                        IPackageInstaller::class.java, String::class.java, Int::class.java
                    )
                    ctor.isAccessible = true
                    ctor.newInstance(iPackageInstaller, installerPackageName, userId)
                }

                // 2. SessionParams
                val params = PackageInstaller.SessionParams(
                    if (extractedApks.count { it.name == "base.apk" } == 1)
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL
                    else
                        PackageInstaller.SessionParams.MODE_INHERIT_EXISTING
                )

                var flags = 0
                if (replaceExisting) flags = flags or 0x00000002
                if (grantPermissions) flags = flags or 0x00000040
                val installFlagsField = PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")
                installFlagsField.isAccessible = true
                installFlagsField.setInt(params, flags)

                params.setSize(extractedApks.sumOf { it.length() })

                callback.onProgress("Creating install session via Binder...")
                val sessionId = packageInstaller.createSession(params)
                callback.onProgress("Session ID: $sessionId")

                val session = packageInstaller.openSession(sessionId)
                wrapSessionBinder(session)

                // 3. 写入所有 APK
                for ((index, apkFile) in extractedApks.withIndex()) {
                    callback.onProgress("[${index + 1}/${extractedApks.size}] ${apkFile.name}")
                    session.openWrite(apkFile.name, 0, apkFile.length()).use { outputStream ->
                        FileInputStream(apkFile).use { input ->
                            input.copyTo(outputStream)
                        }
                        session.fsync(outputStream)
                    }
                }

                // 4. 提交
                callback.onProgress("Submitting install...")
                val receiver = LocalIntentReceiver()
                session.commit(receiver.getIntentSender())

                val resultIntent = receiver.getResult()
                val status = resultIntent.getIntExtra(
                    PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE
                )

                if (status == PackageInstaller.STATUS_SUCCESS) {
                    callback.onSuccess("XAPK installation successful! ${extractedApks.size} APKs installed")
                } else {
                    val msg = resultIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    callback.onError("Install failed: $msg")
                }

            } catch (e: Exception) {
                callback.onError("XAPK install exception: ${e.message}")
            } finally {
                extractedApks?.let { XapkInstaller.cleanupTempFiles(it) }
            }
        }.start()
    }
}
