package dev.huidoudour.installer.auth

/**
 * 智能授权回退列表中的一个候选及其启用状态。
 */
data class SmartAuthorizerCandidate(
    val authorizer: Authorizer,
    val enabled: Boolean,
)

/**
 * 智能授权候选的默认值 / 编解码（存储格式：`shizuku:1,dhizuku:1,none:0`）。
 */
object SmartAuthorizerPreferences {

    private val supportedAuthorizers = listOf(
        Authorizer.Shizuku,
        Authorizer.Dhizuku,
        Authorizer.None,
    )

    fun defaultCandidates(): List<SmartAuthorizerCandidate> =
        supportedAuthorizers.map { SmartAuthorizerCandidate(authorizer = it, enabled = true) }

    fun decode(value: String?): List<SmartAuthorizerCandidate> {
        if (value.isNullOrBlank()) return defaultCandidates()

        val parsed = value
            .split(',')
            .mapNotNull { token ->
                val parts = token.split(':', limit = 2)
                val authorizer = Authorizer.fromValueOrDefault(parts.firstOrNull().orEmpty())
                if (authorizer !in supportedAuthorizers) return@mapNotNull null

                SmartAuthorizerCandidate(
                    authorizer = authorizer,
                    enabled = parts.getOrNull(1) != "0",
                )
            }
            .distinctBy { it.authorizer }

        if (parsed.isEmpty()) return defaultCandidates()

        val missing = defaultCandidates()
            .filterNot { candidate -> parsed.any { it.authorizer == candidate.authorizer } }

        return parsed + missing
    }

    fun encode(candidates: List<SmartAuthorizerCandidate>): String = candidates
        .filter { it.authorizer in supportedAuthorizers }
        .distinctBy { it.authorizer }
        .joinToString(",") { candidate ->
            "${candidate.authorizer.value}:${if (candidate.enabled) "1" else "0"}"
        }
}
