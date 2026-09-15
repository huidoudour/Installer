package dev.huidoudour.installer.signature

/**
 * 比对待安装 APK 与已安装应用的签名，判定是否兼容。
 * 逻辑移植自 InstallerX Revived 的 analyzePackageSignatureMatch。
 *
 * 判定规则与校验强度绑定：
 * - [SignatureVerificationStatus.VERIFIED]：apksig 完整校验通过，可给出 MATCH / MISMATCH 等确定结论；
 * - [SignatureVerificationStatus.SIGNING_BLOCK_ONLY]：只读到签名块声明，只能确认“声明一致”，
 *   声明不一致时因为未经验证，一律返回 [SignatureMatchStatus.UNKNOWN_ERROR] 而不是 MISMATCH；
 * - [SignatureVerificationStatus.FAILED]：校验失败（可能被篡改），不做任何签名可信判定。
 */
object SignatureMatcher {

    fun match(
        pending: AppSignatureInfo?,
        installed: AppSignatureInfo?,
        installedPackageName: String?,
        hasSigningCertificate: (packageName: String, certificateSha256: String) -> Boolean = { _, _ -> false },
    ): SignatureMatchStatus {
        if (installedPackageName.isNullOrEmpty()) return SignatureMatchStatus.NOT_INSTALLED
        if (installed == null) return SignatureMatchStatus.NOT_INSTALLED

        val installedSignerSet = installed.signerSha256Set.takeIf { it.isNotEmpty() }
        val pendingSignerSet = pending?.signerSha256Set?.takeIf { it.isNotEmpty() }
        if (pendingSignerSet == null || installedSignerSet == null) return SignatureMatchStatus.UNKNOWN_ERROR

        // 校验失败（或仅签名块声明）时不得基于未验证的证书判定“签名不一致”
        val pendingVerification = pending.verificationStatus
        if (pendingVerification == SignatureVerificationStatus.FAILED) {
            return SignatureMatchStatus.UNKNOWN_ERROR
        }
        val pendingVerified = pendingVerification == SignatureVerificationStatus.VERIFIED

        return when {
            pendingSignerSet == installedSignerSet -> SignatureMatchStatus.MATCH

            !pendingVerified -> SignatureMatchStatus.UNKNOWN_ERROR

            isRotationCompatible(installed, installedPackageName, pendingSignerSet, hasSigningCertificate) ->
                SignatureMatchStatus.ROTATION_COMPATIBLE

            pending.isCandidateRotationUnconfirmed(pendingSignerSet, installedSignerSet) ->
                SignatureMatchStatus.CANDIDATE_ROTATION_UNCONFIRMED

            else -> SignatureMatchStatus.MISMATCH
        }
    }

    private fun isRotationCompatible(
        installed: AppSignatureInfo,
        installedPackageName: String,
        pendingSignerSet: Set<String>,
        hasSigningCertificate: (packageName: String, certificateSha256: String) -> Boolean,
    ): Boolean {
        if (installed.hasMultipleSigners || pendingSignerSet.size != 1) return false

        val hasHistoryMatch = pendingSignerSet.all { sha256 ->
            sha256 in installed.signingCertificateHistorySha256Set
        }
        val packageManagerConfirms = pendingSignerSet.all { sha256 ->
            hasSigningCertificate(installedPackageName, sha256)
        }

        return hasHistoryMatch || packageManagerConfirms
    }

    private fun AppSignatureInfo?.isCandidateRotationUnconfirmed(
        pendingSignerSet: Set<String>,
        installedSignerSet: Set<String>,
    ): Boolean {
        if (this == null) return false
        if (hasMultipleSigners || pendingSignerSet.size != 1 || installedSignerSet.size != 1) {
            return false
        }

        val lineage = signingCertificateHistorySha256Set
        return lineage.size > 1 &&
            pendingSignerSet.all { it in lineage } &&
            installedSignerSet.all { it in lineage }
    }
}
