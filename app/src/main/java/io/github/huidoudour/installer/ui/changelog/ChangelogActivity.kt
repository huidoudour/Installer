package io.github.huidoudour.installer.ui.changelog

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import io.github.huidoudour.installer.ui.theme.AppTheme
import io.github.huidoudour.installer.util.LanguageManager
import io.github.huidoudour.installer.util.ThemeManager

/**
 * 更新日志 Activity - 独立全屏页面
 */
class ChangelogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 应用用户选择的主题与语言
        ThemeManager.applyUserThemePreference(this)
        LanguageManager.applyUserLanguagePreference(this)

        super.onCreate(savedInstanceState)

        // 允许 Compose 自行处理系统栏内边距
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 注册返回键回调
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        setContent {
            AppTheme {
                ChangelogScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}
