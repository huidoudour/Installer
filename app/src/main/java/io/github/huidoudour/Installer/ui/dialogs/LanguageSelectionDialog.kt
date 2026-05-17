package io.github.huidoudour.Installer.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
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
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // 标题
                Text(
                    text = "语言设置",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 语言选项列表
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    languages.forEachIndexed { index, lang ->
                        LanguageOptionCard(
                            title = lang.nativeName,
                            subtitle = getDisplayName(lang.code),
                            isSelected = selectedLanguage == lang.code,
                            showTranslateIcon = lang.showTranslateIcon,
                            onClick = { selectedLanguage = lang.code }
                        )
                        
                        if (index < languages.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                
                // 分隔线
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outline
                )
                
                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // 取消按钮
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("取消")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 确认按钮 - 淡蓝色边框
                    OutlinedButton(
                        onClick = {
                            if (selectedLanguage != currentLanguage) {
                                onConfirm(selectedLanguage)
                            } else {
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            width = 2.dp,
                            color = Color(0xFF6BA3D6)
                        )
                    ) {
                        Text("确定")
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

@Composable
private fun LanguageOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    showTranslateIcon: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isSelected) MaterialTheme.colorScheme.primary 
                else MaterialTheme.colorScheme.outline
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // RadioButton
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 文本
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // 翻译图标（仅简体中文显示）
            if (showTranslateIcon) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_translate),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
