package dev.huidoudour.installer.util.signature

import android.content.Context
import java.io.File

/**
 * 签名校验门面：解析待安装 APK 的签名，并与已安装应用进行比对。
 *
 * 解析分两级：
 * 1. [ApkSignatureAnalyzer]：apksig 完整校验签名与内容摘要，是判定签名是否一致的**权威来源**；
 * 2. [LightweightApkSignatureReader]：仅读取 APK Signing Block 中的证书声明（毫秒级、不校验），
 *    只在完整校验拿不到任何证书时作为回退，用于展示证书详情与“声明一致”的弱判定。
 *
 * 最终安装时的签名校验仍由系统 / 特权安装器负责。
 */
object SignatureHelper {

    /**
     * 解析 APK 签名信息。
     *
     * @return 校验通过时为 [SignatureVerificationStatus.VERIFIED]；
     *         校验失败但能读到签名块声明时为 [SignatureVerificationStatus.SIGNING_BLOCK_ONLY]；
     *         两者都不可用时为 [SignatureVerificationStatus.FAILED]。
     */
    fun analyzeApk(apkFile: File): AppSignatureInfo {
        val verified = ApkSignatureAnalyzer.analyze(apkFile)
        if (verified.certificates.isNotEmpty()) return verified

        // 完整校验未取到证书（如仅 v4 签名、apksig 无法解析的容器）：回退到签名块声明
        val declared = runCatching { LightweightApkSignatureReader.read(apkFile) }.getOrNull()
        if (declared != null && declared.certificates.isNotEmpty()) {
            return declared.copy(
                warnings = verified.warnings,
                errors = verified.errors,
            )
        }
        return verified
    }

    /**
     * 比对 APK 签名与已安装应用签名。
     */
    fun match(context: Context, apkFile: File, installedPackageName: String?): SignatureMatchResult {
        val apkSignature = runCatching { analyzeApk(apkFile) }.getOrNull()
        val installedSignature = installedPackageName
            ?.takeIf { it.isNotEmpty() }
            ?.let { InstalledPackageSignatureReader.read(context, it) }

        val status = SignatureMatcher.match(
            pending = apkSignature,
            installed = installedSignature,
            installedPackageName = installedPackageName,
            hasSigningCertificate = { packageName, sha256 ->
                InstalledPackageSignatureReader.hasSigningCertificate(context, packageName, sha256)
            },
        )

        return SignatureMatchResult(
            status = status,
            apkSignature = apkSignature,
            installedSignature = installedSignature,
        )
    }
}

/**
 * 一次签名比对的结果集。
 */
data class SignatureMatchResult(
    val status: SignatureMatchStatus,
    val apkSignature: AppSignatureInfo?,
    val installedSignature: AppSignatureInfo?,
) {
    /** 待安装 APK 的主签名 SHA-256（可能为空） */
    val apkSha256: String? get() = apkSignature?.primarySha256
}
