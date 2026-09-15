package dev.huidoudour.installer.signature

import android.os.Build
import android.util.Log
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel

/**
 * 快速读取 APK Signature Scheme v2/v3.x 块中的签名证书声明。
 *
 * 该实现刻意不校验签名本身、也不校验 APK 内容摘要，因此速度极快（只读取文件尾部的
 * EOCD 与 APK Signing Block）。其结果足以支撑“证书比对 / 与已安装应用签名匹配”，
 * 最终签名校验仍由系统或特权安装器负责。
 *
 * 注意：[ApkSignatureAnalyzer] 的完整校验才是签名比对的权威来源，本读取器只在
 * [SignatureHelper.analyzeApk] 无法从完整校验取得证书时作为回退使用；
 * 它读不到仅 v1（JAR）签名的 APK——这类包没有 APK Signing Block。
 *
 * 参考 InstallerX Revived 的 LightweightApkSignatureReader。
 */
object LightweightApkSignatureReader {

    private const val TAG = "LightweightSigReader"

    fun read(file: File): AppSignatureInfo = read(file, Build.VERSION.SDK_INT)

    internal fun read(file: File, platformSdk: Int): AppSignatureInfo {
        val declarations = runCatching {
            RandomAccessFile(file, "r").use { raf -> readDeclarations(raf.channel, platformSdk) }
        }.onFailure { error ->
            Log.w(TAG, "Unable to read lightweight APK signer declarations: ${file.absolutePath}", error)
        }.getOrDefault(SignatureDeclarations())

        val uniqueCertificates = declarations.certificates.fold(mutableListOf<ByteArray>()) { result, certificate ->
            if (result.none(certificate::contentEquals)) result += certificate
            result
        }
        val certificates = uniqueCertificates.map(CertificateFormatter::format)

        return AppSignatureInfo(
            verified = false,
            signerSha256Set = certificates.mapTo(linkedSetOf()) { it.sha256 },
            certificates = certificates,
            hasMultipleSigners = certificates.size > 1,
            declaredSchemes = declarations.schemes,
            verificationStatus = SignatureVerificationStatus.SIGNING_BLOCK_ONLY,
        )
    }

    private fun readDeclarations(channel: SeekableByteChannel, platformSdk: Int): SignatureDeclarations {
        val fileSize = channel.size()
        if (fileSize < ZIP_EOCD_MIN_SIZE) return SignatureDeclarations()

        val eocd = findEocd(channel, fileSize) ?: return SignatureDeclarations()
        val centralDirectoryOffset = eocd.getUnsignedInt(ZIP_EOCD_CENTRAL_DIRECTORY_OFFSET)
        if (centralDirectoryOffset !in APK_SIGNING_BLOCK_FOOTER_SIZE..fileSize) {
            return SignatureDeclarations()
        }

        val footer = channel.readFully(
            centralDirectoryOffset - APK_SIGNING_BLOCK_FOOTER_SIZE,
            APK_SIGNING_BLOCK_FOOTER_SIZE,
        )
        if (!footer.matchesMagic(APK_SIGNING_BLOCK_MAGIC_OFFSET, APK_SIGNING_BLOCK_MAGIC)) {
            return SignatureDeclarations()
        }

        val sizeInFooter = footer.getLong(0)
        if (sizeInFooter < APK_SIGNING_BLOCK_FOOTER_SIZE || sizeInFooter > MAX_SIGNING_BLOCK_SIZE - 8L) {
            throw IOException("APK Signing Block size is outside the lightweight reader limit: $sizeInFooter")
        }
        val totalSize = sizeInFooter + 8L
        val blockOffset = centralDirectoryOffset - totalSize
        if (blockOffset < 0L) throw IOException("APK Signing Block starts before the APK")

        val block = channel.readFully(blockOffset, totalSize.toInt())
        if (block.getLong(0) != sizeInFooter) throw IOException("APK Signing Block size fields differ")
        if (!block.matchesMagic(block.limit() - APK_SIGNING_BLOCK_MAGIC.size, APK_SIGNING_BLOCK_MAGIC)) {
            throw IOException("APK Signing Block magic is missing")
        }

        val pairs = block.sliceRange(8, block.limit() - APK_SIGNING_BLOCK_FOOTER_SIZE)
        val certificatesByScheme = mutableMapOf<String, MutableList<ByteArray>>()
        val schemes = linkedSetOf<String>()
        while (pairs.hasRemaining()) {
            if (pairs.remaining() < 8) throw IOException("Truncated APK Signing Block pair")
            val pairSize = pairs.long.toBoundedInt(pairs.remaining(), "APK Signing Block pair")
            val pair = pairs.readSlice(pairSize)
            if (pair.remaining() < 4) throw IOException("APK Signing Block pair has no ID")
            val id = pair.int
            val scheme = SCHEMES[id] ?: continue
            certificatesByScheme.getOrPut(scheme.label, ::mutableListOf) +=
                parseSchemeSigners(pair, scheme.hasSdkRange, platformSdk)
            schemes += scheme.label
        }
        val certificates = when {
            platformSdk >= MIN_SDK_WITH_V32_SUPPORT &&
                certificatesByScheme[SCHEME_V32].isNullOrEmpty().not() ->
                certificatesByScheme.getValue(SCHEME_V32)

            platformSdk >= MIN_SDK_WITH_V31_SUPPORT &&
                certificatesByScheme[SCHEME_V31].isNullOrEmpty().not() ->
                certificatesByScheme.getValue(SCHEME_V31)

            platformSdk >= MIN_SDK_WITH_V3_SUPPORT &&
                certificatesByScheme[SCHEME_V3].isNullOrEmpty().not() ->
                certificatesByScheme.getValue(SCHEME_V3)

            else -> certificatesByScheme[SCHEME_V2].orEmpty()
        }
        return SignatureDeclarations(certificates, schemes.toList())
    }

