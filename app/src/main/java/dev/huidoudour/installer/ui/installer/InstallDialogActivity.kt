package dev.huidoudour.installer.ui.installer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import dev.huidoudour.terminal.ShellExecutor
import dev.huidoudour.installer.ui.theme.AppTheme
import dev.huidoudour.installer.util.LanguageManager
import dev.huidoudour.installer.util.LogManager
import dev.huidoudour.installer.util.ThemeManager
import dev.huidoudour.installer.R
import java.io.File

/**
 * 独立的安装对话框 Activity
 * 用于处理来自外部的 APK 安装请求
 * 完全独立于 MainActivity，不显示底部导航栏等 App UI
 */
class InstallDialogActivity : AppCompatActivity() {

    // 使用 Compose 快照状态：对话框已打开时收到新的安装请求可直接切换数据源，
    // 由 key(installUri) 重建界面重新解析应用信息
    private var installUri by mutableStateOf<Uri?>(null)
    private val logManager = LogManager.getInstance()

    private fun logInstall(message: String) {
        logManager.addLog(message, "External install")
    }

    /**
     * 支持的安装文件 MIME 类型
     */
    private val supportedMimeTypes = setOf(
        "application/vnd.android.package-archive",
        "application/vnd.apkm",
        "application/vnd.apks",
        "application/vnd.xapk",
        "application/x-xapk",
        "application/x-apkm",
        "application/zip",
        "application/x-zip",
        "application/x-zip-compressed",
        "application/octet-stream"
    )

    /**
     * 支持的安装文件扩展名
     */
    private val supportedExtensions = setOf("apk", "xapk", "apks", "apkm", "zip")

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyUserThemePreference(this)
        LanguageManager.applyUserLanguagePreference(this)

        super.onCreate(savedInstanceState)

        // 设置透明背景，让 Dialog 可以正常显示
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 获取并验证安装 URI
        installUri = resolveInstallUri(intent)
        logInstall("Received external install request: $installUri")
        val errorResId = validateInstallUri(installUri)

        if (errorResId != 0) {
            // URI 无效或文件不支持，显示提示并关闭
            Log.w("InstallDialog", "Validation failed for URI: $installUri, errorResId=$errorResId")
            logInstall("Install request rejected: ${getString(errorResId)}")
            Toast.makeText(this, getString(errorResId), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            AppTheme {
                if (installUri != null) {
                    key(installUri) {
                        InstallDialog(
                            installUri = installUri,
                            onDismiss = {
                                logInstall("External install dialog closed")
                                finish()
                            },
                            onOpenApp = { packageName ->
                                // 使用 Shizuku Shell 启动应用，绕过系统链式启动拦截
                                launchAppViaShizuku(packageName)
                            }
                        )
                    }
                } else {
                    logInstall("Install request has no URI")
                    finish()
                }
            }
        }
    }

    /**
     * 从启动 Intent 中提取安装文件 URI。
     *
     * FileManager 等来源可能通过 `data`、`EXTRA_STREAM` 或 `ClipData` 传递文件，
     * 三种方式都兼容，避免带 zip 的分享请求被丢掉。
     */
    private fun resolveInstallUri(intent: Intent?): Uri? {
        if (intent == null) return null

        intent.data?.let { return it }

        intent.clipData?.let { clip ->
            if (clip.itemCount > 0) {
                clip.getItemAt(0).uri?.let { return it }
            }
        }

        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }

    /**
     * 验证安装 URI 的有效性
     * @return 0 表示有效，非 0 表示对应的错误字符串资源 ID
     */
    private fun validateInstallUri(uri: Uri?): Int {
        // 1. URI 空检查
        if (uri == null) {
            Log.w("InstallDialog", "URI is null")
            logInstall("URI validation failed: URI is null")
            return R.string.invalid_install_request
        }

        // 2. URI scheme 检查（仅允许 file 和 content）
        val scheme = uri.scheme
        if (scheme != "file" && scheme != "content") {
            Log.w("InstallDialog", "Unsupported URI scheme: $scheme")
            logInstall("URI validation failed: unsupported scheme $scheme")
            return R.string.unsupported_file_type
        }

        // 3. MIME 类型检查
        val mimeType = contentResolver.getType(uri)
        Log.d("InstallDialog", "URI MIME type: $mimeType, URI: $uri")
        val isMimeSupported = mimeType != null && supportedMimeTypes.any {
            mimeType.equals(it, ignoreCase = true)
        }

        // 4. 文件扩展名检查（作为 MIME 的补充/降级）
        val fileName = getFileNameFromUri(uri)
        val extension = fileName?.substringAfterLast('.', "")?.lowercase()
        val isExtensionSupported = !extension.isNullOrEmpty() && supportedExtensions.contains(extension)

        // MIME 或扩展名任一匹配即可通过
        if (!isMimeSupported && !isExtensionSupported) {
            Log.w("InstallDialog", "Unsupported file type: mime=$mimeType, ext=$extension, file=$fileName")
            logInstall("URI validation failed: unsupported file $fileName (MIME $mimeType)")
            return R.string.unsupported_file_type
        }

        // 5. 文件可访问性检查
        if (!isUriAccessible(uri)) {
            Log.w("InstallDialog", "URI is not accessible: $uri")
            logInstall("URI validation failed: cannot access $uri")
            return R.string.cannot_access_install_file
        }

        Log.d("InstallDialog", "URI validation passed: mime=$mimeType, ext=$extension, file=$fileName")
        logInstall("URI validated: file=$fileName, MIME=$mimeType")
        return 0
    }

