package dev.huidoudour.installer.util.signature

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log

/**
 * 读取已安装应用的签名信息（以 PackageManager 的 SigningInfo 为准）。
 */
object InstalledPackageSignatureReader {

    private const val TAG = "InstalledSigReader"

    /**
     * 判断已安装包的签名历史中是否包含指定 SHA-256 证书。
     * 仅 API 28+ 支持。
     */
    fun hasSigningCertificate(context: Context, packageName: String, certificateSha256: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val certificateBytes = certificateSha256.hexToByteArrayOrNull() ?: return false
        return runCatching {
            context.packageManager.hasSigningCertificate(
                packageName,
                certificateBytes,
                PackageManager.CERT_INPUT_SHA256,
            )
        }.getOrDefault(false)
    }

    fun read(context: Context, packageName: String): AppSignatureInfo? {
        return try {
            val packageInfo = getInstalledPackageInfo(context, packageName) ?: return null
            val signatures = getInstalledSignatures(packageInfo)
            if (signatures.current.isEmpty()) return null

            val certificates = signatures.current.map(CertificateFormatter::format)
            val signingCertificateHistory = signatures.history.map(CertificateFormatter::format)
            AppSignatureInfo(
                verified = true,
                signerSha256Set = certificates.mapTo(linkedSetOf()) { it.sha256 },
                certificates = certificates,
                signingCertificateHistory = signingCertificateHistory,
                hasMultipleSigners = signatures.hasMultipleSigners,
            )
        } catch (_: PackageManager.NameNotFoundException) {
            Log.d(TAG, "Package not found, can't get signature: $packageName")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get signature hash for installed package: $packageName", e)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackageInfo(context: Context, packageName: String): PackageInfo? {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getPackageInfo(packageName, flags)
        }
    }

    private fun getInstalledSignatures(packageInfo: PackageInfo): InstalledSignatures {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            when {
                signingInfo == null -> InstalledSignatures()

                signingInfo.hasMultipleSigners() -> InstalledSignatures(
                    current = signingInfo.apkContentsSigners?.toList().orEmpty(),
                    hasMultipleSigners = true,
                )

                else -> {
                    val history = signingInfo.signingCertificateHistory?.toList().orEmpty()
                    val current = history.lastOrNull()?.let(::listOf)
                        ?: signingInfo.apkContentsSigners?.toList().orEmpty()
                    InstalledSignatures(
                        current = current,
                        history = history,
                        hasMultipleSigners = false,
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val signatures = packageInfo.signatures?.toList().orEmpty()
            InstalledSignatures(
                current = signatures,
                hasMultipleSigners = signatures.size > 1,
            )
        }
    }

    private fun String.hexToByteArrayOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        val bytes = ByteArray(length / 2)
        for (index in bytes.indices) {
            val high = Character.digit(this[index * 2], 16)
            val low = Character.digit(this[index * 2 + 1], 16)
            if (high < 0 || low < 0) return null
            bytes[index] = ((high shl 4) + low).toByte()
        }
        return bytes
    }

    private data class InstalledSignatures(
        val current: List<Signature> = emptyList(),
        val history: List<Signature> = emptyList(),
        val hasMultipleSigners: Boolean = false,
    )
}
