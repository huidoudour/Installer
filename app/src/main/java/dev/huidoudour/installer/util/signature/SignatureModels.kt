package dev.huidoudour.installer.util.signature

/**
 * 单个签名证书的格式化信息
 */
data class SignatureCertificateInfo(
    val sha256: String,
    val sha1: String,
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val validFrom: String?,
    val validUntil: String?,
    val publicKeyAlgorithm: String?,
    val signatureAlgorithm: String?,
)

/**
 * 一个 APK / 已安装应用的签名信息集合
 */
data class AppSignatureInfo(
    val verified: Boolean,
    val signerSha256Set: Set<String>,
    val certificates: List<SignatureCertificateInfo>,
    val signingCertificateHistory: List<SignatureCertificateInfo> = emptyList(),
    val hasMultipleSigners: Boolean = false,
    val verifiedSchemes: List<String> = emptyList(),
    val declaredSchemes: List<String> = emptyList(),
    val verificationStatus: SignatureVerificationStatus = if (verified) {
        SignatureVerificationStatus.VERIFIED
    } else {
        SignatureVerificationStatus.FAILED
    },
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
) {
    val primarySha256: String?
        get() = certificates.firstOrNull()?.sha256 ?: signerSha256Set.firstOrNull()

    val signingCertificateHistorySha256Set: Set<String>
        get() = signingCertificateHistory.mapTo(linkedSetOf()) { it.sha256 }

    val allKnownSha256Set: Set<String>
        get() = linkedSetOf<String>().apply {
            addAll(signerSha256Set)
            addAll(signingCertificateHistorySha256Set)
        }
}

enum class SignatureVerificationStatus {
    VERIFIED,
    FAILED,
    SIGNING_BLOCK_ONLY,
}

/**
 * 新 APK 与已安装应用签名比对的结果
 */
enum class SignatureMatchStatus {
    /** 目标应用尚未安装 */
    NOT_INSTALLED,

    /** 签名一致，可安全覆盖安装 */
    MATCH,

    /** 签名不同，但已安装包的签名轮换历史确认兼容 */
    ROTATION_COMPATIBLE,

    /** APK 声明了签名轮换谱系，但无法确认已安装包的兼容性 */
    CANDIDATE_ROTATION_UNCONFIRMED,

    /** 签名不一致，存在安全风险 */
    MISMATCH,

    /** 无法获取其中一方的签名 */
    UNKNOWN_ERROR,
}

/**
 * 供 UI 展示的签名校验摘要。
 *
 * @param applicable 是否适用（XAPK/APKS 容器暂标记为不适用）
 */
data class SignatureSummary(
    val applicable: Boolean,
    val status: SignatureMatchStatus,
    val packageName: String?,
    val apkSha256: String?,
    val apkSignature: AppSignatureInfo?,
    val installedSignature: AppSignatureInfo?,
) {
    /** SHA-256 短摘要（前 16 位） */
    val shortSha256: String? get() = apkSha256?.take(16)
}
