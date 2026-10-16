package dev.huidoudour.installer.util

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 轻量“检测新版本”：查询 GitHub Releases 最新正式版，与本地基础版本号比较。
 * 仅做提示与跳转，不下载、不安装。使用内置 [HttpURLConnection]，不引入额外网络库。
 */
object UpdateChecker {

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/huidoudour/Installer/releases/latest"

    /** 跳转兜底地址（API 未返回 html_url 时使用），会自动重定向到最新版本页 */
    const val RELEASES_PAGE_URL = "https://github.com/huidoudour/Installer/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,
        val releaseUrl: String
    )

    /** 检测结果：区分“有新版 / 已最新 / 失败”，供调用方记录日志 */
    sealed interface UpdateCheckResult {
        data class NewVersion(val info: UpdateInfo) : UpdateCheckResult
        data object UpToDate : UpdateCheckResult
        data class Failed(val reason: String) : UpdateCheckResult
    }

    /**
     * 同步发起检测（调用方需自行置于 IO 线程）。
     *
     * @param localBaseVersion 本地基础版本号，如 "26.10.14"（对应 [dev.huidoudour.installer.BuildConfig.BASE_VERSION_NAME]）
     */
    fun check(localBaseVersion: String): UpdateCheckResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                // GitHub API 无 User-Agent 会返回 403
                setRequestProperty("User-Agent", "Installer-UpdateCheck")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode !in HttpURLConnection.HTTP_OK..299) {
                return UpdateCheckResult.Failed("HTTP ${conn.responseCode}")
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            // 预发布/草稿不会出现在 /releases/latest，这里拿到的即最新正式版
            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isEmpty()) {
                return UpdateCheckResult.Failed("missing tag_name in response")
            }
            val htmlUrl = json.optString("html_url").ifEmpty { RELEASES_PAGE_URL }

            if (compareVersions(tag, localBaseVersion) > 0) {
                UpdateCheckResult.NewVersion(UpdateInfo(latestVersion = tag, releaseUrl = htmlUrl))
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Failed(e.message ?: e::class.java.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 按点分数字段逐位比较：a > b 返回正数，a < b 返回负数，相等返回 0。
     * 非数字字符（如预发布后缀）被忽略，缺失段按 0 处理。
     */
    private fun compareVersions(a: String, b: String): Int {
        val partsA = a.split('.')
        val partsB = b.split('.')
        val size = maxOf(partsA.size, partsB.size)
        for (i in 0 until size) {
            val numA = partsA.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            val numB = partsB.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            if (numA != numB) return numA - numB
        }
        return 0
    }
}
