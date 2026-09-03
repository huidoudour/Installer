package io.github.huidoudour.installer.ui.settings

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.huidoudour.installer.R
import io.github.huidoudour.installer.util.LanguageManager
import io.github.huidoudour.installer.auth.PrivilegeHelper
import io.github.huidoudour.installer.util.ThemeManager
import io.github.huidoudour.installer.util.LoaderAnimationMode
import io.github.huidoudour.installer.util.LoaderAnimationPrefs
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

    // 主题状态
    private val _currentTheme = MutableStateFlow(ThemeManager.getUserTheme(context))
    val currentTheme: StateFlow<Int> = _currentTheme.asStateFlow()

    // 语言状态
    private val _currentLanguage = MutableStateFlow(LanguageManager.getUserLanguage(context))
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    // 安装加载动画模式状态
    private val _currentLoaderMode = MutableStateFlow(LoaderAnimationPrefs.getMode(context))
    val currentLoaderMode: StateFlow<LoaderAnimationMode> = _currentLoaderMode.asStateFlow()

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
        viewModelScope.launch(Dispatchers.IO) {
            val mode = PrivilegeHelper.getCurrentMode(context)
            val status = PrivilegeHelper.getStatus(context, mode)
            withContext(Dispatchers.Main) {
                _privilegeMode.value = mode
                _privilegeStatus.value = status
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

    /**
     * 获取加载动画模式显示名称
     */
    fun getLoaderModeName(mode: LoaderAnimationMode): String {
        return when (mode) {
            LoaderAnimationMode.GRAPHIC -> context.getString(R.string.loader_animation_graphic)
            LoaderAnimationMode.WAVE -> context.getString(R.string.loader_animation_wave)
        }
    }

    /**
     * 切换加载动画模式
     */
    fun setLoaderMode(mode: LoaderAnimationMode) {
        LoaderAnimationPrefs.saveMode(context, mode)
        _currentLoaderMode.value = mode
    }

    /**
     * 请求权限
     */
    fun requestPrivilegePermission() {
        viewModelScope.launch(Dispatchers.IO) {
            when (_privilegeStatus.value) {
                PrivilegeHelper.PrivilegeStatus.NOT_INSTALLED -> {
                    PrivilegeHelper.openGithubPage(context, _privilegeMode.value)
                }
                PrivilegeHelper.PrivilegeStatus.NOT_RUNNING -> {
                    PrivilegeHelper.openPrivilegeApp(context, _privilegeMode.value)
                }
                PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED,
                PrivilegeHelper.PrivilegeStatus.VERSION_TOO_LOW -> {
                    when (_privilegeMode.value) {
                        PrivilegeHelper.PrivilegeMode.SHIZUKU -> {
                            PrivilegeHelper.requestShizukuPermission(123)
                        }
                        PrivilegeHelper.PrivilegeMode.DHIZUKU -> {
                            PrivilegeHelper.requestDhizukuPermission(context) { _ ->
                                viewModelScope.launch(Dispatchers.IO) {
                                    val status = PrivilegeHelper.getStatus(context, PrivilegeHelper.PrivilegeMode.DHIZUKU)
                                    withContext(Dispatchers.Main) {
                                        _privilegeStatus.value = status
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * 切换权限模式
     */
    fun switchPrivilegeMode() {
        viewModelScope.launch(Dispatchers.IO) {
            val newMode = PrivilegeHelper.switchMode(context)
            val status = PrivilegeHelper.getStatus(context, newMode)
            withContext(Dispatchers.Main) {
                _privilegeMode.value = newMode
                _privilegeStatus.value = status
            }
        }
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
