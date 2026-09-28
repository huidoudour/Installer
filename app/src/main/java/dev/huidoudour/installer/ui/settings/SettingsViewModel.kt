package dev.huidoudour.installer.ui.settings

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.huidoudour.installer.auth.PrivilegeHelper
import dev.huidoudour.installer.util.LanguageManager
import dev.huidoudour.installer.util.ThemeManager
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

    init {
        refreshPrivilegeStatus()
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
