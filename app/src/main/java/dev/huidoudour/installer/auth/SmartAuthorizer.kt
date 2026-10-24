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

    /** 是否启用“按安装状态选择授权器”（全新安装→Dhizuku，已安装→所选授权方式） */
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

    /**
     * 按安装状态选定的优先授权器：
     * - 全新安装固定优先 Dhizuku（设备所有者）；
     * - 已安装（升级/重装）跟随 [current]，即用户所选授权方式。
     */
    fun preferredAuthorizerForInstallState(current: Authorizer, installed: Boolean): Authorizer =
        if (installed) current else Authorizer.Dhizuku

    /**
     * 计算安装时的尝试顺序。
     *
     * - 开启“按安装状态选择授权器”时：全新安装把 Dhizuku 排到最前，已安装则跟随 [current]；
     * - 其后接 [current]（全新安装时作为 Dhizuku 不可用/失败的回退），再按用户配置的回退顺序（开启“尝试多个授权器安装”时）；
     * - 两个智能开关都未开启时只尝试 [current]；
     * - 最终只保留未被独立开关关闭、且当前可用的授权器；若全部不可用，则回退到 [current]。
     */
    fun resolveInstallPlan(
        context: Context,
        current: Authorizer,
        installed: Boolean,
    ): List<Authorizer> {
        // 两个特权开关都关闭：完全不使用 Shizuku / Dhizuku，直接回退到系统安装器（用于权限测试）
        if (!PrivilegeHelper.isShizukuEnabled(context) && !PrivilegeHelper.isDhizukuEnabled(context)) {
            return listOf(Authorizer.None)
        }

        // 被对应开关关闭的授权器不参与本次安装，回退到系统安装器
        val fallbackCurrent = if (isAuthorizerEnabled(context, current)) current else Authorizer.None

        if (!isByInstallStateEnabled(context) && !isEnabled(context)) {
            return listOf(fallbackCurrent)
        }

        val candidates = buildList {
            if (isByInstallStateEnabled(context)) {
                add(preferredAuthorizerForInstallState(current, installed))
            }
            // 用户选择的授权方式：已安装时即为首选，全新安装时作为 Dhizuku 不可用的回退
            add(current)
            if (isEnabled(context)) {
                addAll(enabledOrder(context))
            }
        }.distinct().filter { isAuthorizerEnabled(context, it) }

        return candidates.filter { isAvailable(context, it) }.ifEmpty { listOf(fallbackCurrent) }
    }

    /** 该授权方式是否未被对应的独立开关关闭（[Authorizer.None] 视为始终允许） */
    private fun isAuthorizerEnabled(context: Context, authorizer: Authorizer): Boolean {
        val mode = toPrivilegeMode(authorizer) ?: return true
        return PrivilegeHelper.isModeEnabled(context, mode)
    }

    /** 展示用的当前启用顺序文本，如 `Shizuku->Dhizuku` */
    fun enabledOrderLabel(context: Context): String {
        val names = enabledOrder(context).map { context.getString(it.displayNameRes) }
        return if (names.isEmpty()) "-" else names.joinToString("->")
    }
}
