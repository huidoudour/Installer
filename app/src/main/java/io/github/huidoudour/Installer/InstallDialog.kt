package io.github.huidoudour.Installer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import io.github.huidoudour.Installer.ui.InstallDialog
import io.github.huidoudour.Installer.util.LanguageManager
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
                        // 打开已安装的应用
                        try {
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                startActivity(launchIntent)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        finish()
                    }
                )
            } else {
                // 如果没有 URI，直接关闭
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        installUri = intent.data
    }
}