    private fun parseSchemeSigners(block: ByteBuffer, hasSdkRange: Boolean, platformSdk: Int): List<ByteArray> {
        val signers = block.readLengthPrefixedSlice()
        val certificates = mutableListOf<ByteArray>()
        var signerCount = 0
        while (signers.hasRemaining()) {
            if (++signerCount > MAX_SIGNER_COUNT) throw IOException("Too many APK signer declarations")
            val signer = signers.readLengthPrefixedSlice()
            val signedData = signer.readLengthPrefixedSlice()
            val sdkRange = if (hasSdkRange) {
                signer.requireRemaining(8, "signer SDK range")
                readSdkRange(signer, "signer")
            } else {
                null
            }
            signer.readLengthPrefixedSlice() // signatures
            signer.readLengthPrefixedBytes() // public key
            val certificate = parseSignedDataSignerCertificate(signedData, sdkRange)
            if (sdkRange == null || platformSdk in sdkRange) {
                certificate?.let(certificates::add)
            }
        }
        return certificates
    }

    private fun parseSignedDataSignerCertificate(signedData: ByteBuffer, expectedSdkRange: IntRange?): ByteArray? {
        signedData.readLengthPrefixedSlice() // content digests; deliberately not verified
        val certificateSequence = signedData.readLengthPrefixedSlice()
        if (expectedSdkRange != null) {
            signedData.requireRemaining(8, "signed-data SDK range")
            val signedDataSdkRange = readSdkRange(signedData, "signed-data")
            if (signedDataSdkRange != expectedSdkRange) {
                throw IOException(
                    "Signer SDK range differs from signed-data SDK range: " +
                        "signer=$expectedSdkRange, signedData=$signedDataSdkRange",
                )
            }
        }
        signedData.readLengthPrefixedSlice() // additional attributes

        var signerCertificate: ByteArray? = null
        var certificateCount = 0
        while (certificateSequence.hasRemaining()) {
            if (++certificateCount > MAX_CERTIFICATE_COUNT) {
                throw IOException("Too many certificates in an APK signer declaration")
            }
            val certificate = certificateSequence.readLengthPrefixedSlice()
            if (certificate.remaining() > MAX_CERTIFICATE_SIZE) {
                throw IOException("APK signer certificate is too large: ${certificate.remaining()}")
            }
            if (signerCertificate == null) {
                signerCertificate = ByteArray(certificate.remaining()).also(certificate::get)
            }
        }
        return signerCertificate
    }

    private fun readSdkRange(buffer: ByteBuffer, label: String): IntRange {
        val minSdk = buffer.int
        val maxSdk = buffer.int
        if (minSdk !in 0..maxSdk) {
            throw IOException("Invalid $label SDK range: min=$minSdk, max=$maxSdk")
        }
        return minSdk..maxSdk
    }

    private fun findEocd(channel: SeekableByteChannel, fileSize: Long): ByteBuffer? {
        val tailSize = minOf(fileSize, ZIP_EOCD_MAX_SIZE.toLong()).toInt()
        val tail = channel.readFully(fileSize - tailSize, tailSize)
        for (offset in tail.limit() - ZIP_EOCD_MIN_SIZE downTo 0) {
            if (tail.getInt(offset) != ZIP_EOCD_SIGNATURE) continue
            val commentLength = tail.getUnsignedShort(offset + ZIP_EOCD_COMMENT_LENGTH_OFFSET)
            if (offset + ZIP_EOCD_MIN_SIZE + commentLength == tail.limit()) {
                return tail.sliceRange(offset, tail.limit())
            }
        }
        return null
    }

