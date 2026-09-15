package dev.huidoudour.installer.ui.lab

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.huidoudour.installer.auth.Authorizer
import dev.huidoudour.installer.auth.SmartAuthorizer
import dev.huidoudour.installer.auth.SmartAuthorizerCandidate
import dev.huidoudour.installer.util.LabPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 实验室页面 ViewModel：管理智能授权与签名校验相关开关及回退列表。
 */
class LabViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    private val _tryMultipleAuthorizers = MutableStateFlow(
        LabPrefs.isTryMultipleAuthorizersEnabled(context)
    )
    val tryMultipleAuthorizers: StateFlow<Boolean> = _tryMultipleAuthorizers.asStateFlow()

    private val _authorizerByInstallState = MutableStateFlow(
        LabPrefs.isAuthorizerByInstallStateEnabled(context)
    )
    val authorizerByInstallState: StateFlow<Boolean> = _authorizerByInstallState.asStateFlow()

    private val _candidates = MutableStateFlow(LabPrefs.getCandidates(context))
    val candidates: StateFlow<List<SmartAuthorizerCandidate>> = _candidates.asStateFlow()

    private val _checkSignature = MutableStateFlow(LabPrefs.isCheckSignatureEnabled(context))
    val checkSignature: StateFlow<Boolean> = _checkSignature.asStateFlow()

    private val _showSignatureDetails = MutableStateFlow(
        LabPrefs.isShowSignatureDetailsEnabled(context)
    )
    val showSignatureDetails: StateFlow<Boolean> = _showSignatureDetails.asStateFlow()

    private val _availability = MutableStateFlow<Map<Authorizer, Boolean>>(emptyMap())
    val availability: StateFlow<Map<Authorizer, Boolean>> = _availability.asStateFlow()

    init {
        refreshAvailability()
    }

    /** 重新检测各授权方式当前是否可用（已安装且已授权 / 系统安装器） */
    fun refreshAvailability() {
        viewModelScope.launch(Dispatchers.IO) {
            val map = Authorizer.entries.associateWith { SmartAuthorizer.isAvailable(context, it) }
            withContext(Dispatchers.Main) { _availability.value = map }
        }
    }

    fun setTryMultipleAuthorizers(enabled: Boolean) {
        _tryMultipleAuthorizers.value = enabled
        LabPrefs.setTryMultipleAuthorizersEnabled(context, enabled)
    }

    fun setAuthorizerByInstallState(enabled: Boolean) {
        _authorizerByInstallState.value = enabled
        LabPrefs.setAuthorizerByInstallStateEnabled(context, enabled)
    }

    fun setCheckSignature(enabled: Boolean) {
        _checkSignature.value = enabled
        LabPrefs.setCheckSignatureEnabled(context, enabled)
    }

    fun setShowSignatureDetails(enabled: Boolean) {
        _showSignatureDetails.value = enabled
        LabPrefs.setShowSignatureDetailsEnabled(context, enabled)
    }

    /**
     * 更新回退候选列表（勾选 / 排序）。
     * @return false 表示被拒绝（至少需保留 1 个启用）
     */
    fun updateCandidates(newCandidates: List<SmartAuthorizerCandidate>): Boolean {
        if (newCandidates.none { it.enabled }) return false
        _candidates.value = newCandidates
        LabPrefs.setCandidates(context, newCandidates)
        return true
    }

    /** 展示用的当前启用顺序文本 */
    fun enabledOrderLabel(): String = LabPrefs.enabledOrderLabel(context)
}
