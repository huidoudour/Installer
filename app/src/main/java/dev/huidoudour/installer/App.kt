package dev.huidoudour.installer

import android.app.Application
import android.os.Build
import dev.huidoudour.installer.auth.PrivilegeHelper
import dev.huidoudour.installer.install.InstallCacheCleaner
import dev.huidoudour.installer.util.LanguageManager
import dev.huidoudour.installer.util.LogManager
import dev.huidoudour.installer.util.NativeCrashHandler
import dev.huidoudour.installer.util.ThemeManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 应用 Application 类
 */
class App : Application() {

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

        // 回收上一次运行遗留的安装临时文件：安装包副本动辄上百 MB，
        // 此时不可能有安装正在进行，是唯一可以安全整体清空的时机。
        val appContext = this
        Thread {
            val freed = InstallCacheCleaner.deleteStaleInstallFiles(appContext)
            if (freed > 0L) {
                LogManager.getInstance().addLog(
                    "Cleared stale install cache: ${InstallCacheCleaner.formatSize(appContext, freed)}",
                    "App"
                )
            }
        }.start()

        // 初始化权限系统
        PrivilegeHelper.initialize(this)

        // 应用保存的主题
        ThemeManager.applyUserThemePreference(this)

        // 应用保存的语言
        LanguageManager.applyUserLanguagePreference(this)
    }

    @SuppressWarnings("unused")
    override fun onTerminate() {
        super.onTerminate()
    }

    companion object {
        lateinit var instance: App
            private set

        @Suppress("unused")
        fun getAppContext(): Application = instance
    }
}
