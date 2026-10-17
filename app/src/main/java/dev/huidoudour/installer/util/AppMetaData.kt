package dev.huidoudour.installer.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * 读取 AndroidManifest 里声明的 `<meta-data>` 标记。
 *
 * 目前用于区分构建渠道，清单里声明的是 `isDebuggable`：
 * - `app/src/debug/AndroidManifest.xml` → `true`
 * - `app/src/release/AndroidManifest.xml` → `false`
 */
object AppMetaData {

    private const val KEY_IS_DEBUGGABLE = "isDebuggable"

    /**
     * 清单里声明的调试标记。
     *
     * 未声明时按「正式包」处理（即视为不可调试），避免渠道清单漏配时把调试行为带进正式包。
     */
    fun isDebuggable(context: Context): Boolean = boolean(context, KEY_IS_DEBUGGABLE, false)

    /**
     * 读取布尔型 meta-data：兼容 `true` / `"true"` 字符串 / `1` 三种写法。
     * 读取失败（包信息不可用、类型不认识）时返回 [defaultValue]。
     */
    fun boolean(context: Context, name: String, defaultValue: Boolean = false): Boolean {
        val value = metaData(context, name) ?: return defaultValue
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> defaultValue
        }
    }

    private fun metaData(context: Context, name: String): Any? = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        info.metaData?.get(name)
    }.getOrNull()
}
