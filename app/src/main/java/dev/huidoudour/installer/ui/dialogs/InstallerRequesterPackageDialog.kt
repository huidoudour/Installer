package dev.huidoudour.installer.ui.dialogs

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.huidoudour.installer.R
import dev.huidoudour.installer.util.LogManager

fun getCurrentInstallerPackage(context: Context): String =
    context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        .getString("installer_package", "")
        ?.ifEmpty { "io.github.huidoudour.Installer" }
        ?: "io.github.huidoudour.Installer"

fun saveInstallerPackage(context: Context, packageName: String) {
    context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        .edit()
        .putString("installer_package", packageName)
        .apply()
}

fun getCurrentRequesterPackage(context: Context): String {
    val saved = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        .getString("requester_package", "")
    return saved?.ifEmpty { "me.huidoudour.core" } ?: "me.huidoudour.core"
}

fun saveRequesterPackage(context: Context, packageName: String) {
    context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        .edit()
        .putString("requester_package", packageName)
        .apply()
}

/** 请求者可选包名，按文本长度由短到长排列。 */
private val requesterOptions = listOf(
    "me.huidoudour.core",
    "io.github.huidoudour.zjs",
    "me.huidoudour.file.manager",
    "io.github.huidoudour.Installer",
)

/** 执行者可选包名（安装者身份，对应 pm install 的 -i），按文本长度由短到长排列。 */
private val installerOptions = listOf(
    "com.android.shell",
    "me.huidoudour.core",
    "io.github.huidoudour.zjs",
    "io.github.huidoudour.Installer",
)

/**
 * 紧凑单选按钮：关闭 Material3 默认 48dp 最小触摸区并限制尺寸，
 * 以压缩列表行的垂直间距（整行本身可点击，触控区域仍为整行宽）。
 */
@Composable
private fun CompactRadioButton(
    selected: Boolean,
    onClick: () -> Unit
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun InstallerRequesterPackageDialog(
    context: Context,
    onDismiss: () -> Unit,
    onInstallerConfirmed: (String) -> Unit,
    onRequesterConfirmed: (String) -> Unit
) {
    val density = LocalDensity.current
    val currentInstaller = getCurrentInstallerPackage(context)
    val currentRequester = getCurrentRequesterPackage(context)
    var selectedInstaller by remember { mutableStateOf(currentInstaller) }
    var selectedRequester by remember { mutableStateOf(currentRequester) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.package_select_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // === 请求者 ===
                Text(
                    text = stringResource(R.string.requester_section_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                requesterOptions.forEachIndexed { index, packageName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedRequester = packageName
                                LogManager.getInstance().addLog(
                                    "Requester package option selected: $packageName",
                                    "Install UI"
                                )
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactRadioButton(
                            selected = selectedRequester == packageName,
                            onClick = {
                                selectedRequester = packageName
                                LogManager.getInstance().addLog(
                                    "Requester package option selected: $packageName",
                                    "Install UI"
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        // 包名独占一行，分隔线宽度跟随包名文本长度。
                        Column(modifier = Modifier.weight(1f)) {
                            var nameWidth by remember { mutableStateOf(0.dp) }
                            Text(
                                text = packageName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = {
                                    nameWidth = with(density) { it.size.width.toDp() }
                                }
                            )
                            if (index < requesterOptions.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .width(nameWidth),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(8.dp))

                // === 执行者 ===
                Text(
                    text = stringResource(R.string.installer_section_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                installerOptions.forEachIndexed { index, packageName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedInstaller = packageName
                                LogManager.getInstance().addLog(
                                    "Installer package option selected: $packageName",
                                    "Install UI"
                                )
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactRadioButton(
                            selected = selectedInstaller == packageName,
                            onClick = {
                                selectedInstaller = packageName
                                LogManager.getInstance().addLog(
                                    "Installer package option selected: $packageName",
                                    "Install UI"
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        // 包名独占一行，分隔线宽度跟随包名文本长度。
                        Column(modifier = Modifier.weight(1f)) {
                            var nameWidth by remember { mutableStateOf(0.dp) }
                            Text(
                                text = packageName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = {
                                    nameWidth = with(density) { it.size.width.toDp() }
                                }
                            )
                            if (index < installerOptions.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .width(nameWidth),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        LogManager.getInstance().addLog("Installer/requester selection cancelled", "Install UI")
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        LogManager.getInstance().addLog(
                            "Installer/requester selection confirmed: installer=$selectedInstaller, requester=$selectedRequester",
                            "Install UI"
                        )
                        saveInstallerPackage(context, selectedInstaller)
                        onInstallerConfirmed(selectedInstaller)
                        saveRequesterPackage(context, selectedRequester)
                        onRequesterConfirmed(selectedRequester)
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}
