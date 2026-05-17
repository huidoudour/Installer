package io.github.huidoudour.Installer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
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
class InstallDialogActivity : ComponentActivity() {

    private var installUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyUserThemePreference(this)
        LanguageManager.applyUserLanguagePreference(this)

        super.onCreate(savedInstanceState)

        // 设置透明背景，让 Dialog 可以正常显示
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 获取安装 URI
        installUri = intent.data

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
                // 如果没有 URI，直接关闭
                finish()
            }
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