    private fun SeekableByteChannel.readFully(offset: Long, size: Int): ByteBuffer {
        if (offset < 0L || size < 0 || offset > this.size() || size.toLong() > this.size() - offset) {
            throw IOException("Requested APK range is outside the source: offset=$offset, size=$size")
        }
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        position(offset)
        while (buffer.hasRemaining()) {
            when (read(buffer)) {
                -1 -> throw EOFException("Unexpected end of APK while reading signer declarations")
                0 -> continue
            }
        }
        buffer.flip()
        return buffer
    }

    private fun ByteBuffer.readLengthPrefixedSlice(): ByteBuffer {
        requireRemaining(4, "length-prefixed field")
        return readSlice(int.toBoundedInt(remaining(), "length-prefixed field"))
    }

    private fun ByteBuffer.readLengthPrefixedBytes(): ByteArray {
        val value = readLengthPrefixedSlice()
        return ByteArray(value.remaining()).also(value::get)
    }

    private fun ByteBuffer.readSlice(size: Int): ByteBuffer {
        requireRemaining(size, "field")
        val result = slice().order(ByteOrder.LITTLE_ENDIAN)
        result.limit(size)
        position(position() + size)
        return result
    }

    private fun ByteBuffer.sliceRange(start: Int, end: Int): ByteBuffer {
        if (start !in 0..end || end > limit()) throw IOException("Invalid APK buffer range")
        val duplicate = duplicate().order(ByteOrder.LITTLE_ENDIAN)
        duplicate.position(start)
        duplicate.limit(end)
        return duplicate.slice().order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun ByteBuffer.requireRemaining(size: Int, label: String) {
        if (size < 0 || remaining() < size) {
            throw IOException("Truncated $label: need=$size, remaining=${remaining()}")
        }
    }

    private fun Long.toBoundedInt(available: Int, label: String): Int {
        if (this < 0L || this > available.toLong() || this > Int.MAX_VALUE.toLong()) {
            throw IOException("Invalid $label size: $this, available=$available")
        }
        return toInt()
    }

    private fun Int.toBoundedInt(available: Int, label: String): Int {
        if (this !in 0..available) throw IOException("Invalid $label size: $this, available=$available")
        return this
    }

    private fun ByteBuffer.getUnsignedInt(offset: Int): Long = getInt(offset).toLong() and 0xffffffffL

    private fun ByteBuffer.getUnsignedShort(offset: Int): Int = getShort(offset).toInt() and 0xffff

    private fun ByteBuffer.matchesMagic(offset: Int, magic: ByteArray): Boolean = offset >= 0 && offset + magic.size <= limit() && magic.indices.all { index ->
        get(offset + index) == magic[index]
    }

    private data class SignatureDeclarations(
        val certificates: List<ByteArray> = emptyList(),
        val schemes: List<String> = emptyList(),
    )

    private data class Scheme(val label: String, val hasSdkRange: Boolean)

    private const val ZIP_EOCD_SIGNATURE = 0x06054b50
    private const val ZIP_EOCD_MIN_SIZE = 22
    private const val ZIP_EOCD_MAX_SIZE = ZIP_EOCD_MIN_SIZE + 0xffff
    private const val ZIP_EOCD_CENTRAL_DIRECTORY_OFFSET = 16
    private const val ZIP_EOCD_COMMENT_LENGTH_OFFSET = 20
    private const val APK_SIGNING_BLOCK_FOOTER_SIZE = 24
    private const val APK_SIGNING_BLOCK_MAGIC_OFFSET = 8
    private const val MAX_SIGNING_BLOCK_SIZE = 8 * 1024 * 1024
    private const val MAX_SIGNER_COUNT = 32
    private const val MAX_CERTIFICATE_COUNT = 64
    private const val MAX_CERTIFICATE_SIZE = 64 * 1024
    private const val MIN_SDK_WITH_V3_SUPPORT = Build.VERSION_CODES.P
    private const val MIN_SDK_WITH_V31_SUPPORT = Build.VERSION_CODES.TIRAMISU
    private const val MIN_SDK_WITH_V32_SUPPORT = Build.VERSION_CODES.CINNAMON_BUN
    private const val SCHEME_V2 = "V2"
    private const val SCHEME_V3 = "V3"
    private const val SCHEME_V31 = "V3.1"
    private const val SCHEME_V32 = "V3.2"
    private val APK_SIGNING_BLOCK_MAGIC = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
    private val SCHEMES = mapOf(
        0x7109871a to Scheme(SCHEME_V2, hasSdkRange = false),
        0xf05368c0.toInt() to Scheme(SCHEME_V3, hasSdkRange = true),
        0x1b93ad61 to Scheme(SCHEME_V31, hasSdkRange = true),
        0x70e1c89f to Scheme(SCHEME_V32, hasSdkRange = true),
    )
}
