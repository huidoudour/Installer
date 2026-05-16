package io.github.huidoudour.Installer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onThemeClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // 设置标题
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // 应用设置卡片
        AppSettingsCard(onThemeClick = onThemeClick)

        Spacer(modifier = Modifier.height(16.dp))

        // 权限授权设置入口
        PrivilegeSettingsCard()

        Spacer(modifier = Modifier.height(16.dp))

        // 关于应用卡片
        AboutAppCard()
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AppSettingsCard(onThemeClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // 卡片标题
        Text(
            text = stringResource(R.string.app_settings),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp)
        )

        // 分段列表项
        val items = listOf(
            SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_settings),
                title = stringResource(R.string.theme_settings),
                subtitle = null,
                colorPreview = null,
                onClick = onThemeClick
            ),
            SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_palette),
                title = stringResource(R.string.theme_color),
                subtitle = stringResource(R.string.follow_wallpaper),
                colorPreview = MaterialTheme.colorScheme.primary,
                onClick = { }
            ),
            SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_language),
                title = stringResource(R.string.language_settings),
                subtitle = stringResource(R.string.follow_system),
                colorPreview = null,
                onClick = { }
            ),
            SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_notifications_outline),
                title = stringResource(R.string.notification_settings),
                subtitle = null,
                colorPreview = null,
                onClick = { }
            ),
            SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_package),
                title = stringResource(R.string.current_installer_package),
                subtitle = null,
                colorPreview = null,
                onClick = { }
            )
        )

        items.forEachIndexed { index, item ->
            SettingListItem(
                item = item,
                shape = segmentedShape(index, items.size),
                isFirst = index == 0,
                isLast = index == items.lastIndex
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PrivilegeSettingsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Text(
            text = stringResource(R.string.privilege_settings),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp)
        )

        SettingListItem(
            item = SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_lock),
                title = stringResource(R.string.privilege_settings),
                subtitle = stringResource(R.string.checking),
                colorPreview = null,
                onClick = { }
            ),
            shape = singleShape,
            isFirst = true,
            isLast = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun AboutAppCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Text(
            text = stringResource(R.string.about_app),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp)
        )

        // 关于开发者按钮
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = SmallShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = { }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.about_developer),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 版本信息
        SettingListItem(
            item = SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_version),
                title = stringResource(R.string.app_version),
                subtitle = "v1.0.0",
                colorPreview = null,
                onClick = { }
            ),
            shape = singleShape,
            isFirst = true,
            isLast = true,
            showArrow = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private data class SettingItemData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val colorPreview: Color?,
    val onClick: () -> Unit
)

@Composable
private fun SettingListItem(
    item: SettingItemData,
    shape: androidx.compose.ui.graphics.Shape,
    isFirst: Boolean,
    isLast: Boolean,
    showArrow: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceBright
        },
        label = "background"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = shape,
        color = backgroundColor,
        onClick = item.onClick,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 标题和副标题
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp
                    )
                )
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 颜色预览
            if (item.colorPreview != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(item.colorPreview)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 箭头
            if (showArrow) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    // 分段间隙
    if (!isLast) {
        Spacer(modifier = Modifier.height(SegmentedGap))
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceBright
        },
        label = "background"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SmallShape,
        color = backgroundColor,
        onClick = { onCheckedChange(!checked) },
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp
                    )
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