    /**
     * 从 URI 获取文件名
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> uri.lastPathSegment
                "content" -> {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) cursor.getString(nameIndex) else null
                        } else null
                    }
                }
                else -> uri.lastPathSegment
            }
        } catch (e: Exception) {
            Log.e("InstallDialog", "Failed to get file name from URI", e)
            logInstall("Failed to read installation file name: ${e.message}")
            null
        }
    }

    /**
     * 检查 URI 对应的文件是否可访问
     */
    private fun isUriAccessible(uri: Uri): Boolean {
        return try {
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path
                    path != null && File(path).let { it.exists() && it.canRead() }
                }
                "content" -> {
                    contentResolver.openInputStream(uri)?.use { true } ?: false
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e("InstallDialog", "URI accessibility check failed", e)
            logInstall("Failed to check installation file access: ${e.message}")
            false
        }
    }

    /**
     * 通过 Shizuku Shell 启动应用
     * 使用 am start 命令绕过系统的链式启动拦截
     */
    private fun launchAppViaShizuku(packageName: String) {
        Thread {
            try {
                Log.d("InstallDialog", "Launching app via Shizuku Shell: $packageName")
                logInstall("Opening installed app: $packageName")
                
                // 检查 Shizuku 是否可用
                val shizukuAvailable = ShellExecutor.isShizukuAvailable()
                
                if (shizukuAvailable) {
                    // 使用 Shizuku 执行 am start 命令
                    val command = "am start -n $(pm resolve-activity --components $packageName | tail -n 1)"
                    
                    Log.d("InstallDialog", "Executing shell command: $command")
                    logInstall("Starting app through Shizuku shell")
                    
                    ShellExecutor.executeShizukuCommand(command, object : ShellExecutor.ExecuteCallback {
                        override fun onOutput(line: String) {
                            Log.d("InstallDialog", "Shell output: $line")
                            logInstall("App launch output: $line")
                        }
                        
                        override fun onError(error: String) {
                            Log.e("InstallDialog", "Shell error: $error")
                            logInstall("App launch failed: $error")
                            runOnUiThread {
                                Toast.makeText(
                                    this@InstallDialogActivity,
                                    getString(R.string.shizuku_shell_launch_failed, error),
                                    Toast.LENGTH_LONG
                                ).show()
                                finish()
                            }
                        }
                        
                        override fun onComplete(exitCode: Int) {
                            Log.d("InstallDialog", "Shell command result, exit code: $exitCode")
                            logInstall("App launch completed with exit code $exitCode")
                            runOnUiThread {
                                if (exitCode == 0) {
                                    Toast.makeText(
                                        this@InstallDialogActivity,
                                        getString(R.string.app_launched_success, packageName),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                finish()
                            }
                        }
                    })
                } else {
                    // Shizuku 不可用，降级为传统方式
                    Log.w("InstallDialog", "Shizuku unavailable, falling back to traditional launch")
                    logInstall("Shizuku unavailable; falling back to Android app launch")
                    runOnUiThread {
                        try {
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                startActivity(launchIntent)
                                logInstall("Started app with Android launcher")
                            } else {
                                logInstall("No launch activity found for $packageName")
                            }
                        } catch (e: Exception) {
                            Log.e("InstallDialog", "Traditional launch failed", e)
                            logInstall("Android app launch failed: ${e.message}")
                            Toast.makeText(
                                this@InstallDialogActivity,
                                getString(R.string.launch_app_via_shizuku_failed, e.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e("InstallDialog", "Launch app failed", e)
                logInstall("Could not open installed app: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@InstallDialogActivity,
                        getString(R.string.launch_app_via_shizuku_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUri = resolveInstallUri(intent)
        logInstall("Received updated external install request: $newUri")
        if (newUri == null || newUri == installUri) return

        // 对话框已打开时又收到新的安装请求：校验通过后切换数据源，
        // 由 key(installUri) 重建界面并重新解析应用信息
        val errorResId = validateInstallUri(newUri)
        if (errorResId != 0) {
            Log.w("InstallDialog", "Validation failed for new URI: $newUri, errorResId=$errorResId")
            logInstall("Install request rejected: ${getString(errorResId)}")
            Toast.makeText(this, getString(errorResId), Toast.LENGTH_SHORT).show()
            return
        }

        installUri = newUri
    }
}
