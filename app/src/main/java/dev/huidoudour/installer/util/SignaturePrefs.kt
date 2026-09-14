package dev.huidoudour.installer.util

import android.content.Context

/**
 * 签名校验相关偏好管理（写入 `app_settings`）。
 *
 * - [KEY_CHECK_APP_SIGNATURE]：安装前校验签名，默认开启
 * - [KEY_SHOW_SIGNATURE_DETAILS]：显示签名详情，默认关闭
 */
object SignaturePrefs {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_CHECK_APP_SIGNATURE = "check_app_signature"
    private const val KEY_SHOW_SIGNATURE_DETAILS = "show_signature_details"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 是否在安装前校验签名（默认 true） */
    fun isCheckEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHECK_APP_SIGNATURE, true)

    fun setCheckEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CHECK_APP_SIGNATURE, enabled).apply()
    }

    /** 是否显示签名详情（默认 false） */
    fun isShowDetailsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_SIGNATURE_DETAILS, false)

    fun setShowDetailsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_SIGNATURE_DETAILS, enabled).apply()
    }
}
