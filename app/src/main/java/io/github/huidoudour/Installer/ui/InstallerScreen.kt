package io.github.huidoudour.Installer.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.ui.dialogs.InstallerPackageDialog
import io.github.huidoudour.Installer.ui.theme.SmallShape
import io.github.huidoudour.Installer.util.PrivilegeHelper

// Brand colors matching source project button tints
private val ButtonPrimaryBlue = Color(0xFF2196F3)
private val ButtonSecondaryGreen = Color(0xFF4CAF50)
private val ButtonInstallTeal = Color(0xFF00BCD4)
private val ButtonAccentOrange = Color(0xFFFF9800)

/**
 * InstallerScreen - 主安装界面
 * 包含三张卡片：权限状态、文件选择、安装选项
 */
@Composable
fun InstallerScreen(
    onThemeClick: () -> Unit = {},
    viewModel: InstallerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showInstallerPackageDialog by remember { mutableStateOf(false) }

    val privilegeStatus: PrivilegeHelper.PrivilegeStatus by viewModel.privilegeStatus.collectAsState()
    val privilegeMode by viewModel.privilegeMode.collectAsState()
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val fileType by viewModel.fileType.collectAsState()
    val isXapkFile by viewModel.isXapkFile.collectAsState()
    val isInstallEnabled by viewModel.isInstallEnabled.collectAsState()
    val isInstalling by viewModel.isInstalling.collectAsState()
    val installCompleted by viewModel.installCompleted.collectAsState()
    val enableCustomPackageName by viewModel.enableCustomPackageName.collectAsState()
    val selectedInstallerPackage by viewModel.selectedInstallerPackage.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.onFileSelected(uri)
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            filePickerLauncher.launch("*/*")
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            filePickerLauncher.launch("*/*")
        }
    }

    // MD3 背景包裹，恢复原有样式
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Card 1: Privilege Status
            PrivilegeStatusCard(
                privilegeMode = privilegeMode,
                status = privilegeStatus,
                onSwitchPrivilege = { viewModel.switchPrivilegeMode() },
            onRequestPermission = { viewModel.requestPrivilegePermission() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: File Selection
            FileSelectionCard(
                selectedFileName = selectedFileName,
                fileType = fileType,
                onSelectFile = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (Environment.isExternalStorageManager()) {
                            filePickerLauncher.launch("*/*")
                        } else {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${context.packageName}")
                            manageStorageLauncher.launch(intent)
                        }
                    } else {
                        storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                },
                onRefreshFileInfo = { viewModel.refreshFileInfo() },
                onInstall = { viewModel.install() },
                isInstallEnabled = isInstallEnabled,
                isInstalling = isInstalling,
                installCompleted = installCompleted
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Card 3: Install Options
            InstallOptionsCard(
                enableCustomPackageName = enableCustomPackageName,
                onEnableCustomPackageNameChange = { enabled ->
                    viewModel.setEnableCustomPackageName(enabled)
                    val pkg = if (enabled) selectedInstallerPackage else "com.android.shell"
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.custom_package_name_setting_changed, pkg),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onSwitchInstallerPackage = { showInstallerPackageDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showInstallerPackageDialog) {
        InstallerPackageDialog(
            context = context,
            onDismiss = { showInstallerPackageDialog = false },
            onConfirmed = {
                showInstallerPackageDialog = false
                viewModel.setSelectedInstallerPackage(it)
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.installer_package_changed),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

@Composable
fun PrivilegeStatusCard(
    privilegeMode: PrivilegeHelper.PrivilegeMode,
    status: PrivilegeHelper.PrivilegeStatus,
    onSwitchPrivilege: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current

    val statusColor = when (status) {
        PrivilegeHelper.PrivilegeStatus.AUTHORIZED -> Color(0xFF388E3C)      // success green
        PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED -> Color(0xFFFF9800)   // warning orange
        else -> Color(0xFFD32F2F)                                             // error red
    }

    val statusBackgroundColor by animateColorAsState(
        targetValue = statusColor.copy(alpha = 0.12f),
        label = "statusBg"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Title row: "%s Status" / "%s Status" + Switch button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.privilege_status_title, PrivilegeHelper.getModeName(privilegeMode)),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            // Switch button - TonalButton style matching original
            Surface(
                shape = SmallShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onSwitchPrivilege
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.switch_privilege),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status indicator row - colored dot + status text
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SmallShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = statusBackgroundColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = when (status) {
                        PrivilegeHelper.PrivilegeStatus.NOT_INSTALLED -> context.getString(R.string.privilege_not_installed_status, PrivilegeHelper.getModeName(privilegeMode))
                        PrivilegeHelper.PrivilegeStatus.NOT_RUNNING -> context.getString(R.string.privilege_not_running_status, PrivilegeHelper.getModeName(privilegeMode))
                        PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED -> context.getString(R.string.privilege_not_authorized_status)
                        PrivilegeHelper.PrivilegeStatus.AUTHORIZED -> context.getString(R.string.privilege_authorized_status)
                        PrivilegeHelper.PrivilegeStatus.VERSION_TOO_LOW -> context.getString(R.string.privilege_version_low_status)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Grant/Download/Open/Authorized button - matching source project button_secondary (green)
        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = status != PrivilegeHelper.PrivilegeStatus.AUTHORIZED,
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonSecondaryGreen,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = when (status) {
                    PrivilegeHelper.PrivilegeStatus.NOT_INSTALLED -> context.getString(R.string.privilege_download_button, PrivilegeHelper.getModeName(privilegeMode))
                    PrivilegeHelper.PrivilegeStatus.NOT_RUNNING -> context.getString(R.string.privilege_open_button, PrivilegeHelper.getModeName(privilegeMode))
                    PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED -> context.getString(R.string.privilege_request_auth_button)
                    PrivilegeHelper.PrivilegeStatus.AUTHORIZED -> context.getString(R.string.privilege_authorized_button)
                    PrivilegeHelper.PrivilegeStatus.VERSION_TOO_LOW -> context.getString(R.string.privilege_update_button, PrivilegeHelper.getModeName(privilegeMode))
                },
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun FileSelectionCard(
    selectedFileName: String?,
    fileType: String?,
    onSelectFile: () -> Unit,
    onRefreshFileInfo: () -> Unit,
    onInstall: () -> Unit,
    isInstallEnabled: Boolean,
    isInstalling: Boolean,
    installCompleted: Boolean = false
) {
    val hasFile = selectedFileName != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Title row with refresh button (matching original btnRefreshFile)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.select_package),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )

                // Refresh button - TonalButton style matching original
                Surface(
                    shape = SmallShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onRefreshFileInfo,
                    enabled = hasFile
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.refresh),
                            fontSize = 12.sp,
                            color = if (hasFile) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

        // File info container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = if (hasFile) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasFile) Icons.Default.Star else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (hasFile) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedFileName ?: stringResource(R.string.no_file_selected),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = if (hasFile) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (fileType != null && hasFile) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = fileType,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Select file button - matching source project button_primary (blue)
        Button(
            onClick = onSelectFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonPrimaryBlue,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = stringResource(R.string.select_package_file),
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Install button - green when ready, blue after complete
        val installButtonColor = when {
            installCompleted -> ButtonPrimaryBlue
            isInstallEnabled -> ButtonSecondaryGreen
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        Button(
            onClick = onInstall,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = isInstallEnabled && !isInstalling,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = installButtonColor,
                contentColor = Color.White,
                disabledContainerColor = if (installCompleted) ButtonPrimaryBlue.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = if (installCompleted) Color.White.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            contentPadding = PaddingValues(16.dp)
        ) {
            if (isInstalling) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (isInstalling) stringResource(R.string.installing_progress)
                else stringResource(R.string.install_apk),
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun InstallOptionsCard(
    enableCustomPackageName: Boolean,
    onEnableCustomPackageNameChange: (Boolean) -> Unit,
    onSwitchInstallerPackage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.install_options),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            // Switch installer button - TonalButton style
            Surface(
                shape = SmallShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onSwitchInstallerPackage
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.switch_installer_package),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Switch options - bordered container with horizontal dividers
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                SwitchRow(
                    title = stringResource(R.string.enable_custom_package_name),
                    checked = enableCustomPackageName,
                    onCheckedChange = onEnableCustomPackageNameChange
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline
                )
                // 替换现有应用 — 锁定开启
                SwitchRow(
                    title = stringResource(R.string.replace_existing_app),
                    checked = true,
                    onCheckedChange = {},
                    enabled = false
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline
                )
                // 自动授予权限 — 锁定关闭
                SwitchRow(
                    title = stringResource(R.string.auto_grant_permissions),
                    checked = false,
                    onCheckedChange = {},
                    enabled = false
                )
            }
        }
    }
}

/**
 * SwitchRow - 单个开关行，用于 InstallOptionsCard 的分段容器内
 */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (!enabled) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        } else if (isPressed) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "switchRowBg"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(0.dp),
        color = backgroundColor,
        onClick = { if (enabled) onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = { if (enabled) onCheckedChange(it) },
                enabled = enabled
            )
        }
    }
}
