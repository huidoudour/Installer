package dev.huidoudour.installer.install

import android.content.Context
import androidx.annotation.StringRes
import dev.huidoudour.installer.R

/**
 * 安装失败的归类。
 *
 * 归类结果决定对话框中展示的“失败原因”和“建议”，
 * 枚举本身不持有文案，文案由 [reasonRes]/[suggestionsRes] 指向的字符串资源提供。
 */
enum class InstallFailureCategory(
    @StringRes val reasonRes: Int,
    @StringRes val suggestionsRes: Int,
) {
    SIGNATURE_MISMATCH(
        R.string.install_failure_signature_reason,
        R.string.install_failure_signature_suggestions,
    ),
    VERSION_DOWNGRADE(
        R.string.install_failure_downgrade_reason,
        R.string.install_failure_downgrade_suggestions,
    ),
    VERSION_INCOMPATIBLE(
        R.string.install_failure_sdk_reason,
        R.string.install_failure_sdk_suggestions,
    ),
    ABI_INCOMPATIBLE(
        R.string.install_failure_abi_reason,
        R.string.install_failure_abi_suggestions,
    ),
    MISSING_SPLIT(
        R.string.install_failure_split_reason,
        R.string.install_failure_split_suggestions,
    ),
    TEST_ONLY(
        R.string.install_failure_test_only_reason,
        R.string.install_failure_test_only_suggestions,
    ),
    MISSING_SHARED_LIBRARY(
        R.string.install_failure_shared_library_reason,
        R.string.install_failure_shared_library_suggestions,
    ),
    PACKAGE_CONFLICT(
        R.string.install_failure_conflict_reason,
        R.string.install_failure_conflict_suggestions,
    ),
    INSUFFICIENT_STORAGE(
        R.string.install_failure_storage_reason,
        R.string.install_failure_storage_suggestions,
    ),
    STORAGE_UNAVAILABLE(
        R.string.install_failure_media_reason,
        R.string.install_failure_media_suggestions,
    ),
    INVALID_PACKAGE(
        R.string.install_failure_invalid_reason,
        R.string.install_failure_invalid_suggestions,
    ),
    BLOCKED_BY_POLICY(
        R.string.install_failure_blocked_reason,
        R.string.install_failure_blocked_suggestions,
    ),
    ABORTED(
        R.string.install_failure_aborted_reason,
        R.string.install_failure_aborted_suggestions,
    ),
    VERIFICATION_TIMEOUT(
        R.string.install_failure_timeout_reason,
        R.string.install_failure_timeout_suggestions,
    ),
    DEXOPT_FAILURE(
        R.string.install_failure_dexopt_reason,
        R.string.install_failure_dexopt_suggestions,
    ),
    PRIVILEGE_UNAVAILABLE(
        R.string.install_failure_privilege_reason,
        R.string.install_failure_privilege_suggestions,
    ),
    FILE_ACCESS_ERROR(
        R.string.install_failure_file_reason,
        R.string.install_failure_file_suggestions,
    ),
    SYSTEM_INSTALLER_ERROR(
        R.string.install_failure_system_installer_reason,
        R.string.install_failure_system_installer_suggestions,
    ),
    USER_ACTION_REQUIRED(
        R.string.install_failure_user_action_reason,
        R.string.install_failure_user_action_suggestions,
    ),
    SYSTEM_ERROR(
        R.string.install_failure_system_reason,
        R.string.install_failure_system_suggestions,
    ),
    UNKNOWN(
        R.string.install_failure_unknown_reason,
        R.string.install_failure_unknown_suggestions,
    ),
}

/**
 * 分析安装失败原因时可用的事实依据。
 *
 * 错误文本本身可能只有 `INSTALL_FAILED_*` 代码或一段系统消息，
 * 结合安装前已知的信息（版本关系、签名比对、容器类型）可以提高判断准确率。
 */
data class InstallFailureHints(
    val packageName: String? = null,
    val isDowngradeAttempt: Boolean = false,
    val signatureMismatch: Boolean = false,
    val isContainer: Boolean = false,
)

/**
 * 一次安装失败的分析结果。
 *
 * @param technicalDetail 底层原始错误（可能来自多个授权方式的尝试），用于日志和详情展示
 */
data class InstallFailureAnalysis(
    val category: InstallFailureCategory,
    val reason: String,
    val suggestions: String,
    val technicalDetail: String?,
)

/**
 * 根据安装器返回的错误文本推断失败原因和修复建议。
 *
 * Android 的 PackageInstaller 通常返回 `INSTALL_FAILED_*` / `INSTALL_PARSE_FAILED_*` 代码，
 * Shell 方式则返回 `Failure [INSTALL_FAILED_*]`。这里按“具体原因优先、通用原因兜底”的顺序匹配，
 * 从而避免 `STATUS_FAILURE_INVALID` 之类的通用前缀掩盖真正的失败原因。
 */
object InstallFailureAnalyzer {

