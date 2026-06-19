package io.github.huidoudour.Installer.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.ui.dialogs.InstallerPackageDialog
import io.github.huidoudour.Installer.ui.dialogs.LanguageSelectionDialog
import io.github.huidoudour.Installer.ui.dialogs.ThemeSelectionDialog
import io.github.huidoudour.Installer.ui.dialogs.getCurrentInstallerPackage
import io.github.huidoudour.Installer.ui.theme.CardShape
import io.github.huidoudour.Installer.ui.theme.SegmentedGap
import io.github.huidoudour.Installer.ui.theme.SmallShape
import io.github.huidoudour.Installer.ui.theme.segmentedShape
import io.github.huidoudour.Installer.ui.theme.singleShape
import io.github.huidoudour.Installer.util.PrivilegeHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToMe: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showInstallerPackageDialog by remember { mutableStateOf(false) }
    var showPrivilegeDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }

    // 注意：语言切换使用 AppCompatDelegate.setApplicationLocales() 自动触发 Activity 重建，无需手动 recreate

    // Notification permission launcher (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, context.getString(R.string.notification_permission_granted), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.notification_permission_denied), Toast.LENGTH_SHORT).show()
        }
        // Open system notification settings regardless
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.cannot_open_notification_settings), Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Settings title
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // App Settings card
        AppSettingsCard(
            onThemeClick = { showThemeDialog = true },
            onLanguageClick = { showLanguageDialog = true },
            onNotificationClick = { showNotificationDialog = true },
            onInstallerPackageClick = { showInstallerPackageDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Privilege settings
        PrivilegeSettingsCard(
            viewModel = viewModel,
            onClick = { showPrivilegeDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // About App card - with Easter egg and Me navigation
        AboutAppCard(
            context = context,
            onNavigateToMe = onNavigateToMe
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Theme selection dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = viewModel.currentTheme.value,
            onDismiss = { showThemeDialog = false },
            onConfirm = { newTheme ->
                viewModel.setTheme(newTheme)
                showThemeDialog = false
                Toast.makeText(context, context.getString(R.string.toast_theme_changed), Toast.LENGTH_SHORT).show()
                // 不需要 recreate — Compose 通过 GlobalThemeStore 实时响应
            }
        )
    }

    // Installer package dialog
    if (showInstallerPackageDialog) {
        InstallerPackageDialog(
            context = context,
            onDismiss = { showInstallerPackageDialog = false },
            onConfirmed = {
                showInstallerPackageDialog = false
                Toast.makeText(context, context.getString(R.string.installer_package_changed), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Language selection dialog
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = viewModel.currentLanguage.value,
            onDismiss = { showLanguageDialog = false },
            onConfirm = { newLanguage ->
                viewModel.setLanguage(newLanguage, context as? Activity)
                showLanguageDialog = false
                Toast.makeText(context, context.getString(R.string.toast_language_changed), Toast.LENGTH_LONG).show()
                // AppCompatDelegate.setApplicationLocales() 会自动触发 Activity 重建（带淡入淡出动画）
            },
            getDisplayName = { langCode ->
                viewModel.getLanguageDisplayName(langCode)
            }
        )
    }

    // Privilege selection dialog
    if (showPrivilegeDialog) {
        PrivilegeSelectionDialog(
            viewModel = viewModel,
            onDismiss = { showPrivilegeDialog = false }
        )
    }

    // Notification permission dialog
    if (showNotificationDialog) {
        NotificationPermissionDialog(
            onDismiss = { showNotificationDialog = false },
            notificationPermissionLauncher = notificationPermissionLauncher
        )
    }
}

@Composable
private fun AppSettingsCard(
    onThemeClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onInstallerPackageClick: () -> Unit = {}
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Text(
            text = stringResource(R.string.app_settings),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        val items = listOf(
            SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_palette),
                title = stringResource(R.string.theme_settings),
                subtitle = null,
                colorPreview = null,
                onClick = onThemeClick
            ),
            SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_language),
                title = stringResource(R.string.language_settings),
                subtitle = null,
                colorPreview = null,
                onClick = onLanguageClick
            ),
            SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_notifications_hollow),
                title = stringResource(R.string.notification_settings),
                subtitle = null,
                colorPreview = null,
                onClick = onNotificationClick
            ),
            SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_package),
                title = stringResource(R.string.current_installer_package),
                subtitle = getCurrentInstallerPackage(context),
                colorPreview = null,
                onClick = onInstallerPackageClick
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

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun PrivilegeSettingsCard(
    viewModel: SettingsViewModel,
    onClick: () -> Unit
) {
    val privilegeStatus by viewModel.privilegeStatus.collectAsState()
    val privilegeMode by viewModel.privilegeMode.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshPrivilegeStatus()
    }

    val statusText = viewModel.getStatusText(privilegeStatus)
    val modeName = PrivilegeHelper.getModeName(privilegeMode)

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
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        SettingListItem(
            item = SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_lock),
                title = stringResource(R.string.privilege_settings),
                subtitle = "$modeName: $statusText",
                colorPreview = null,
                onClick = onClick
            ),
            shape = singleShape,
            isFirst = true,
            isLast = true
        )

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AboutAppCard(
    context: android.content.Context,
    onNavigateToMe: () -> Unit
) {
    val versionName = try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: context.getString(R.string.unknown)
    } catch (e: Exception) {
        context.getString(R.string.unknown)
    }

    // Easter egg: tap version 6 times within 2 seconds
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var showEasterEgg by remember { mutableStateOf(false) }

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
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        // About Developer button - click→Me, long press→DeveloperTest
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = SmallShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = onNavigateToMe
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onNavigateToMe,
                        onLongClick = {
                            Toast.makeText(context, context.getString(R.string.easter_egg_text), Toast.LENGTH_SHORT).show()
                        }
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_github),
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

        // Version info - with Easter egg
        SettingListItem(
            item = SettingItemData(
                icon = ImageVector.vectorResource(R.drawable.ic_version),
                title = stringResource(R.string.app_version),
                subtitle = versionName,
                colorPreview = null,
                onClick = {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 2000) {
                        tapCount++
                        if (tapCount >= 6) {
                            showEasterEgg = true
                            tapCount = 0
                        }
                    } else {
                        tapCount = 1
                    }
                    lastTapTime = now
                }
            ),
            shape = singleShape,
            isFirst = true,
            isLast = true,
            showArrow = true
        )

        Spacer(modifier = Modifier.height(4.dp))
    }

    // Easter egg toast
    if (showEasterEgg) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, context.getString(R.string.easter_egg_text), Toast.LENGTH_SHORT).show()
            showEasterEgg = false
        }
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
            .padding(horizontal = 12.dp)
            .height(56.dp),
        shape = shape,
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = item.onClick,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                )
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.colorPreview != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(item.colorPreview)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

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

    if (!isLast) {
        Spacer(modifier = Modifier.height(SegmentedGap))
    }
}
@Composable
private fun PrivilegeSelectionDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val privilegeStatus by viewModel.privilegeStatus.collectAsState()
    val privilegeMode by viewModel.privilegeMode.collectAsState()

    var selectedMode by remember { mutableStateOf(privilegeMode) }

    LaunchedEffect(Unit) {
        viewModel.refreshPrivilegeStatus()
    }

    var shizukuStatus by remember { mutableStateOf<PrivilegeHelper.PrivilegeStatus?>(null) }
    var dhizukuStatus by remember { mutableStateOf<PrivilegeHelper.PrivilegeStatus?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            shizukuStatus = PrivilegeHelper.checkShizukuStatus()
            dhizukuStatus = PrivilegeHelper.checkDhizukuStatus(context)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.select_privilege_mode),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                // Shizuku card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = PrivilegeHelper.PrivilegeMode.SHIZUKU },
                    shape = SmallShape,
                    color = if (selectedMode == PrivilegeHelper.PrivilegeMode.SHIZUKU)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (selectedMode == PrivilegeHelper.PrivilegeMode.SHIZUKU)
                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_shizuku),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = if (selectedMode == PrivilegeHelper.PrivilegeMode.SHIZUKU)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.shizuku), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = when (shizukuStatus) {
                                    PrivilegeHelper.PrivilegeStatus.AUTHORIZED -> stringResource(R.string.shizuku_connected_and_authorized)
                                    PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED -> stringResource(R.string.shizuku_connected_but_not_authorized)
                                    PrivilegeHelper.PrivilegeStatus.NOT_RUNNING -> stringResource(R.string.shizuku_not_running)
                                    PrivilegeHelper.PrivilegeStatus.VERSION_TOO_LOW -> stringResource(R.string.shizuku_version_too_low)
                                    else -> stringResource(R.string.checking)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (shizukuStatus == PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED) {
                            TextButton(onClick = {
                                PrivilegeHelper.requestShizukuPermission(456)
                            }) {
                                Text(stringResource(R.string.request_authorization))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dhizuku card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = PrivilegeHelper.PrivilegeMode.DHIZUKU },
                    shape = SmallShape,
                    color = if (selectedMode == PrivilegeHelper.PrivilegeMode.DHIZUKU)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (selectedMode == PrivilegeHelper.PrivilegeMode.DHIZUKU)
                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            packageName = "com.rosan.dhizuku",
                            size = 40,
                            tint = if (selectedMode == PrivilegeHelper.PrivilegeMode.DHIZUKU)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.dhizuku), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = when (dhizukuStatus) {
                                    PrivilegeHelper.PrivilegeStatus.AUTHORIZED -> stringResource(R.string.dhizuku_connected_and_authorized)
                                    PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED -> stringResource(R.string.dhizuku_connected_but_not_authorized)
                                    PrivilegeHelper.PrivilegeStatus.NOT_RUNNING -> stringResource(R.string.dhizuku_not_running)
                                    PrivilegeHelper.PrivilegeStatus.VERSION_TOO_LOW -> stringResource(R.string.dhizuku_version_too_low)
                                    else -> stringResource(R.string.checking)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (dhizukuStatus == PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED) {
                            TextButton(onClick = {
                                PrivilegeHelper.requestDhizukuPermission(context)
                            }) {
                                Text(stringResource(R.string.request_authorization))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selectedMode != privilegeMode) {
                    viewModel.switchPrivilegeMode()
                }
                onDismiss()
            }) {
                Text(stringResource(R.string.next_step))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
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
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = SmallShape,
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = { onCheckedChange(!checked) },
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
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

@Composable
private fun NotificationPermissionDialog(
    onDismiss: () -> Unit,
    notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val context = LocalContext.current
    val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(stringResource(R.string.notification_settings), fontWeight = FontWeight.SemiBold)
        },
        text = {
            Text(
                if (isGranted) stringResource(R.string.notification_permission_granted)
                else stringResource(R.string.notification_permission_denied)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (!isGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onDismiss()
            }) {
                Text(if (isGranted) stringResource(R.string.ok) else stringResource(R.string.grant_permission))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 动态加载已安装应用的图标
 * 优先从 PackageManager 获取真实图标，如果获取失败则显示默认图标
 */
@Composable
fun AppIcon(
    packageName: String,
    size: Int,
    tint: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = null,
            modifier = Modifier.size(size.dp),
            colorFilter = if (tint != Color.Unspecified)
                androidx.compose.ui.graphics.ColorFilter.tint(tint)
            else null
        )
    } else {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_warning),
            contentDescription = null,
            modifier = Modifier.size(size.dp),
            tint = tint
        )
    }
}
