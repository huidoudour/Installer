package io.github.huidoudour.Installer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.ui.theme.AppTheme
import io.github.huidoudour.Installer.util.LanguageManager

class MeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 应用用户选择的语言
        LanguageManager.applyUserLanguagePreference(this)

        super.onCreate(savedInstanceState)

        // 允许 Compose 自行处理系统栏内边距，等价于 XML 的 fitsSystemWindows="true"
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AppTheme {
                MeScreen()
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // 添加返回动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
