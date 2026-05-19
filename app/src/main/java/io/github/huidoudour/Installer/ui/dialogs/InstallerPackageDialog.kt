package io.github.huidoudour.Installer.ui.dialogs

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import io.github.huidoudour.Installer.R

data class InstallerPackageOption(
    val packageName: String,
    val displayName: String
)

private val installerOptions = listOf(
    InstallerPackageOption("io.github.huidoudour.Installer", "Installer"),
    InstallerPackageOption("me.huidoudour.core", "Huidoudour Core"),
    InstallerPackageOption("io.github.huidoudour.zjs", "ZJS")
)

fun getCurrentInstallerPackage(context: Context): String {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val saved = prefs.getString("installer_package", "")
    return saved?.ifEmpty { "io.github.huidoudour.Installer" } ?: "io.github.huidoudour.Installer"
}

fun saveInstallerPackage(context: Context, packageName: String) {
    context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        .edit().putString("installer_package", packageName).apply()
}

fun getInstallerPackageDisplayName(context: Context): String {
    val pkg = getCurrentInstallerPackage(context)
    return installerOptions.find { it.packageName == pkg }?.displayName ?: pkg
}

@Composable
fun InstallerPackageDialog(
    context: Context,
    onDismiss: () -> Unit,
    onConfirmed: (String) -> Unit
) {
    val currentPackage = getCurrentInstallerPackage(context)
    var selectedPackage by remember { mutableStateOf(currentPackage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.installer_package_settings),
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                installerOptions.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPackage = option.packageName }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPackage == option.packageName,
                            onClick = { selectedPackage = option.packageName }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = option.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = option.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (index < installerOptions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 48.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                saveInstallerPackage(context, selectedPackage)
                onConfirmed(selectedPackage)
            }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
