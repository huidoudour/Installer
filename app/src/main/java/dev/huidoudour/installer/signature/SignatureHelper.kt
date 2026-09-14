package dev.huidoudour.installer.signature

import android.content.Context
import java.io.File

/**
 * 签名校验门面：快速解析待安装 APK 的签名，并与已安装应用进行比对。
 */
object SignatureHelper {

    /**
     * 快速解析 APK 签名信息。
     * 只读取 APK Signing Block 的签名证书声明（不做整包摘要校验），毫秒级完成。
     */
    fun analyzeApk(apkFile: File): AppSignatureInfo = LightweightApkSignatureReader.read(apkFile)

    /**
     * 读取已安装应用的签名信息。
     */
    fun readInstalled(context: Context, packageName: String): AppSignatureInfo? =
        InstalledPackageSignatureReader.read(context, packageName)

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
