package io.github.huidoudour.Installer.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import com.rosan.dhizuku.api.Dhizuku
import io.github.huidoudour.Installer.R
import rikka.shizuku.Shizuku

/**
 * 统一的权限管理工具类
 * 支持 Shizuku 授权方式
 */
object PrivilegeHelper {

    private const val TAG = "PrivilegeHelper"

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val SHIZUKU_GITHUB_URL = "https://github.com/RikkaApps/Shizuku"

    private const val DHIZUKU_PACKAGE = "com.rosan.dhizuku"
    private const val DHIZUKU_GITHUB_URL = "https://github.com/iamr0s/Dhizuku"

    private const val PREFS_NAME = "privilege_settings"
    private const val KEY_CURRENT_MODE = "current_mode"

    enum class PrivilegeMode {
        SHIZUKU,
        DHIZUKU
    }

    enum class PrivilegeStatus {
        NOT_INSTALLED,      // 授权器未安装
        NOT_RUNNING,        // 授权器未运行
        NOT_AUTHORIZED,     // 未授权
        AUTHORIZED,         // 已授权
        VERSION_TOO_LOW    // 版本过低
    }

    /**
     * 检查指定授权器是否已安装
     */
    fun isInstalled(context: Context, mode: PrivilegeMode): Boolean {
        val packageName = when (mode) {
            PrivilegeMode.SHIZUKU -> SHIZUKU_PACKAGE
            PrivilegeMode.DHIZUKU -> DHIZUKU_PACKAGE
        }
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 检查 Shizuku 状态
     */
    fun checkShizukuStatus(): PrivilegeStatus {
        return try {
            if (!Shizuku.pingBinder()) {
                return PrivilegeStatus.NOT_RUNNING
            }

            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                return PrivilegeStatus.VERSION_TOO_LOW
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                PrivilegeStatus.AUTHORIZED
            } else {
                PrivilegeStatus.NOT_AUTHORIZED
            }
        } catch (e: Exception) {
            PrivilegeStatus.NOT_RUNNING
        }
    }

    /**
     * 检查 Dhizuku 状态
     */
    fun checkDhizukuStatus(context: Context): PrivilegeStatus {
        return try {
            if (!Dhizuku.init(context.applicationContext)) {
                return PrivilegeStatus.NOT_RUNNING
            }

            // 检查权限是否已授予
            val hasPermission = try {
                context.checkSelfPermission("com.rosan.dhizuku.permission.API") == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }

            if (hasPermission) {
                PrivilegeStatus.AUTHORIZED
            } else {
                // 尝试通过 DhizukuRemoteProcess 检查
                try {
                    val process = Dhizuku.newProcess(arrayOf("echo", "test"), null, null)
                    process?.destroy()
                    PrivilegeStatus.AUTHORIZED
                } catch (e: Exception) {
                    PrivilegeStatus.NOT_AUTHORIZED
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "checkDhizukuStatus error: ${e.message}")
            PrivilegeStatus.NOT_RUNNING
        }
    }

    /**
     * 获取指定授权器的状态
     */
    fun getStatus(context: Context, mode: PrivilegeMode): PrivilegeStatus {
        if (!isInstalled(context, mode)) {
            return PrivilegeStatus.NOT_INSTALLED
        }

        return when (mode) {
            PrivilegeMode.SHIZUKU -> checkShizukuStatus()
            PrivilegeMode.DHIZUKU -> checkDhizukuStatus(context)
        }
    }

    /**
     * 请求 Shizuku 授权
     */
    fun requestShizukuPermission(requestCode: Int) {
        try {
            if (Shizuku.pingBinder() &&
                !(Shizuku.isPreV11() || Shizuku.getVersion() < 11) &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 请求 Dhizuku 授权
     */
    fun requestDhizukuPermission(context: Context) {
        try {
            if (!Dhizuku.init(context.applicationContext)) {
                android.util.Log.e(TAG, "Dhizuku init failed when requesting permission")
                return
            }

            // Dhizuku 的权限请求需要通过 Intent
            val intent = android.content.Intent("moe.someone.test.Dhizuku")
            intent.setPackage(DHIZUKU_PACKAGE)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start Dhizuku permission activity: ${e.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error requesting Dhizuku permission: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 打开指定授权器应用
     */
    fun openPrivilegeApp(context: Context, mode: PrivilegeMode) {
        val packageName = when (mode) {
            PrivilegeMode.SHIZUKU -> SHIZUKU_PACKAGE
            PrivilegeMode.DHIZUKU -> DHIZUKU_PACKAGE
        }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }

    /**
     * 打开 GitHub 项目页面
     */
    fun openGithubPage(context: Context, mode: PrivilegeMode) {
        val url = when (mode) {
            PrivilegeMode.SHIZUKU -> SHIZUKU_GITHUB_URL
            PrivilegeMode.DHIZUKU -> DHIZUKU_GITHUB_URL
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * 获取状态描述文本
     */
    fun getStatusDescription(status: PrivilegeStatus, context: Context): String {
        return when (status) {
            PrivilegeStatus.NOT_INSTALLED -> context.getString(R.string.privilege_status_not_installed)
            PrivilegeStatus.NOT_RUNNING -> context.getString(R.string.privilege_status_not_running)
            PrivilegeStatus.NOT_AUTHORIZED -> context.getString(R.string.privilege_status_not_authorized)
            PrivilegeStatus.AUTHORIZED -> context.getString(R.string.privilege_status_authorized)
            PrivilegeStatus.VERSION_TOO_LOW -> context.getString(R.string.privilege_status_version_too_low)
            else -> context.getString(R.string.privilege_status_unknown)
        }
    }

    /**
     * 获取授权器名称
     */
    fun getModeName(mode: PrivilegeMode): String {
        return when (mode) {
            PrivilegeMode.SHIZUKU -> "Shizuku"
            PrivilegeMode.DHIZUKU -> "Dhizuku"
        }
    }

    /**
     * 保存当前选择的授权器模式
     */
    fun saveCurrentMode(context: Context, mode: PrivilegeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CURRENT_MODE, mode.name).apply()
    }

    /**
     * 获取当前选择的授权器模式
     */
    fun getCurrentMode(context: Context): PrivilegeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeName = prefs.getString(KEY_CURRENT_MODE, PrivilegeMode.SHIZUKU.name)
        return try {
            PrivilegeMode.valueOf(modeName ?: PrivilegeMode.SHIZUKU.name)
        } catch (e: IllegalArgumentException) {
            PrivilegeMode.SHIZUKU
        }
    }

    /**
     * 切换到另一个授权器
     */
    fun switchMode(context: Context): PrivilegeMode {
        val currentMode = getCurrentMode(context)
        val newMode = when (currentMode) {
            PrivilegeMode.SHIZUKU -> PrivilegeMode.DHIZUKU
            PrivilegeMode.DHIZUKU -> PrivilegeMode.SHIZUKU
        }
        saveCurrentMode(context, newMode)
        return newMode
    }

    /**
     * 初始化权限系统
     */
    fun initialize(context: Context) {
        val currentMode = getCurrentMode(context)
        getStatus(context, currentMode)
    }
}
