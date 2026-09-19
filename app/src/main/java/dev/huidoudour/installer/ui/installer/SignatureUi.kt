package dev.huidoudour.installer.ui.installer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.huidoudour.installer.util.signature.AppSignatureInfo
import dev.huidoudour.installer.util.signature.SignatureMatchStatus
import dev.huidoudour.installer.util.signature.SignatureSummary
import dev.huidoudour.installer.util.signature.SignatureVerificationStatus
import dev.huidoudour.installer.ui.theme.SmallShape
import dev.huidoudour.installer.R

// 签名状态指示颜色
private val SignatureOkGreen = Color(0xFF388E3C)
private val SignatureErrorRed = Color(0xFFD32F2F)

/** 签名详情里最多展示的校验问题条数（apksig 对 v1-only 包可能产生上百条告警） */
private const val MAX_SHOWN_ISSUES = 5
private const val ELLIPSIS = "\n…"

/**
 * 一行简洁的签名状态指示（按比对结果着色）。
 * 仅在 [showDetails] 开启且签名适用时，点击文本可查看完整证书详情。
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

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .clip(SmallShape)
                .clickable(enabled = clickable) { onClick() },
            shape = SmallShape,
            color = statusColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
        ) {
            Text(
                text = signatureStatusText(summary),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
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
    return when (dialogSignatureStatus(summary)) {
        DialogSignatureStatus.MATCH -> stringResource(R.string.signature_status_match)
        DialogSignatureStatus.NORMAL -> stringResource(R.string.signature_status_normal)
        DialogSignatureStatus.ABNORMAL -> stringResource(R.string.signature_status_abnormal)
    }
}

private fun signatureStatusColor(summary: SignatureSummary): Color {
    return when (dialogSignatureStatus(summary)) {
        DialogSignatureStatus.MATCH,
        DialogSignatureStatus.NORMAL -> SignatureOkGreen
        DialogSignatureStatus.ABNORMAL -> SignatureErrorRed
    }
}

private enum class DialogSignatureStatus {
    MATCH,
    NORMAL,
    ABNORMAL,
}

/**
 * 独立安装对话框只保留三个结论：
 * 已安装包可确认兼容为“一致”，未安装且 APK 已完整验证为“正常”，
 * 不能验证、未签名或不兼容时均明确提示“异常”。
 */
private fun dialogSignatureStatus(summary: SignatureSummary): DialogSignatureStatus {
    val apkSignature = summary.apkSignature
    val isVerified = apkSignature?.verificationStatus == SignatureVerificationStatus.VERIFIED &&
        apkSignature.signerSha256Set.isNotEmpty()
    if (!isVerified) return DialogSignatureStatus.ABNORMAL

    return when (summary.status) {
        SignatureMatchStatus.MATCH,
        SignatureMatchStatus.ROTATION_COMPATIBLE -> DialogSignatureStatus.MATCH
        SignatureMatchStatus.NOT_INSTALLED -> DialogSignatureStatus.NORMAL
        SignatureMatchStatus.MISMATCH,
        SignatureMatchStatus.CANDIDATE_ROTATION_UNCONFIRMED,
        SignatureMatchStatus.UNKNOWN_ERROR -> DialogSignatureStatus.ABNORMAL
    }
}
