package io.github.huidoudour.Installer

import android.app.Application
import io.github.huidoudour.Installer.util.LanguageManager
import io.github.huidoudour.Installer.util.LogManager
import io.github.huidoudour.Installer.util.NativeCrashHandler
import io.github.huidoudour.Installer.util.PrivilegeHelper
import io.github.huidoudour.Installer.util.ThemeManager

/**
 * 应用 Application 类
 */
class InstallerApplication : Application() {

    private lateinit var crashHandler: NativeCrashHandler

    override fun onCreate() {
        super.onCreate()
        instance = this

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
