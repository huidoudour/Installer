package dev.huidoudour.installer.util

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 轻量“检测新版本”：查询 GitHub Releases 最新正式版，与本地基础版本号比较。
 * 仅做提示与跳转，不下载、不安装。使用内置 [HttpURLConnection]，不引入额外网络库。
 */
object UpdateChecker {

    /**
     * 走 GitHub 网页版 releases/latest（会 302 跳转到 /releases/tag/<版本>）而非 api.github.com，
     * 规避未认证 API 的 60 次/小时限流（403）。
     */
    private const val LATEST_RELEASE_PAGE =
        "https://github.com/huidoudour/Installer/releases/latest"

    const val RELEASES_PAGE_URL = LATEST_RELEASE_PAGE

    /** 认证检测端点：填写 PAT 后走这里（未认证 60 次/小时 → 认证 5000 次/小时） */
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/huidoudour/Installer/releases/latest"

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
     * @param token 可选 GitHub PAT；非空则走认证 API，为空则回退网页 releases/latest 的 302 跳转
     */
    fun check(localBaseVersion: String, token: String? = null): UpdateCheckResult {
        return if (!token.isNullOrBlank()) {
            checkViaApi(localBaseVersion, token)
        } else {
            checkViaPage(localBaseVersion)
        }
    }

    /** 无 token：走网页版 releases/latest，捕获 302 的 Location 解析版本，不受 API 限流 */
    private fun checkViaPage(localBaseVersion: String): UpdateCheckResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_RELEASE_PAGE).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                // 不自动跟随重定向，手动捕获 302 的 Location 解析 /releases/tag/<版本>
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Installer-UpdateCheck")
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                // 尚无任何已发布的正式版
                return UpdateCheckResult.Failed("HTTP 404 - no published release")
            }
            val location = conn.getHeaderField("Location")
            if (code in 300..399 && location != null) {
                // releases/latest 会排除草稿/预发布，Location 指向最新正式版
                val tag = location.substringAfterLast("/tag/", "")
                    .substringBefore('?')
                    .removePrefix("v")
                if (tag.isEmpty()) {
                    return UpdateCheckResult.Failed("no tag in redirect: $location")
                }
                compareTag(localBaseVersion, tag)
            } else {
                UpdateCheckResult.Failed("HTTP $code")
            }
        } catch (e: Exception) {
            UpdateCheckResult.Failed(e.message ?: e::class.java.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    /** 有 token：走 api.github.com 认证请求，从 JSON 取 tag_name */
    private fun checkViaApi(localBaseVersion: String, token: String): UpdateCheckResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Installer-UpdateCheck")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer $token")
            }
            val code = conn.responseCode
            if (code !in HttpURLConnection.HTTP_OK..299) {
                // 认证失败/限流等：读取 GitHub 返回的 {"message": "..."} 便于定位
                val detail = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                        ?.let { JSONObject(it).optString("message") }
                }.getOrNull()
                return UpdateCheckResult.Failed(
                    "HTTP $code" + if (detail.isNullOrEmpty()) "" else " - $detail"
                )
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val tag = JSONObject(body).optString("tag_name").removePrefix("v")
            if (tag.isEmpty()) {
                return UpdateCheckResult.Failed("missing tag_name in response")
            }
            compareTag(localBaseVersion, tag)
        } catch (e: Exception) {
            UpdateCheckResult.Failed(e.message ?: e::class.java.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    /** 统一比较：远端 tag 大于本地版本则新版，否则已最新 */
    private fun compareTag(localBaseVersion: String, tag: String): UpdateCheckResult {
        val info = UpdateInfo(latestVersion = tag, releaseUrl = RELEASES_PAGE_URL)
        return if (compareVersions(tag, localBaseVersion) > 0) {
            UpdateCheckResult.NewVersion(info)
        } else {
            UpdateCheckResult.UpToDate
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
