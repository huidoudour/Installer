package io.github.huidoudour.Installer.ui.dialogs

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.huidoudour.Installer.R

private val installerOptions = listOf(
    InstallerPackageOption("io.github.huidoudour.Installer", "Installer"),
    InstallerPackageOption("me.huidoudour.core", "Huidoudour Core"),
    InstallerPackageOption("io.github.huidoudour.zjs", "ZJS"),
)

private val requesterOptions = listOf(
    InstallerPackageOption("io.github.huidoudour.Installer", "Installer"),
    InstallerPackageOption("me.huidoudour.core", "Huidoudour Core"),
    InstallerPackageOption("io.github.huidoudour.zjs", "ZJS"),
    InstallerPackageOption("com.android.shell", "Shell"),
)

@Composable
fun InstallerRequesterPackageDialog(
    context: Context,
    onDismiss: () -> Unit,
    onInstallerConfirmed: (String) -> Unit,
    onRequesterConfirmed: (String) -> Unit
) {
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

                // === 执行者 ===
                Text(
                    text = stringResource(R.string.installer_section_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                installerOptions.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedInstaller = option.packageName }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedInstaller == option.packageName,
                            onClick = { selectedInstaller = option.packageName }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = option.packageName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (index < installerOptions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 48.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))

                // === 请求者 ===
                Text(
                    text = stringResource(R.string.requester_section_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                requesterOptions.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRequester = option.packageName }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRequester == option.packageName,
                            onClick = { selectedRequester = option.packageName }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = option.packageName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (index < requesterOptions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 48.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
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
