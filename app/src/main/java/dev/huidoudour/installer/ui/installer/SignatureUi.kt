package dev.huidoudour.installer.ui.installer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.huidoudour.installer.signature.AppSignatureInfo
import dev.huidoudour.installer.signature.SignatureMatchStatus
import dev.huidoudour.installer.signature.SignatureSummary
import dev.huidoudour.installer.signature.SignatureVerificationStatus
import dev.huidoudour.installer.ui.theme.SmallShape
import dev.huidoudour.installer.R

// 签名状态指示颜色
private val SignatureOkGreen = Color(0xFF388E3C)
private val SignatureWarnOrange = Color(0xFFFF9800)
private val SignatureErrorRed = Color(0xFFD32F2F)
private val SignatureInfoBlue = Color(0xFF1976D2)
private val SignatureNeutralGray = Color(0xFF757575)

/** 签名详情里最多展示的校验问题条数（apksig 对 v1-only 包可能产生上百条告警） */
private const val MAX_SHOWN_ISSUES = 5
private const val ELLIPSIS = "\n…"

/**
 * 一行签名状态指示（按比对结果着色）。
 * 仅在 [showDetails] 开启且签名适用时，点击可查看完整证书详情。
 */
@Composable
fun SignatureStatusRow(
    summary: SignatureSummary,
    showDetails: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val statusColor = signatureStatusColor(summary)
    val clickable = showDetails && summary.applicable

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .clickable(enabled = clickable) { onClick() },
        shape = SmallShape,
        color = statusColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = statusColor
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = signatureStatusText(summary),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
                summary.shortSha256?.let { sha ->
                    Text(
                        text = "SHA-256: $sha…",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (clickable) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 签名证书完整信息对话框。
 */
@Composable
fun SignatureDetailsDialog(
    summary: SignatureSummary,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.signature_details_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                val cert = summary.apkSignature?.certificates?.firstOrNull()
                if (!summary.applicable) {
                    Text(stringResource(R.string.signature_status_na))
                } else if (cert == null) {
                    Text(stringResource(R.string.signature_status_unknown))
                } else {
                    DetailLine(stringResource(R.string.signature_sha256), cert.sha256)
                    DetailLine(stringResource(R.string.signature_sha1), cert.sha1)
                    DetailLine(stringResource(R.string.signature_subject), cert.subject)
                    DetailLine(stringResource(R.string.signature_issuer), cert.issuer)
                    DetailLine(stringResource(R.string.signature_serial), cert.serialNumber)
                    cert.validFrom?.let {
                        DetailLine(stringResource(R.string.signature_valid_from), it)
                    }
                    cert.validUntil?.let {
                        DetailLine(stringResource(R.string.signature_valid_until), it)
                    }
                }

                // 校验强度与方案：区分“完整校验”与“仅签名块声明”，避免把弱判定当成确定结论
                summary.apkSignature?.let { info ->
                    DetailLine(
                        stringResource(R.string.signature_verification_status),
                        verificationStatusText(info)
                    )
                    val issues = (info.errors + info.warnings).distinct()
                    if (issues.isNotEmpty()) {
                        DetailLine(
                            stringResource(R.string.signature_verification_issues),
                            issues.take(MAX_SHOWN_ISSUES).joinToString("\n") +
                                if (issues.size > MAX_SHOWN_ISSUES) ELLIPSIS else ""
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun verificationStatusText(info: AppSignatureInfo): String {
    val label = when (info.verificationStatus) {
        SignatureVerificationStatus.VERIFIED ->
            stringResource(R.string.signature_verification_verified)
        SignatureVerificationStatus.SIGNING_BLOCK_ONLY ->
            stringResource(R.string.signature_verification_signing_block_only)
        SignatureVerificationStatus.FAILED ->
            stringResource(R.string.signature_verification_failed)
    }
    val schemes = (info.verifiedSchemes.ifEmpty { info.declaredSchemes }).distinct()
    return if (schemes.isEmpty()) label else "$label (${schemes.joinToString(", ")})"
}

@Composable
private fun signatureStatusText(summary: SignatureSummary): String {
    if (!summary.applicable) return stringResource(R.string.signature_status_na)
    return when (summary.status) {
        SignatureMatchStatus.MATCH -> stringResource(R.string.signature_status_match)
        SignatureMatchStatus.MISMATCH -> stringResource(R.string.signature_status_mismatch)
        SignatureMatchStatus.NOT_INSTALLED -> stringResource(R.string.signature_status_not_installed)
        SignatureMatchStatus.ROTATION_COMPATIBLE,
        SignatureMatchStatus.CANDIDATE_ROTATION_UNCONFIRMED ->
            stringResource(R.string.signature_status_rotation)
        SignatureMatchStatus.UNKNOWN_ERROR -> stringResource(R.string.signature_status_unknown)
    }
}

private fun signatureStatusColor(summary: SignatureSummary): Color {
    if (!summary.applicable) return SignatureNeutralGray
    return when (summary.status) {
        SignatureMatchStatus.MATCH,
        SignatureMatchStatus.ROTATION_COMPATIBLE -> SignatureOkGreen
        SignatureMatchStatus.MISMATCH -> SignatureErrorRed
        SignatureMatchStatus.NOT_INSTALLED -> SignatureInfoBlue
        SignatureMatchStatus.CANDIDATE_ROTATION_UNCONFIRMED -> SignatureWarnOrange
        SignatureMatchStatus.UNKNOWN_ERROR -> SignatureNeutralGray
    }
}
