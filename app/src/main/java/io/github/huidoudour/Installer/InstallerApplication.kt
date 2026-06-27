package io.github.huidoudour.Installer

import android.app.Application
import android.os.Build
import io.github.huidoudour.Installer.util.LanguageManager
import io.github.huidoudour.Installer.util.LogManager
import io.github.huidoudour.Installer.util.NativeCrashHandler
import io.github.huidoudour.Installer.util.PrivilegeHelper
import io.github.huidoudour.Installer.util.ThemeManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 应用 Application 类
 */
class InstallerApplication : Application() {

    private lateinit var crashHandler: NativeCrashHandler

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 解除 Android 隐藏 API 限制（必须最早执行，让后续所有代码都能访问隐藏 API）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }

        // 初始化崩溃处理器
        crashHandler = NativeCrashHandler(this)

        // 初始化日志管理器
        LogManager.getInstance().setContext(this)

        // 初始化权限系统
        PrivilegeHelper.initialize(this)

        // 应用保存的主题
        ThemeManager.applyUserThemePreference(this)

        // 应用保存的语言
        LanguageManager.applyUserLanguagePreference(this)
    }

    override fun onTerminate() {
        super.onTerminate()
    }

    companion object {
        lateinit var instance: InstallerApplication
            private set

        fun getAppContext(): Application = instance
    }
}
