package io.github.huidoudour.Installer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import io.github.huidoudour.Installer.ui.theme.AppTheme
import io.github.huidoudour.Installer.util.LanguageManager

class MeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 应用用户选择的语言
        LanguageManager.applyUserLanguagePreference(this)

        super.onCreate(savedInstanceState)

        // 允许 Compose 自行处理系统栏内边距，等价于 XML 的 fitsSystemWindows="true"
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 注册返回键回调
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        setContent {
            AppTheme {
                MeScreen()
            }
        }
    }
}
