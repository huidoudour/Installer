package io.github.huidoudour.Installer.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import io.github.huidoudour.Installer.util.ThemeManager

/**
 * 主题选择对话框 - Material Design 3 风格
 */
@Composable
fun ThemeSelectionDialog(
    currentTheme: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedTheme by remember { mutableStateOf(currentTheme) }
    
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
                // 标题
                Text(
                    text = stringResource(R.string.theme_settings),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 主题选项
                val themeOptions = listOf(
                    Pair(ThemeManager.THEME_FOLLOW_SYSTEM, stringResource(R.string.theme_follow_system)),
                    Pair(ThemeManager.THEME_LIGHT, stringResource(R.string.theme_light)),
                    Pair(ThemeManager.THEME_DARK, stringResource(R.string.theme_dark))
                )

                themeOptions.forEachIndexed { index, (themeValue, themeName) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTheme = themeValue }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTheme == themeValue,
                            onClick = { selectedTheme = themeValue }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = themeName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (index < themeOptions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 48.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }

                // 操作按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        if (selectedTheme != currentTheme) {
                            onConfirm(selectedTheme)
                        } else {
                            onDismiss()
                        }
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}
