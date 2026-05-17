package io.github.huidoudour.Installer.ui.dialogs

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // 标题
                Text(
                    text = "主题设置",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 主题选项列表
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // 跟随系统
                    ThemeOptionCard(
                        title = "跟随系统",
                        subtitle = "Follow System Theme",
                        isSelected = selectedTheme == ThemeManager.THEME_FOLLOW_SYSTEM,
                        onClick = { selectedTheme = ThemeManager.THEME_FOLLOW_SYSTEM }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 浅色主题
                    ThemeOptionCard(
                        title = "浅色主题",
                        subtitle = "Light Mode",
                        isSelected = selectedTheme == ThemeManager.THEME_LIGHT,
                        onClick = { selectedTheme = ThemeManager.THEME_LIGHT }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 深色主题
                    ThemeOptionCard(
                        title = "深色主题",
                        subtitle = "Dark Mode",
                        isSelected = selectedTheme == ThemeManager.THEME_DARK,
                        onClick = { selectedTheme = ThemeManager.THEME_DARK }
                    )
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
                            if (selectedTheme != currentTheme) {
                                onConfirm(selectedTheme)
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

@Composable
private fun ThemeOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
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
        }
    }
}