    private val statusCodeRegex = Regex(
        "(?:status|状态|狀態|ステータス|статус)\\s*[=＝]\\s*(-?\\d+)",
        RegexOption.IGNORE_CASE,
    )

    fun analyze(
        context: Context,
        errors: List<String>,
        hints: InstallFailureHints = InstallFailureHints(),
    ): InstallFailureAnalysis {
        val messages = errors.map { it.trim() }.filter { it.isNotEmpty() }
        val combined = messages.joinToString("\n").lowercase()
        val category = classify(context, combined, hints)
        return InstallFailureAnalysis(
            category = category,
            reason = context.getString(category.reasonRes),
            suggestions = context.getString(category.suggestionsRes),
            technicalDetail = messages.distinct().takeIf { it.isNotEmpty() }
                ?.joinToString("\n")
                ?: context.getString(R.string.install_failure_no_detail),
        )
    }

    private fun classify(
        context: Context,
        combined: String,
        hints: InstallFailureHints,
    ): InstallFailureCategory {
        if (combined.hasAny(
                "install_failed_update_incompatible",
                "install_parse_failed_inconsistent_certificates",
                "signatures do not match",
                "signature does not match",
                "signature mismatch",
                "signature_mismatch",
                "签名不一致",
                "签名校验失败",
                "署名が一致",
            )
        ) {
            return InstallFailureCategory.SIGNATURE_MISMATCH
        }

        if (combined.hasAny(
                "install_failed_version_downgrade",
                "version_downgrade",
            )
        ) {
            return InstallFailureCategory.VERSION_DOWNGRADE
        }

        if (combined.hasAny(
                "install_failed_older_sdk",
                "install_failed_newer_sdk",
                "install_failed_deprecated_sdk_version",
                "status_failure_incompatible",
                "requires api level",
                "min_sdk",
                "minsdk",
                "sdk version",
                "系统版本过低",
                "系统版本过高",
                "需要更高版本的 android",
            )
        ) {
            return InstallFailureCategory.VERSION_INCOMPATIBLE
        }

        if (combined.hasAny(
                "install_failed_no_matching_abis",
                "install_failed_cpu_abi_incompatible",
                "install_failed_not_supported_arch",
                "install_failed_unsupported_arch",
                "abi_incompatible",
                "no matching abis",
                "cpu abi",
                "native library",
                "架构不兼容",
            )
        ) {
            return InstallFailureCategory.ABI_INCOMPATIBLE
        }

        if (combined.hasAny(
                "install_failed_missing_split",
                "missing split",
                "missing_split",
                "缺少拆分",
                "缺少 split",
            )
        ) {
            return InstallFailureCategory.MISSING_SPLIT
        }

        if (combined.hasAny(
                "install_failed_test_only",
                "test_only",
                "testonly",
                "test-only",
                "测试包",
            )
        ) {
            return InstallFailureCategory.TEST_ONLY
        }

        if (combined.hasAny(
                "install_failed_missing_shared_library",
                "missing shared library",
            )
        ) {
            return InstallFailureCategory.MISSING_SHARED_LIBRARY
        }

        if (combined.hasAny(
                "install_failed_already_exists",
                "install_failed_duplicate_package",
                "install_failed_uid_changed",
                "install_failed_shared_user_incompatible",
                "install_failed_conflicting_provider",
                "install_failed_duplicate_permission",
                "install_failed_replace_couldnt_delete",
                "install_failed_permission_model_downgrade",
                "package conflict",
                "conflicts with",
                "已存在",
                "冲突",
            )
        ) {
            return InstallFailureCategory.PACKAGE_CONFLICT
        }

        if (combined.hasAny(
                "install_failed_insufficient_storage",
                "insufficient_storage",
                "no space left",
                "enospc",
                "not enough space",
                "storage full",
                "空间不足",
                "存储空间不足",
            )
        ) {
            return InstallFailureCategory.INSUFFICIENT_STORAGE
        }

        if (combined.hasAny(
                "install_failed_media_unavailable",
                "install_failed_container_error",
                "install_failed_invalid_install_location",
                "media unavailable",
                "invalid install location",
                "外部存储不可用",
            )
        ) {
            return InstallFailureCategory.STORAGE_UNAVAILABLE
        }

        if (combined.hasAny(
                "install_failed_invalid_apk",
                "install_parse_failed",
                "install_failed_invalid_uri",
                "install_failed_bad_manifest",
                "failed to parse",
                "corrupt",
                "解析失败",
                "安装包损坏",
                "损坏",
            )
        ) {
            return InstallFailureCategory.INVALID_PACKAGE
        }

        if (combined.hasAny(
                "install_failed_user_restricted",
                "install_failed_blocked_by_policy",
                "install_failed_verification_failure",
                "status_failure_blocked",
                "user restricted",
                "blocked by policy",
                "play protect",
                "unknown sources",
                "not allowed",
                "restricted",
                "device policy",
                "未知来源",
                "被禁止",
                "已阻止",
            )
        ) {
            return InstallFailureCategory.BLOCKED_BY_POLICY
        }

        if (combined.hasAny(
                "install_failed_aborted",
                "status_failure_aborted",
                "aborted",
                "cancelled",
                "canceled",
                "已取消",
                "已中止",
            )
        ) {
            return InstallFailureCategory.ABORTED
        }

        if (combined.hasAny(
                "install_failed_verification_timeout",
                "verification_timeout",
                "timed out",
                "timeout",
                "超时",
            )
        ) {
            return InstallFailureCategory.VERIFICATION_TIMEOUT
        }

        if (combined.hasAny(
                "install_failed_dexopt",
                "dexopt",
                "dex_opt",
            )
        ) {
            return InstallFailureCategory.DEXOPT_FAILURE
        }

        val statusCategory = statusCategoryFromCode(combined)
        if (statusCategory != null) {
            return statusCategory
        }

        if (combined.hasAny(
                "install_failed_internal_error",
                "status_failure",
                "internal error",
                "installation dispatch failed",
            )
        ) {
            return InstallFailureCategory.SYSTEM_ERROR
        }

        if (combined.hasAny(
                "no_available_authorizer",
                "not connected",
                "not running",
                "not authorized",
                "unauthorized",
                "permission denial",
                "securityexception",
                "binder died",
                "dead object",
                "shizuku",
                "dhizuku",
                "未连接",
                "未授权",
                "没有可用的授权",
            ) || containsResourceMessage(
                context, combined, R.string.no_available_authorizer
            ) || containsResourceMessage(
                context, combined, R.string.container_needs_privilege
            )
        ) {
            return InstallFailureCategory.PRIVILEGE_UNAVAILABLE
        }

        if (combined.hasAny(
                "file_not_found",
                "no such file",
                "cannot read",
                "could not copy",
                "couldn't copy",
                "cannot access",
                "unsupported file",
                "no apk",
                "未找到任何 apk",
                "无法读取",
                "无法访问",
            ) || containsResourceMessage(
                context, combined, R.string.file_not_found
            ) || containsResourceMessage(
                context, combined, R.string.no_apk_found
            ) || containsResourceMessage(
                context, combined, R.string.cannot_access_install_file
            ) || containsResourceMessage(
                context, combined, R.string.unsupported_file_type
            )
        ) {
            return InstallFailureCategory.FILE_ACCESS_ERROR
        }

        if (combined.hasAny(
                "system installer failed",
                "activitynotfound",
                "no activity found",
                "系统安装器",
            ) || containsResourceMessage(
                context, combined, R.string.system_installer_failed
            )
        ) {
            return InstallFailureCategory.SYSTEM_INSTALLER_ERROR
        }

        if (combined.hasAny(
                "install_requires_user_action",
                "user action",
                "requires user action",
                "需要用户操作",
            ) || containsResourceMessage(
                context, combined, R.string.install_requires_user_action
            )
        ) {
            return InstallFailureCategory.USER_ACTION_REQUIRED
        }

        // 没有任何明确线索时，优先使用安装前已知的事实作为兜底判断。
        if (hints.signatureMismatch) {
            return InstallFailureCategory.SIGNATURE_MISMATCH
        }

        // 用户本来就在尝试降级时，降级是最可能的阻碍。
        if (hints.isDowngradeAttempt) {
            return InstallFailureCategory.VERSION_DOWNGRADE
        }

        return InstallFailureCategory.UNKNOWN
    }

