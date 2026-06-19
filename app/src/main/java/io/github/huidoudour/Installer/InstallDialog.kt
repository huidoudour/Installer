package io.github.huidoudour.Installer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import io.github.huidoudour.Installer.ui.InstallDialog
import io.github.huidoudour.Installer.util.LanguageManager
import io.github.huidoudour.Installer.util.ShellExecutor
import io.github.huidoudour.Installer.util.ThemeManager

/**
 * 独立的安装对话框 Activity
 * 用于处理来自外部的 APK 安装请求
 * 完全独立于 MainActivity，不显示底部导航栏等 App UI
 */
class InstallDialogActivity : AppCompatActivity() {

    private var installUri: Uri? = null

    /**
     * 支持的安装文件 MIME 类型
     */
    private val SUPPORTED_MIME_TYPES = setOf(
        "application/vnd.android.package-archive",
        "application/vnd.apkm",
        "application/x-xapk",
        "application/octet-stream"
    )

    /**
     * 支持的安装文件扩展名
     */
    private val SUPPORTED_EXTENSIONS = setOf("apk", "xapk", "apks", "apkm")

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyUserThemePreference(this)
        LanguageManager.applyUserLanguagePreference(this)

        super.onCreate(savedInstanceState)

        // 设置透明背景，让 Dialog 可以正常显示
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 获取并验证安装 URI
        installUri = intent.data
        val errorResId = validateInstallUri(installUri)

        if (errorResId != 0) {
            // URI 无效或文件不支持，显示提示并关闭
            Log.w("InstallDialog", "Validation failed for URI: $installUri, errorResId=$errorResId")
            Toast.makeText(this, getString(errorResId), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            if (installUri != null) {
                InstallDialog(
                    installUri = installUri,
                    onDismiss = {
                        finish()
                    },
                    onInstallComplete = {
                        finish()
                    },
                    onOpenApp = { packageName ->
                        // 使用 Shizuku Shell 启动应用，绕过系统链式启动拦截
                        launchAppViaShizuku(packageName)
                    }
                )
            } else {
                finish()
            }
        }
    }

    /**
     * 验证安装 URI 的有效性
     * @return 0 表示有效，非 0 表示对应的错误字符串资源 ID
     */
    private fun validateInstallUri(uri: Uri?): Int {
        // 1. URI 空检查
        if (uri == null) {
            Log.w("InstallDialog", "URI is null")
            return R.string.invalid_install_request
        }

        // 2. URI scheme 检查（仅允许 file 和 content）
        val scheme = uri.scheme
        if (scheme != "file" && scheme != "content") {
            Log.w("InstallDialog", "Unsupported URI scheme: $scheme")
            return R.string.unsupported_file_type
        }

        // 3. MIME 类型检查
        val mimeType = contentResolver.getType(uri)
        Log.d("InstallDialog", "URI MIME type: $mimeType, URI: $uri")
        val isMimeSupported = mimeType != null && SUPPORTED_MIME_TYPES.any {
            mimeType.equals(it, ignoreCase = true)
        }

        // 4. 文件扩展名检查（作为 MIME 的补充/降级）
        val fileName = getFileNameFromUri(uri)
        val extension = fileName?.substringAfterLast('.', "")?.lowercase()
        val isExtensionSupported = !extension.isNullOrEmpty() && SUPPORTED_EXTENSIONS.contains(extension)

        // MIME 或扩展名任一匹配即可通过
        if (!isMimeSupported && !isExtensionSupported) {
            Log.w("InstallDialog", "Unsupported file type: mime=$mimeType, ext=$extension, file=$fileName")
            return R.string.unsupported_file_type
        }

        // 5. 文件可访问性检查
        if (!isUriAccessible(uri)) {
            Log.w("InstallDialog", "URI is not accessible: $uri")
            return R.string.cannot_access_install_file
        }

        Log.d("InstallDialog", "URI validation passed: mime=$mimeType, ext=$extension, file=$fileName")
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
                    path != null && java.io.File(path).let { it.exists() && it.canRead() }
                }
                "content" -> {
                    contentResolver.openInputStream(uri)?.use { true } ?: false
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e("InstallDialog", "URI accessibility check failed", e)
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
                
                // 检查 Shizuku 是否可用
                val shizukuAvailable = ShellExecutor.isShizukuAvailable()
                
                if (shizukuAvailable) {
                    // 使用 Shizuku 执行 am start 命令
                    val command = "am start -n $(pm resolve-activity --components $packageName | tail -n 1)"
                    
                    Log.d("InstallDialog", "Executing shell command: $command")
                    
                    ShellExecutor.executeShizukuCommand(command, object : ShellExecutor.ExecuteCallback {
                        override fun onOutput(line: String) {
                            Log.d("InstallDialog", "Shell output: $line")
                        }
                        
                        override fun onError(error: String) {
                            Log.e("InstallDialog", "Shell error: $error")
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
                    runOnUiThread {
                        try {
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                startActivity(launchIntent)
                            }
                        } catch (e: Exception) {
                            Log.e("InstallDialog", "Traditional launch failed", e)
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
        installUri = intent.data
    }
}
