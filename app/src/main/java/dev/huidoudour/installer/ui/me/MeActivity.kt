package dev.huidoudour.installer.ui.me

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import dev.huidoudour.installer.ui.theme.AppTheme
import dev.huidoudour.installer.util.LanguageManager

class MeActivity : AppCompatActivity() {

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
