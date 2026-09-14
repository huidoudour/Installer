package dev.huidoudour.installer.signature

import android.content.pm.Signature
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 将编码后的证书字节 / [Signature] / [X509Certificate] 格式化为 [SignatureCertificateInfo]。
 */
object CertificateFormatter {

    fun format(encodedCertificate: ByteArray): SignatureCertificateInfo {
        val certificate = encodedCertificate.toX509CertificateOrNull()
        return SignatureCertificateInfo(
            sha256 = encodedCertificate.digestHex(SHA_256),
            sha1 = encodedCertificate.digestHex(SHA_1),
            subject = certificate?.subjectX500Principal?.name ?: UNKNOWN,
            issuer = certificate?.issuerX500Principal?.name ?: UNKNOWN,
            serialNumber = certificate?.serialNumber?.toString(16) ?: UNKNOWN,
            validFrom = certificate?.notBefore?.formatUtc(),
            validUntil = certificate?.notAfter?.formatUtc(),
            publicKeyAlgorithm = certificate?.publicKey?.algorithm,
            signatureAlgorithm = certificate?.sigAlgName,
        )
    }

    fun format(signature: Signature): SignatureCertificateInfo = format(signature.toByteArray())

    fun format(certificate: X509Certificate): SignatureCertificateInfo {
        val encoded = certificate.encoded
        return SignatureCertificateInfo(
            sha256 = encoded.digestHex(SHA_256),
            sha1 = encoded.digestHex(SHA_1),
            subject = certificate.subjectX500Principal.name,
            issuer = certificate.issuerX500Principal.name,
            serialNumber = certificate.serialNumber.toString(16),
            validFrom = certificate.notBefore.formatUtc(),
            validUntil = certificate.notAfter.formatUtc(),
            publicKeyAlgorithm = certificate.publicKey.algorithm,
            signatureAlgorithm = certificate.sigAlgName,
        )
    }

    private fun ByteArray.toX509CertificateOrNull() = runCatching {
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(this)) as X509Certificate
    }.getOrNull()

    private fun ByteArray.digestHex(algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        return digest.digest(this).joinToString("") { "%02x".format(it) }
    }

    private fun java.util.Date.formatUtc(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(this)
    }

    private const val SHA_1 = "SHA-1"
    private const val SHA_256 = "SHA-256"
    private const val UNKNOWN = "Unknown"
}
