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

/**
 * 语言选择对话框 - Material Design 3 风格
 */
@Composable
fun LanguageSelectionDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    getDisplayName: (String) -> String = { it }
) {
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }
    
    val languages = listOf(
        LanguageOption("system", "Follow System", "跟随系统", false),
        LanguageOption("zh", "简体中文", "Simplified Chinese", true),
        LanguageOption("en", "English", "English", false),
        LanguageOption("zh-TW", "繁體中文", "Traditional Chinese", false),
        LanguageOption("ru", "Русский", "Russian", false),
        LanguageOption("ja", "日本語", "Japanese", false),
        LanguageOption("zh-HK", "喵语中文", "Meow Language", false)
    )
    
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
                    text = stringResource(R.string.language_settings),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 语言选项
                languages.forEachIndexed { index, lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLanguage = lang.code }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedLanguage == lang.code,
                            onClick = { selectedLanguage = lang.code }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = lang.nativeName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = getDisplayName(lang.code),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (index < languages.lastIndex) {
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
                        if (selectedLanguage != currentLanguage) {
                            onConfirm(selectedLanguage)
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

private data class LanguageOption(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val showTranslateIcon: Boolean = false
)
