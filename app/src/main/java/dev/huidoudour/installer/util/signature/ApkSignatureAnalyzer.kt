package dev.huidoudour.installer.util.signature

import android.os.Build
import android.util.Log
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.cert.X509Certificate

/**
 * 基于 apksig 的完整 APK 签名校验（v1 / v2 / v3 / v3.1 / v4）。
 *
 * 与 [LightweightApkSignatureReader]（只读取 APK Signing Block 中的证书声明、不校验签名）
 * 不同，本分析器会真正校验签名与内容摘要，因此其结果是“签名比对”的权威来源：
 * 只有校验通过（[AppSignatureInfo.verified] 为 true）的证书集合才允许用于判定
 * “签名不一致（MISMATCH）”，否则只能判定“无法确认”。
 *
 * 注意：apksig 尚不解析 v3.2 签名块（[LightweightApkSignatureReader] 会把它读进
 * [AppSignatureInfo.declaredSchemes]），但 v3.2 总是与 v2/v3 同时签名，
 * 因此不影响完整校验结果。
 */
object ApkSignatureAnalyzer {

    private const val TAG = "ApkSignatureAnalyzer"

    /**
     * 校验 APK 签名。失败时返回 [SignatureVerificationStatus.FAILED] 的空结果，
     * 不会抛出异常。
     */
    fun analyze(file: File): AppSignatureInfo = analyze(file, Build.VERSION.SDK_INT)

    internal fun analyze(file: File, platformSdk: Int): AppSignatureInfo {
        return try {
            val result = ApkVerifier.Builder(file)
                .setMinCheckedPlatformVersion(platformSdk)
                .setMaxCheckedPlatformVersion(platformSdk)
                .build()
                .verify()

            val certificates = result.signerCertificates.orEmpty()
                .map(CertificateFormatter::format)

            AppSignatureInfo(
                verified = result.isVerified,
                signerSha256Set = certificates.mapTo(linkedSetOf()) { it.sha256 },
                certificates = certificates,
                signingCertificateHistory = result.signingLineageCertificates()
                    .map(CertificateFormatter::format),
                hasMultipleSigners = certificates.size > 1,
                verifiedSchemes = result.verifiedSchemes(),
                verificationStatus = if (result.isVerified) {
                    SignatureVerificationStatus.VERIFIED
                } else {
                    SignatureVerificationStatus.FAILED
                },
                warnings = result.warnings.orEmpty().map { it.toString() },
                errors = result.errors.orEmpty().map { it.toString() },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify APK signature: ${file.absolutePath}", e)
            AppSignatureInfo(
                verified = false,
                signerSha256Set = emptySet(),
                certificates = emptyList(),
                verificationStatus = SignatureVerificationStatus.FAILED,
                errors = listOf(e.message ?: e::class.java.simpleName),
            )
        }
    }

    private fun ApkVerifier.Result.verifiedSchemes(): List<String> = buildList {
        if (isVerifiedUsingV1Scheme) add(SCHEME_V1)
        if (isVerifiedUsingV2Scheme) add(SCHEME_V2)
        if (isVerifiedUsingV3Scheme) add(SCHEME_V3)
        if (isVerifiedUsingV31Scheme) add(SCHEME_V31)
        if (isVerifiedUsingV4Scheme) add(SCHEME_V4)
    }

    /**
     * 读取 APK 内声明的签名轮换谱系（v3.1+）。读取失败不影响主流程。
     */
    private fun ApkVerifier.Result.signingLineageCertificates(): List<X509Certificate> = runCatching {
        signingCertificateLineage?.certificatesInLineage.orEmpty()
    }.getOrElse { error ->
        Log.w(TAG, "Failed to read APK signing certificate lineage", error)
        emptyList()
    }

    const val SCHEME_V1 = "V1"
    const val SCHEME_V2 = "V2"
    const val SCHEME_V3 = "V3"
    const val SCHEME_V31 = "V3.1"
    const val SCHEME_V4 = "V4"
}

