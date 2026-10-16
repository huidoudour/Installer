package dev.huidoudour.installer.ui.settings

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.huidoudour.installer.BuildConfig
import dev.huidoudour.installer.auth.PrivilegeHelper
import dev.huidoudour.installer.util.LanguageManager
import dev.huidoudour.installer.util.LogManager
import dev.huidoudour.installer.util.SecurePrefs
import dev.huidoudour.installer.util.ThemeManager
import dev.huidoudour.installer.util.UpdateChecker
import dev.huidoudour.installer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SettingsScreen ViewModel
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // 主题状态
    private val _currentTheme = MutableStateFlow(ThemeManager.getUserTheme(context))
    val currentTheme: StateFlow<Int> = _currentTheme.asStateFlow()

    // 语言状态
    private val _currentLanguage = MutableStateFlow(LanguageManager.getUserLanguage(context))
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    // 权限状态
    private val _privilegeStatus = MutableStateFlow(PrivilegeHelper.PrivilegeStatus.NOT_INSTALLED)
    val privilegeStatus: StateFlow<PrivilegeHelper.PrivilegeStatus> = _privilegeStatus.asStateFlow()

    private val _privilegeMode = MutableStateFlow(PrivilegeHelper.getCurrentMode(context))
    val privilegeMode: StateFlow<PrivilegeHelper.PrivilegeMode> = _privilegeMode.asStateFlow()

    // 安装行为选项与安装页共用同一份持久化配置。
    private val _replaceExisting = MutableStateFlow(prefs.getBoolean("replace_existing_app", true))
    val replaceExisting: StateFlow<Boolean> = _replaceExisting.asStateFlow()

    private val _grantPermissions = MutableStateFlow(prefs.getBoolean("auto_grant_permissions", false))
    val grantPermissions: StateFlow<Boolean> = _grantPermissions.asStateFlow()

    // 全局开关（分开）：是否使用 Shizuku / Dhizuku 特权安装（关闭后该项安装一律走系统安装器）
    private val _useShizuku = MutableStateFlow(PrivilegeHelper.isShizukuEnabled(context))
    val useShizuku: StateFlow<Boolean> = _useShizuku.asStateFlow()

    private val _useDhizuku = MutableStateFlow(PrivilegeHelper.isDhizukuEnabled(context))
    val useDhizuku: StateFlow<Boolean> = _useDhizuku.asStateFlow()

    // 新版本检测结果：null 表示无新版或尚未检测/检测失败
    private val _updateInfo = MutableStateFlow<UpdateChecker.UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateChecker.UpdateInfo?> = _updateInfo.asStateFlow()

    // GitHub PAT（可选）：非空时检测走认证 API，提高限流额度。加密存于 SecurePrefs。
    private val _githubPat = MutableStateFlow(loadGithubPat())
    val githubPat: StateFlow<String> = _githubPat.asStateFlow()

    /** 从加密区读取 PAT；若仅存在历史明文则迁移后清除明文 */
    private fun loadGithubPat(): String {
        val secure = SecurePrefs.getString(context, KEY_GITHUB_PAT)
        if (secure.isNotEmpty()) return secure
        val legacy = prefs.getString(KEY_GITHUB_PAT, "").orEmpty()
        return if (legacy.isNotEmpty()) {
            SecurePrefs.putString(context, KEY_GITHUB_PAT, legacy)
            prefs.edit().remove(KEY_GITHUB_PAT).apply()
            legacy
        } else ""
    }

    init {
        refreshPrivilegeStatus()
    }

    private companion object {
        const val KEY_GITHUB_PAT = "github_pat"
        const val KEY_LAST_AUTO_CHECK = "last_auto_update_check"
        const val AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }

    /**
     * 刷新权限状态
     */
    fun refreshPrivilegeStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val mode = PrivilegeHelper.getCurrentMode(context)
            val status = PrivilegeHelper.getStatus(context, mode)
            val useShizuku = PrivilegeHelper.isShizukuEnabled(context)
            val useDhizuku = PrivilegeHelper.isDhizukuEnabled(context)
            withContext(Dispatchers.Main) {
                _privilegeMode.value = mode
                _privilegeStatus.value = status
                _useShizuku.value = useShizuku
                _useDhizuku.value = useDhizuku
            }
        }
    }

    /**
     * 自动检测：24h 内只真正发起一次（手动 checkUpdateNow 不受限）。
     * 触发与结果均输出到日志页（tag: Update）
     */
    fun checkUpdate() {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_AUTO_CHECK, 0L)
        if (now - last < AUTO_CHECK_INTERVAL_MS) return
        viewModelScope.launch { checkUpdateNow() }
    }

    /**
     * 立即执行一次检测并返回结果（供手动触发）：同时更新状态、写日志并记录本次时间。
     * 调用方需处于协程作用域；内部已切到 IO。
     */
    suspend fun checkUpdateNow(): UpdateChecker.UpdateCheckResult = withContext(Dispatchers.IO) {
        val localVersion = BuildConfig.BASE_VERSION_NAME
        val logManager = LogManager.getInstance()
        prefs.edit().putLong(KEY_LAST_AUTO_CHECK, System.currentTimeMillis()).apply()
        val token = _githubPat.value
        logManager.addLog(
            "Update check triggered: local version $localVersion, mode=${if (token.isBlank()) "page" else "api"}",
            "Update"
        )
        val result = UpdateChecker.check(localVersion, token)
        when (result) {
            is UpdateChecker.UpdateCheckResult.NewVersion -> {
                _updateInfo.value = result.info
                logManager.addLog(
                    "New version found: ${result.info.latestVersion} (${result.info.releaseUrl})",
                    "Update"
                )
            }

            UpdateChecker.UpdateCheckResult.UpToDate -> {
                _updateInfo.value = null
                logManager.addLog("Update check finished: already up to date", "Update")
            }

            is UpdateChecker.UpdateCheckResult.Failed -> {
                _updateInfo.value = null
                logManager.addLog("Update check failed: ${result.reason}", "Update")
            }
        }
        result
    }

    /** 保存/清除 GitHub PAT（传空串即清除）；加密存储 */
    fun setGithubPat(token: String) {
        val trimmed = token.trim()
        _githubPat.value = trimmed
        if (trimmed.isEmpty()) {
            SecurePrefs.remove(context, KEY_GITHUB_PAT)
        } else {
            SecurePrefs.putString(context, KEY_GITHUB_PAT, trimmed)
        }
    }

    /**
     * 切换主题
     */
    fun setTheme(theme: Int) {
        ThemeManager.saveUserTheme(context, theme)
        ThemeManager.applyTheme(theme)
        _currentTheme.value = theme
    }

    /**
     * 切换语言
     * 使用 AppCompatDelegate.setApplicationLocales() 自动触发 Activity 重建
     * @param activity 用于设置淡入淡出过渡动画
     */
    fun setLanguage(languageCode: String, activity: Activity? = null) {
        LanguageManager.saveUserLanguage(context, languageCode)
        LanguageManager.applyLanguage(languageCode, activity)
        // 注意：applyLanguage 会触发 Activity 重建，无需手动更新 _currentLanguage
    }

    /**
     * 获取语言显示名称
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return LanguageManager.getLanguageDisplayName(context, languageCode)
    }

    fun setReplaceExisting(value: Boolean) {
        _replaceExisting.value = value
        prefs.edit().putBoolean("replace_existing_app", value).apply()
    }

    fun setGrantPermissions(value: Boolean) {
        _grantPermissions.value = value
        prefs.edit().putBoolean("auto_grant_permissions", value).apply()
    }

    /**
     * 获取状态文本
     */
    fun getStatusText(status: PrivilegeHelper.PrivilegeStatus): String {
        return when (status) {
            PrivilegeHelper.PrivilegeStatus.AUTHORIZED -> context.getString(R.string.authorized)
            PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED -> context.getString(R.string.not_authorized)
            PrivilegeHelper.PrivilegeStatus.NOT_INSTALLED -> context.getString(R.string.not_installed)
            PrivilegeHelper.PrivilegeStatus.NOT_RUNNING -> context.getString(R.string.not_running)
            PrivilegeHelper.PrivilegeStatus.VERSION_TOO_LOW -> context.getString(R.string.version_too_low)
        }
    }
}
