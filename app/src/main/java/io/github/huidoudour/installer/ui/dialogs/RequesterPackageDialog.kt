package io.github.huidoudour.installer.ui.dialogs

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.huidoudour.installer.R

private val requesterOptions = listOf(
    InstallerPackageOption("io.github.huidoudour.Installer", "Installer"),
    InstallerPackageOption("me.huidoudour.core", "Huidoudour Core"),
    InstallerPackageOption("io.github.huidoudour.zjs", "ZJS"),
    InstallerPackageOption("com.android.shell", "Shell"),
)

fun getCurrentRequesterPackage(context: Context): String {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val saved = prefs.getString("requester_package", "")
    return saved?.ifEmpty { "me.huidoudour.core" } ?: "me.huidoudour.core"
}

fun saveRequesterPackage(context: Context, packageName: String) {
    context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        .edit().putString("requester_package", packageName).apply()
}

fun getRequesterPackageDisplayName(context: Context): String {
    val pkg = getCurrentRequesterPackage(context)
    return requesterOptions.find { it.packageName == pkg }?.displayName ?: pkg
}

@Composable
fun RequesterPackageDialog(
    context: Context,
    onDismiss: () -> Unit,
    onConfirmed: (String) -> Unit
) {
    val currentPackage = getCurrentRequesterPackage(context)
    var selectedPackage by remember { mutableStateOf(currentPackage) }

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
                    text = stringResource(R.string.requester_package_settings),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                requesterOptions.forEachIndexed { index, option ->
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        saveRequesterPackage(context, selectedPackage)
                        onConfirmed(selectedPackage)
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}
