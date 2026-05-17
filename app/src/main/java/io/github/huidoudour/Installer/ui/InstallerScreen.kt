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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.ui.theme.*
import io.github.huidoudour.Installer.util.PrivilegeHelper
import io.github.huidoudour.Installer.util.XapkInstaller

@Composable
fun InstallerScreen(
    onThemeClick: () -> Unit = {},
    viewModel: InstallerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 状态
    val privilegeStatus: PrivilegeHelper.PrivilegeStatus by viewModel.privilegeStatus.collectAsState()
    val privilegeMode by viewModel.privilegeMode.collectAsState()
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val fileType by viewModel.fileType.collectAsState()
    val isXapkFile by viewModel.isXapkFile.collectAsState()
    val isInstallEnabled by viewModel.isInstallEnabled.collectAsState()
    val isInstalling by viewModel.isInstalling.collectAsState()
    val enableCustomPackageName by viewModel.enableCustomPackageName.collectAsState()
    val replaceExisting by viewModel.replaceExisting.collectAsState()
    val grantPermissions by viewModel.grantPermissions.collectAsState()

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.onFileSelected(uri)
    }

    // 存储权限Launcher
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            filePickerLauncher.launch("*/*")
        }
    }

    // MANAGE_EXTERNAL_STORAGE Launcher
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            filePickerLauncher.launch("*/*")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // 授权器状态卡片
        PrivilegeStatusCard(
            privilegeMode = privilegeMode,
            status = privilegeStatus,
            onSwitchPrivilege = { viewModel.switchPrivilegeMode() },
            onRequestPermission = { viewModel.requestPrivilegePermission() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 文件选择卡片
        FileSelectionCard(
            selectedFileName = selectedFileName,
            fileType = fileType,
            onRefresh = { viewModel.refreshFileInfo() },
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
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 安装操作卡片
        InstallOptionsCard(
            enableCustomPackageName = enableCustomPackageName,
            onEnableCustomPackageNameChange = {
                viewModel.setEnableCustomPackageName(it)
                viewModel.saveSwitchStates()
            },
            replaceExisting = replaceExisting,
            onReplaceExistingChange = {
                viewModel.setReplaceExisting(it)
                viewModel.saveSwitchStates()
            },
            grantPermissions = grantPermissions,
            onGrantPermissionsChange = {
                viewModel.setGrantPermissions(it)
                viewModel.saveSwitchStates()
            },
            onInstall = { viewModel.install() },
            onSwitchInstallerPackage = { },
            isInstallEnabled = isInstallEnabled,
            isInstalling = isInstalling
        )
        
        Spacer(modifier = Modifier.height(32.dp))
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
        PrivilegeHelper.PrivilegeStatus.AUTHORIZED -> MaterialTheme.colorScheme.primary
        PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    
    val statusBackgroundColor by animateColorAsState(
        targetValue = statusColor.copy(alpha = 0.15f),
        label = "statusBg"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp)
    ) {
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.privilege_status_title, PrivilegeHelper.getModeName(privilegeMode)),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary
            )

            // 切换按钮
            Surface(
                shape = SmallShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onSwitchPrivilege
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.switch_privilege),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 状态指示器
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SmallShape,
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
                        .size(10.dp)
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
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 请求权限按钮 - 使用包裹容器，使用绿色（button_secondary）
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                enabled = status != PrivilegeHelper.PrivilegeStatus.AUTHORIZED,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)  // button_secondary 绿色
                ),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(
                    imageVector = when (status) {
                        PrivilegeHelper.PrivilegeStatus.AUTHORIZED -> Icons.Default.CheckCircle
                        else -> androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_lock)
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
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
}

@Composable
fun FileSelectionCard(
    selectedFileName: String?,
    fileType: String?,
    onRefresh: () -> Unit,
    onSelectFile: () -> Unit
) {
    val hasFile = selectedFileName != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp)
    ) {
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.select_package),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary
            )

            // 刷新按钮
            Surface(
                shape = SmallShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onRefresh,
                enabled = hasFile
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (hasFile) MaterialTheme.colorScheme.onSecondaryContainer 
                               else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.refresh),
                        fontSize = 12.sp,
                        color = if (hasFile) MaterialTheme.colorScheme.onSecondaryContainer 
                                else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 文件信息容器
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SmallShape,
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
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        color = if (hasFile) MaterialTheme.colorScheme.onSurface 
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (fileType != null && hasFile) {
                        Text(
                            text = fileType,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 选择文件按钮 - 使用包裹容器，使用蓝色（button_primary）
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Button(
                onClick = onSelectFile,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)  // button_primary 蓝色
                ),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.select_package_file),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun InstallOptionsCard(
    enableCustomPackageName: Boolean,
    onEnableCustomPackageNameChange: (Boolean) -> Unit,
    replaceExisting: Boolean,
    onReplaceExistingChange: (Boolean) -> Unit,
    grantPermissions: Boolean,
    onGrantPermissionsChange: (Boolean) -> Unit,
    onInstall: () -> Unit,
    onSwitchInstallerPackage: () -> Unit,
    isInstallEnabled: Boolean,
    isInstalling: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp)
    ) {
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.install_options),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary
            )

            // 切换安装器按钮
            Surface(
                shape = SmallShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onSwitchInstallerPackage
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.switch_installer_package),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 开关选项 - 使用分段列表
        val switchItems = listOf(
            Triple(
                stringResource(R.string.enable_custom_package_name),
                null as String?,
                enableCustomPackageName
            ),
            Triple(
                stringResource(R.string.replace_existing_app),
                null as String?,
                replaceExisting
            ),
            Triple(
                stringResource(R.string.auto_grant_permissions),
                null as String?,
                grantPermissions
            )
        )

        switchItems.forEachIndexed { index, (title, subtitle, checked) ->
            val isChecked = when (title) {
                stringResource(R.string.enable_custom_package_name) -> enableCustomPackageName
                stringResource(R.string.replace_existing_app) -> replaceExisting
                else -> grantPermissions
            }
            val onCheckedChange: (Boolean) -> Unit = when (title) {
                stringResource(R.string.enable_custom_package_name) -> onEnableCustomPackageNameChange
                stringResource(R.string.replace_existing_app) -> onReplaceExistingChange
                else -> onGrantPermissionsChange
            }

            SettingsSwitchItem(
                title = title,
                subtitle = subtitle,
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )

            if (index < switchItems.lastIndex) {
                Spacer(modifier = Modifier.height(SegmentedGap))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 安装按钮 - 使用包裹容器，使用绿色（button_install）
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Button(
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
                enabled = isInstallEnabled && !isInstalling,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF009688),  // button_install 绿色
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isInstalling) stringResource(R.string.installing_progress) 
                           else stringResource(R.string.install_apk),
                    fontSize = 15.sp
                )
            }
        }
    }
}