    /**
     * 将 PackageInstaller 的数值状态映射为归类的兜底规则。
     * 状态文本里常带有本地化的“状态=4”前缀，因此先做正则提取。
     */
    private fun statusCategoryFromCode(combined: String): InstallFailureCategory? {
        val status = statusCodeRegex.find(combined)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return null
        return when (status) {
            2 -> InstallFailureCategory.BLOCKED_BY_POLICY       // STATUS_FAILURE_BLOCKED
            3 -> InstallFailureCategory.ABORTED                 // STATUS_FAILURE_ABORTED
            4 -> InstallFailureCategory.INVALID_PACKAGE         // STATUS_FAILURE_INVALID
            5 -> InstallFailureCategory.PACKAGE_CONFLICT        // STATUS_FAILURE_CONFLICT
            6 -> InstallFailureCategory.INSUFFICIENT_STORAGE    // STATUS_FAILURE_STORAGE
            7 -> InstallFailureCategory.VERSION_INCOMPATIBLE    // STATUS_FAILURE_INCOMPATIBLE
            else -> null
        }
    }

    /**
     * 判断错误文本是否命中了本项目自己生成的本地化消息。
     *
     * 这些消息的格式串在不同语言下不同，无法用英文关键字匹配；
     * 这里取格式串中首个占位符之前的部分作为特征前缀。
     */
    private fun containsResourceMessage(context: Context, combined: String, resId: Int): Boolean {
        val template = runCatching { context.getString(resId) }.getOrNull() ?: return false
        val marker = template.substringBefore('%').trim()
        return marker.isNotEmpty() && combined.contains(marker.lowercase())
    }

    private fun String.hasAny(vararg needles: String): Boolean =
        needles.any { contains(it) }
}
