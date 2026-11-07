package dev.huidoudour.installer.ui.licenses

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.huidoudour.installer.R
import dev.huidoudour.installer.ui.theme.CardShape
import dev.huidoudour.installer.ui.theme.SegmentedGap
import dev.huidoudour.installer.ui.theme.segmentedShape

/**
 * 「开源许可」页面：列出本项目与随包分发的第三方开源库、许可证及原文入口。
 *
 * 内容为静态清单（[LicenseCatalog]）。带内置原文的条目在应用内弹窗展示，
 * 其余条目点击跳转浏览器查看项目主页或许可证原文。
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var previewEntry by remember { mutableStateOf<LicenseEntry?>(null) }
    val onEntryClick: (LicenseEntry) -> Unit = { entry ->
        // 内置原文优先：应用内直接展示，不跳转外部浏览器
        if (entry.textRes != null) previewEntry = entry else openExternalLink(context, entry.url)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // 顶部返回栏
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.licenses_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }

        LicenseSection(
            title = stringResource(R.string.licenses_section_this_app),
            entries = LicenseCatalog.thisApp,
            onEntryClick = onEntryClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        LicenseSection(
            title = stringResource(R.string.licenses_section_dependencies),
            entries = LicenseCatalog.dependencies,
            onEntryClick = onEntryClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        LicenseSection(
            title = stringResource(R.string.licenses_section_license_texts),
            entries = LicenseCatalog.licenseTexts,
            onEntryClick = onEntryClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.licenses_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    previewEntry?.let { entry ->
        LocalLicenseDialog(entry = entry, onDismiss = { previewEntry = null })
    }
}

/** 与设置页一致的卡片容器：标题 + 许可条目列表。 */
@Composable
private fun LicenseSection(
    title: String,
    entries: List<LicenseEntry>,
    onEntryClick: (LicenseEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        entries.forEachIndexed { index, entry ->
            LicenseRow(
                entry = entry,
                shape = segmentedShape(index, entries.size),
                isLast = index == entries.lastIndex,
                onClick = { onEntryClick(entry) }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

/** 单条许可信息：名称 + 许可证，右侧为查看图标（内置原文）或外链图标。 */
@Composable
private fun LicenseRow(
    entry: LicenseEntry,
    shape: Shape,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceBright
        },
        label = "licenseRowBackground"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .heightIn(min = 56.dp),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                )
                Text(
                    text = entry.license
                        ?: stringResource(R.string.licenses_license_unspecified),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                imageVector = if (entry.textRes != null) {
                    Icons.AutoMirrored.Filled.ArrowForward
                } else {
                    Icons.AutoMirrored.Filled.OpenInNew
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (!isLast) {
        Spacer(modifier = Modifier.height(SegmentedGap))
    }
}

/** 用外部浏览器打开链接；无可用浏览器时以短 Toast 提示。 */
private fun openExternalLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(
            context,
            context.getString(R.string.open_link_failed),
            Toast.LENGTH_SHORT
        ).show()
    }
}

/** 在应用内弹窗展示随包内置的许可证原文，避免跳转外部。 */
@Composable
private fun LocalLicenseDialog(entry: LicenseEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // raw 原文很短（MIT 约 1KB），同步读取一次即可，无需异步加载
    val text = remember(entry) {
        entry.textRes?.let { res ->
            runCatching {
                context.resources.openRawResource(res).bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                if (entry.license != null) {
                    Text(
                        text = entry.license,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 标题区与许可正文之间的分隔线
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}
