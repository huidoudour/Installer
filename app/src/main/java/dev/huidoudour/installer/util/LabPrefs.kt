package dev.huidoudour.installer.util

import android.content.Context
import dev.huidoudour.installer.auth.Authorizer
import dev.huidoudour.installer.auth.SmartAuthorizer
import dev.huidoudour.installer.auth.SmartAuthorizerCandidate

/**
 * 实验室页面各开关的统一入口（底层委托给 [SmartAuthorizer] 与 [SignaturePrefs]，
 * 保持单一数据源）。
 */
object LabPrefs {

    // ==================== 智能授权 ====================

    /** 是否启用“尝试多个授权器安装” */
    fun isTryMultipleAuthorizersEnabled(context: Context): Boolean =
        SmartAuthorizer.isEnabled(context)

    fun setTryMultipleAuthorizersEnabled(context: Context, enabled: Boolean) {
        SmartAuthorizer.setEnabled(context, enabled)
    }

    /** 是否开启“按安装状态选择授权器”（已安装→Shizuku，未安装→Dhizuku） */
    fun isAuthorizerByInstallStateEnabled(context: Context): Boolean =
        SmartAuthorizer.isByInstallStateEnabled(context)

    fun setAuthorizerByInstallStateEnabled(context: Context, enabled: Boolean) {
        SmartAuthorizer.setByInstallStateEnabled(context, enabled)
    }

    /** 智能授权回退候选列表（含启用状态与顺序） */
    fun getCandidates(context: Context): List<SmartAuthorizerCandidate> =
        SmartAuthorizer.getCandidates(context)

    fun setCandidates(context: Context, candidates: List<SmartAuthorizerCandidate>) {
        SmartAuthorizer.setCandidates(context, candidates)
    }

    /** 展示用的当前启用顺序文本，如 `Shizuku->Dhizuku` */
    fun enabledOrderLabel(context: Context): String =
        SmartAuthorizer.enabledOrderLabel(context)

    /** 该授权方式当前是否可用 */
    fun isAuthorizerAvailable(context: Context, authorizer: Authorizer): Boolean =
        SmartAuthorizer.isAvailable(context, authorizer)

    // ==================== 签名校验 ====================

    /** 是否在安装前校验签名 */
    fun isCheckSignatureEnabled(context: Context): Boolean =
        SignaturePrefs.isCheckEnabled(context)

    fun setCheckSignatureEnabled(context: Context, enabled: Boolean) {
        SignaturePrefs.setCheckEnabled(context, enabled)
    }

    /** 是否显示签名详情 */
    fun isShowSignatureDetailsEnabled(context: Context): Boolean =
        SignaturePrefs.isShowDetailsEnabled(context)

    fun setShowSignatureDetailsEnabled(context: Context, enabled: Boolean) {
        SignaturePrefs.setShowDetailsEnabled(context, enabled)
    }
}
