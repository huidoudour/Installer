package dev.huidoudour.installer.signature

/**
 * 比对待安装 APK 与已安装应用的签名，判定是否兼容。
 * 逻辑移植自 InstallerX Revived 的 analyzePackageSignatureMatch。
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

        val pendingSignerSet = pending?.signerSha256Set?.takeIf { it.isNotEmpty() && pending.verified }
        val installedSignerSet = installed.signerSha256Set.takeIf { it.isNotEmpty() }

        return when {
            pendingSignerSet == null || installedSignerSet == null -> SignatureMatchStatus.UNKNOWN_ERROR

            pendingSignerSet == installedSignerSet -> SignatureMatchStatus.MATCH

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
