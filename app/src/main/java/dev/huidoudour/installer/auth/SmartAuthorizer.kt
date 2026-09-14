package dev.huidoudour.installer.auth

import android.content.Context

/**
 * 智能授权：多授权器回退安装的偏好与解析。
 *
 * - 偏好写入 `app_settings`：`try_multiple_authorizers_on_install`、`smart_authorizer_candidates`
 * - [resolveInstallPlan] 综合“按安装状态选择”与“回退列表”返回安装时逐个尝试的授权方式
 */
object SmartAuthorizer {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_TRY_MULTIPLE = "try_multiple_authorizers_on_install"
    private const val KEY_CANDIDATES = "smart_authorizer_candidates"
    private const val KEY_BY_INSTALL_STATE = "authorizer_by_install_state"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 是否启用“尝试多个授权器安装” */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TRY_MULTIPLE, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRY_MULTIPLE, enabled).apply()
    }

    /** 是否启用“按安装状态选择授权器”（已安装→Shizuku，未安装→Dhizuku） */
    fun isByInstallStateEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BY_INSTALL_STATE, false)

    fun setByInstallStateEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BY_INSTALL_STATE, enabled).apply()
    }

    fun getCandidates(context: Context): List<SmartAuthorizerCandidate> =
        SmartAuthorizerPreferences.decode(prefs(context).getString(KEY_CANDIDATES, null))

    fun setCandidates(context: Context, candidates: List<SmartAuthorizerCandidate>) {
        prefs(context).edit()
            .putString(KEY_CANDIDATES, SmartAuthorizerPreferences.encode(candidates))
            .apply()
    }

    /** 已启用的候选顺序 */
    fun enabledOrder(context: Context): List<Authorizer> =
        getCandidates(context).filter { it.enabled }.map { it.authorizer }

    /** 将 [Authorizer] 映射为特权模式；[Authorizer.None] 无特权模式 */
    fun toPrivilegeMode(authorizer: Authorizer): PrivilegeHelper.PrivilegeMode? = when (authorizer) {
        Authorizer.Shizuku -> PrivilegeHelper.PrivilegeMode.SHIZUKU
        Authorizer.Dhizuku -> PrivilegeHelper.PrivilegeMode.DHIZUKU
        Authorizer.None -> null
    }

    /** 该授权方式当前是否可用（特权授权器需已授权；[Authorizer.None] 始终视为可用） */
    fun isAvailable(context: Context, authorizer: Authorizer): Boolean = when (authorizer) {
        Authorizer.None -> true
        else -> {
            val mode = toPrivilegeMode(authorizer) ?: return false
            PrivilegeHelper.getStatus(context, mode) == PrivilegeHelper.PrivilegeStatus.AUTHORIZED
        }
    }

    /** 按安装状态选定的首选授权器：已安装→Shizuku，未安装→Dhizuku */
    fun preferredAuthorizerForInstallState(installed: Boolean): Authorizer =
        if (installed) Authorizer.Shizuku else Authorizer.Dhizuku

    /**
     * 计算安装时的尝试顺序。
     *
     * - 开启“按安装状态选择授权器”时，将 [installed] 对应的首选授权器排到最前；
     * - 开启“尝试多个授权器安装”时，其后接用户配置的回退顺序（去重）；
     * - [current] 为两者都未开启时的默认单一授权器；
     * - 最终只保留当前可用的授权器；若智能策略无可用授权器，则回退到 [current]。
     */
    fun resolveInstallPlan(
        context: Context,
        current: Authorizer,
        installed: Boolean,
    ): List<Authorizer> {
        if (!isByInstallStateEnabled(context) && !isEnabled(context)) {
            return listOf(current)
        }

        val candidates = buildList {
            if (isByInstallStateEnabled(context)) {
                add(preferredAuthorizerForInstallState(installed))
            }
            if (isEnabled(context)) {
                addAll(enabledOrder(context))
            }
        }.distinct()

        return candidates.filter { isAvailable(context, it) }.ifEmpty { listOf(current) }
    }

    /** 展示用的当前启用顺序文本，如 `Shizuku->Dhizuku` */
    fun enabledOrderLabel(context: Context): String {
        val names = enabledOrder(context).map { context.getString(it.displayNameRes) }
        return if (names.isEmpty()) "-" else names.joinToString("->")
    }
}
