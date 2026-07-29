package io.github.huidoudour.Installer.auth

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
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

    const val DHIZUKU_PACKAGE = "com.rosan.dhizuku"
    const val DHIZUKU_PACKAGE_CLONE = "me.huidoudour.dhizuku"
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
     * 获取当前实际活跃的 Dhizuku 授权器包名
     * 优先通过 DevicePolicyManager 检测被设为 DeviceOwner / ProfileOwner 的版本
     * DPM 检测不到时，优先返回克隆版（用户主动安装的版本）
     */
    fun getActiveDhizukuPackage(context: Context): String? {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        if (dpm != null) {
            if (dpm.isDeviceOwnerApp(DHIZUKU_PACKAGE) || dpm.isProfileOwnerApp(DHIZUKU_PACKAGE)) {
                android.util.Log.d(TAG, "DPM: original is device/profile owner")
                return DHIZUKU_PACKAGE
            }
            if (dpm.isDeviceOwnerApp(DHIZUKU_PACKAGE_CLONE) || dpm.isProfileOwnerApp(DHIZUKU_PACKAGE_CLONE)) {
                android.util.Log.d(TAG, "DPM: clone is device/profile owner")
                return DHIZUKU_PACKAGE_CLONE
            }
            android.util.Log.d(TAG, "DPM: neither package detected as device/profile owner")
        }
        // DPM 不可用或无检测结果时：克隆版优先（用户自己构建并安装的版本）
        // 注意：原版仅在 DPM 明确检测到时才优先返回
        val cloneInstalled = isPackageInstalled(context, DHIZUKU_PACKAGE_CLONE)
        val originalInstalled = isPackageInstalled(context, DHIZUKU_PACKAGE)
        android.util.Log.d(TAG, "Fallback detection: cloneInstalled=$cloneInstalled, originalInstalled=$originalInstalled")
        return when {
            cloneInstalled -> DHIZUKU_PACKAGE_CLONE
            originalInstalled -> DHIZUKU_PACKAGE
            else -> null
        }
    }

    /**
     * 获取已安装的 Dhizuku 包名（检测两个变体）
     * 仅根据「是否安装」判断，不关心谁被激活
     * @return 返回当前设备上已安装的 Dhizuku 包名，均未安装则返回 null
     */
    fun getInstalledDhizukuPackage(context: Context): String? {
        return when {
            isPackageInstalled(context, DHIZUKU_PACKAGE) -> DHIZUKU_PACKAGE
            isPackageInstalled(context, DHIZUKU_PACKAGE_CLONE) -> DHIZUKU_PACKAGE_CLONE
            else -> null
        }
    }

    /**
     * 检查指定包名是否已安装
     */
    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 检查指定授权器是否已安装
     */
    fun isInstalled(context: Context, mode: PrivilegeMode): Boolean {
        return when (mode) {
            PrivilegeMode.SHIZUKU -> isPackageInstalled(context, SHIZUKU_PACKAGE)
            PrivilegeMode.DHIZUKU -> getInstalledDhizukuPackage(context) != null
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
     * 支持原版 (com.rosan.dhizuku) 和重构版 (me.huidoudour.dhizuku)
     *
     * 流程：
     * 1. 通过 DevicePolicyManager 检测哪个包被设为设备/配置文件所有者（活跃的才有特权）
     * 2. 若 DPM 检测不到，尝试 Dhizuku.init() API 连接
     * 3. 最终回退到标准 Android 权限检测（signature 级别自动匹配）
     */
    fun checkDhizukuStatus(context: Context): PrivilegeStatus {
        val activePkg = getActiveDhizukuPackage(context) ?: return PrivilegeStatus.NOT_INSTALLED

        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val isActive = dpm?.isDeviceOwnerApp(activePkg) == true || dpm?.isProfileOwnerApp(activePkg) == true

            if (!isActive) {
                // 安装了但未激活为设备/配置文件所有者，尝试 API 连接看能否用
                if (Dhizuku.init(context.applicationContext)) {
                    // API 连上了（非活跃但服务在运行？），继续检查权限
                    if (Dhizuku.isPermissionGranted()) {
                        return PrivilegeStatus.AUTHORIZED
                    }
                    try {
                        val process = Dhizuku.newProcess(arrayOf("echo", "test"), null, null)
                        process.destroy()
                        return PrivilegeStatus.AUTHORIZED
                    } catch (e: Exception) {
                        return PrivilegeStatus.NOT_AUTHORIZED
                    }
                }
                // API 连不上且未激活 → 未运行
                return PrivilegeStatus.NOT_RUNNING
            }

            // ---- 以下是已激活的情况 ----

            // 优先使用 Dhizuku API 检查
            if (Dhizuku.init(context.applicationContext)) {
                if (Dhizuku.isPermissionGranted()) {
                    return PrivilegeStatus.AUTHORIZED
                }
                try {
                    val process = Dhizuku.newProcess(arrayOf("echo", "test"), null, null)
                    process.destroy()
                    return PrivilegeStatus.AUTHORIZED
                } catch (e: Exception) {
                    return PrivilegeStatus.NOT_AUTHORIZED
                }
            }

            // API 连接失败（可能是克隆版），回退到权限检测
            val hasPermission = try {
                context.checkSelfPermission("com.rosan.dhizuku.permission.API") == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }

            if (hasPermission) {
                PrivilegeStatus.AUTHORIZED
            } else {
                PrivilegeStatus.NOT_AUTHORIZED
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
     * 支持原版 (com.rosan.dhizuku) 和重构版 (me.huidoudour.dhizuku)
     * 优先使用 API（namespace 未变时克隆版也可用），失败则引导手动授权
     * @param onResult 可选回调，授权完成后调用，参数为是否授权成功
     */
    fun requestDhizukuPermission(context: Context, onResult: ((Boolean) -> Unit)? = null) {
        val dhizukuPackage = getInstalledDhizukuPackage(context)
        if (dhizukuPackage == null) {
            android.util.Log.e(TAG, "No Dhizuku app installed")
            onResult?.invoke(false)
            return
        }

        // 统一尝试 API 连接（原版/克隆版均可）
        try {
            if (!Dhizuku.init(context.applicationContext)) {
                // API 连接失败
                android.util.Log.w(TAG, "Dhizuku API init failed, package=$dhizukuPackage")
                if (dhizukuPackage == DHIZUKU_PACKAGE_CLONE) {
                    // 克隆版：引导手动授权
                    openPrivilegeApp(context, PrivilegeMode.DHIZUKU)
                }
                onResult?.invoke(false)
                return
            }

            if (Dhizuku.isPermissionGranted()) {
                android.util.Log.d(TAG, "Dhizuku permission already granted")
                onResult?.invoke(true)
                return
            }

            Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                override fun onRequestPermission(grantResult: Int) {
                    val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        android.util.Log.d(TAG, "Dhizuku permission granted")
                    } else {
                        android.util.Log.w(TAG, "Dhizuku permission denied")
                    }
                    onResult?.invoke(granted)
                }
            })
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error requesting Dhizuku permission: ${e.message}")
            e.printStackTrace()
            if (dhizukuPackage == DHIZUKU_PACKAGE_CLONE) {
                // 克隆版 API 异常时引导手动授权
                openPrivilegeApp(context, PrivilegeMode.DHIZUKU)
            }
            onResult?.invoke(false)
        }
    }

    /**
     * 打开指定授权器应用
     */
    fun openPrivilegeApp(context: Context, mode: PrivilegeMode) {
        val packageName = when (mode) {
            PrivilegeMode.SHIZUKU -> SHIZUKU_PACKAGE
            PrivilegeMode.DHIZUKU -> getActiveDhizukuPackage(context) ?: run {
                android.util.Log.e(TAG, "No Dhizuku installed, cannot open")
                return
            }
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
