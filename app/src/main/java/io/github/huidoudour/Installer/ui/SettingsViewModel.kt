package io.github.huidoudour.Installer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.huidoudour.Installer.util.LanguageManager
import io.github.huidoudour.Installer.util.PrivilegeHelper
import io.github.huidoudour.Installer.util.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SettingsScreen ViewModel
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

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

    init {
        refreshPrivilegeStatus()
    }

    /**
     * 刷新权限状态
     */
    fun refreshPrivilegeStatus() {
        viewModelScope.launch {
            val mode = PrivilegeHelper.getCurrentMode(context)
            val status = PrivilegeHelper.getStatus(context, mode)
            _privilegeMode.value = mode
            _privilegeStatus.value = status
        }
    }

    /**
     * 切换主题
     */
    fun setTheme(theme: Int) {
        // TODO: 实现主题设置
        _currentTheme.value = theme
    }

    /**
     * 切换语言
     */
    fun setLanguage(languageCode: String) {
        LanguageManager.saveUserLanguage(context, languageCode)
        LanguageManager.applyUserLanguagePreference(context)
        _currentLanguage.value = languageCode
    }

    /**
     * 获取语言显示名称
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return LanguageManager.getLanguageDisplayName(context, languageCode)
    }

    /**
     * 请求权限
     */
    fun requestPrivilegePermission() {
        // TODO: 实现权限请求
        refreshPrivilegeStatus()
    }

    /**
     * 切换权限模式
     */
    fun switchPrivilegeMode() {
        // TODO: 实现模式切换
        val newMode = if (_privilegeMode.value == PrivilegeHelper.PrivilegeMode.SHIZUKU) {
            PrivilegeHelper.PrivilegeMode.DHIZUKU
        } else {
            PrivilegeHelper.PrivilegeMode.SHIZUKU
        }
        // PrivilegeHelper.setCurrentMode(context, newMode)
        refreshPrivilegeStatus()
    }

    /**
     * 获取状态文本
     */
    fun getStatusText(status: PrivilegeHelper.PrivilegeStatus): String {
        return when (status) {
            PrivilegeHelper.PrivilegeStatus.AUTHORIZED -> "已授权"
            PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED -> "未授权"
            PrivilegeHelper.PrivilegeStatus.NOT_INSTALLED -> "未安装"
            PrivilegeHelper.PrivilegeStatus.NOT_RUNNING -> "未运行"
            PrivilegeHelper.PrivilegeStatus.VERSION_TOO_LOW -> "版本过低"
        }
    }
}
