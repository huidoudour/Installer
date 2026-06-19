package io.github.huidoudour.Installer.ui.theme

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.huidoudour.Installer.R

/**
 * 颜色选择 Activity
 * 用于从设置界面启动 Compose 颜色选择对话框
 */
class ColorPickerActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_CURRENT_COLOR = "current_color"
        const val EXTRA_USE_DYNAMIC = "use_dynamic"
        const val RESULT_COLOR = "result_color"
        const val RESULT_USE_DYNAMIC = "result_use_dynamic"
        
        @JvmStatic
        fun createIntent(
            activity: Activity,
            currentColor: Int,
            useDynamicColor: Boolean
        ): Intent {
            return Intent(activity, ColorPickerActivity::class.java).apply {
                putExtra(EXTRA_CURRENT_COLOR, currentColor)
                putExtra(EXTRA_USE_DYNAMIC, useDynamicColor)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentColor = intent.getIntExtra(EXTRA_CURRENT_COLOR, 0xFF4A672D.toInt())
        val useDynamicColor = intent.getBooleanExtra(EXTRA_USE_DYNAMIC, true)
        
        // 基础主题色
        val seedColor = Color(currentColor)
        
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                var selectedColor by remember { mutableStateOf(seedColor) }
                var isDynamic by remember { mutableStateOf(useDynamicColor) }
                
                ColorPickerDialog(
                    currentColor = selectedColor,
                    useDynamicColor = isDynamic,
                    onDismiss = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onColorSelected = { color ->
                        val resultIntent = Intent().apply {
                            putExtra(RESULT_COLOR, color.toArgb())
                            putExtra(RESULT_USE_DYNAMIC, isDynamic)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                    onDynamicColorChanged = { dynamic ->
                        isDynamic = dynamic
                    }
                )
            }
        }
    }
}

/**
 * 预设颜色列表
 */
val presetColors = listOf(
    0xFF4A672D.toInt() to "Default Green",
    0xFFBA68C8.toInt() to "Pink",
    0xFFEF5350.toInt() to "Red",
    0xFFFF7043.toInt() to "Orange",
    0xFFFFCA28.toInt() to "Amber",
    0xFFFFEE58.toInt() to "Yellow",
    0xFF9CCC65.toInt() to "Lime",
    0xFF66BB6A.toInt() to "Green",
    0xFF26C6DA.toInt() to "Cyan",
    0xFF26A69A.toInt() to "Teal",
    0xFF29B6F6.toInt() to "Light Blue",
    0xFF42A5F5.toInt() to "Blue",
    0xFF5C6BC0.toInt() to "Indigo",
    0xFFAB47BC.toInt() to "Purple",
    0xFF7E57C2.toInt() to "Deep Purple",
    0xFF78909C.toInt() to "Blue Grey",
    0xFF8D6E63.toInt() to "Brown",
    0xFF9E9E9E.toInt() to "Grey"
)

/**
 * 生成色调调色板 (HSV算法)
 */
fun generateTonalPalette(baseColor: Color): List<Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(baseColor.toArgb(), hsv)
    
    val lightnesses = listOf(0.95f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f, 0.2f, 0.1f)
    return lightnesses.map { l ->
        Color.hsv(hsv[0], hsv[1].coerceAtMost(0.8f), l)
    }
}

/**
 * 颜色选择对话框
 */
@Composable
fun ColorPickerDialog(
    currentColor: Color,
    useDynamicColor: Boolean,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit
) {
    var selectedColor by remember { mutableStateOf(currentColor) }
    var isDynamic by remember { mutableStateOf(useDynamicColor) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题
                Text(
                    text = stringResource(R.string.theme_color_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 颜色预览
                ColorSchemePreview(
                    seedColor = selectedColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 跟随壁纸开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.follow_wallpaper_option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDynamic) stringResource(R.string.dynamic_color_enabled) else stringResource(R.string.using_custom_color),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDynamic,
                        onCheckedChange = { 
                            isDynamic = it
                            onDynamicColorChanged(it)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 预设颜色网格
                if (!isDynamic) {
                    Text(
                        text = stringResource(R.string.preset_colors_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(120.dp)
                    ) {
                        items(presetColors) { (colorValue, colorName) ->
                            ColorItem(
                                color = Color(colorValue),
                                isSelected = selectedColor.toArgb() == colorValue,
                                colorName = colorName,
                                onClick = {
                                    selectedColor = Color(colorValue)
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 按钮 - 使用 MD3 风格的包裹容器
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.height(IntrinsicSize.Max)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = { onColorSelected(selectedColor) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(stringResource(R.string.apply_button))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 颜色项
 */
@Composable
fun ColorItem(
    color: Color,
    isSelected: Boolean,
    colorName: String,
    onClick: () -> Unit
) {
    val animatedColor by animateColorAsState(targetValue = color, label = "color")
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(animatedColor)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                painter = painterResource(id = android.R.drawable.checkbox_on_background),
                contentDescription = stringResource(R.string.selected_content_description),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 颜色方案预览
 */
@Composable
fun ColorSchemePreview(
    seedColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = remember(seedColor) { generateTonalPalette(seedColor) }
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            palette.forEach { color ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(color)
                )
            }
        }
    }
}
