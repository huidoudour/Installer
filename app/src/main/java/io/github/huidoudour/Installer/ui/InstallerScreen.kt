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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.ui.dialogs.InstallerPackageDialog
import io.github.huidoudour.Installer.ui.theme.AppTheme
import io.github.huidoudour.Installer.ui.theme.CardShape
import io.github.huidoudour.Installer.ui.theme.SegmentedGap
import io.github.huidoudour.Installer.ui.theme.SmallShape
import io.github.huidoudour.Installer.util.PrivilegeHelper

// Brand colors matching source project button tints
private val ButtonPrimaryBlue = Color(0xFF2196F3)
private val ButtonSecondaryGreen = Color(0xFF4CAF50)
private val ButtonInstallTeal = Color(0xFF00BCD4)
private val ButtonAccentOrange = Color(0xFFFF9800)

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
    val enableCustomPackageName by viewModel.enableCustomPackageName.collectAsState()
    val replaceExisting by viewModel.replaceExisting.collectAsState()
    val grantPermissions by viewModel.grantPermissions.collectAsState()

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Card 1: Privilege Status
        PrivilegeStatusCard(
            privilegeMode = privilegeMode,
            status = privilegeStatus,
            onSwitchPrivilege = { viewModel.switchPrivilegeMode() },
            onRequestPermission = { viewModel.requestPrivilegePermission() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: File Selection
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

        // Card 3: Install Options
        InstallOptionsCard(
            enableCustomPackageName = enableCustomPackageName,
            onEnableCustomPackageNameChange = { viewModel.setEnableCustomPackageName(it); viewModel.saveSwitchStates() },
            replaceExisting = replaceExisting,
            onReplaceExistingChange = { viewModel.setReplaceExisting(it); viewModel.saveSwitchStates() },
            grantPermissions = grantPermissions,
            onGrantPermissionsChange = { viewModel.setGrantPermissions(it); viewModel.saveSwitchStates() },
            onInstall = { viewModel.install() },
            onSwitchInstallerPackage = { showInstallerPackageDialog = true },
            isInstallEnabled = isInstallEnabled,
            isInstalling = isInstalling
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showInstallerPackageDialog) {
        InstallerPackageDialog(
            context = context,
            onDismiss = { showInstallerPackageDialog = false },
            onConfirmed = {
                showInstallerPackageDialog = false
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
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp)
    ) {
        // Title row: "Shizuku Status" / "Dhizuku Status" + Switch button
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

        // Status indicator row - colored dot + status text
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

        Spacer(modifier = Modifier.height(16.dp))

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
            Icon(
                imageVector = if (status == PrivilegeHelper.PrivilegeStatus.AUTHORIZED) {
                    Icons.Default.CheckCircle
                } else {
                    ImageVector.vectorResource(R.drawable.ic_lock)
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
        // Title row
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

            // Refresh button - TonalButton style
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

        // File info container
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
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

        Spacer(modifier = Modifier.height(16.dp))

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

        // Switch options - segmented list
        val switchItems = listOf(
            Triple(stringResource(R.string.enable_custom_package_name), null as String?, enableCustomPackageName),
            Triple(stringResource(R.string.replace_existing_app), null as String?, replaceExisting),
            Triple(stringResource(R.string.auto_grant_permissions), null as String?, grantPermissions)
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

        // Install button - matching source project button_install (teal)
        Button(
            onClick = onInstall,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = isInstallEnabled && !isInstalling,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonInstallTeal,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            contentPadding = PaddingValues(16.dp)
        ) {
            if (isInstalling) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
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

// ============ Compose 预览 ============

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun InstallerScreenPreview() {
    AppTheme {
        // 使用模拟数据预览完整的 InstallerScreen UI
        InstallerScreenContent(
            privilegeMode = PrivilegeHelper.PrivilegeMode.SHIZUKU,
            privilegeStatus = PrivilegeHelper.PrivilegeStatus.AUTHORIZED,
            selectedFileName = "example_app.apk",
            fileType = "APK 文件",
            isXapkFile = false,
            isInstallEnabled = true,
            isInstalling = false,
            enableCustomPackageName = true,
            replaceExisting = true,
            grantPermissions = false,
            onSwitchPrivilege = {},
            onRequestPermission = {},
            onRefresh = {},
            onSelectFile = {},
            onEnableCustomPackageNameChange = {},
            onReplaceExistingChange = {},
            onGrantPermissionsChange = {},
            onInstall = {},
            onSwitchInstallerPackage = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "未授权状态")
@Composable
fun InstallerScreenNotAuthorizedPreview() {
    AppTheme {
        InstallerScreenContent(
            privilegeMode = PrivilegeHelper.PrivilegeMode.DHIZUKU,
            privilegeStatus = PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED,
            selectedFileName = null,
            fileType = null,
            isXapkFile = false,
            isInstallEnabled = false,
            isInstalling = false,
            enableCustomPackageName = true,
            replaceExisting = true,
            grantPermissions = false,
            onSwitchPrivilege = {},
            onRequestPermission = {},
            onRefresh = {},
            onSelectFile = {},
            onEnableCustomPackageNameChange = {},
            onReplaceExistingChange = {},
            onGrantPermissionsChange = {},
            onInstall = {},
            onSwitchInstallerPackage = {}
        )
    }
}

/**
 * 可预览的 InstallerScreen 内容组件
 * 将 UI 逻辑与 ViewModel 解耦，支持预览
 */
@Composable
fun InstallerScreenContent(
    privilegeMode: PrivilegeHelper.PrivilegeMode,
    privilegeStatus: PrivilegeHelper.PrivilegeStatus,
    selectedFileName: String?,
    fileType: String?,
    isXapkFile: Boolean,
    isInstallEnabled: Boolean,
    isInstalling: Boolean,
    enableCustomPackageName: Boolean,
    replaceExisting: Boolean,
    grantPermissions: Boolean,
    onSwitchPrivilege: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onSelectFile: () -> Unit,
    onEnableCustomPackageNameChange: (Boolean) -> Unit,
    onReplaceExistingChange: (Boolean) -> Unit,
    onGrantPermissionsChange: (Boolean) -> Unit,
    onInstall: () -> Unit,
    onSwitchInstallerPackage: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showInstallerPackageDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Card 1: Privilege Status
        PrivilegeStatusCard(
            privilegeMode = privilegeMode,
            status = privilegeStatus,
            onSwitchPrivilege = onSwitchPrivilege,
            onRequestPermission = onRequestPermission
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: File Selection
        FileSelectionCard(
            selectedFileName = selectedFileName,
            fileType = fileType,
            onRefresh = onRefresh,
            onSelectFile = onSelectFile
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card 3: Install Options
        InstallOptionsCard(
            enableCustomPackageName = enableCustomPackageName,
            onEnableCustomPackageNameChange = onEnableCustomPackageNameChange,
            replaceExisting = replaceExisting,
            onReplaceExistingChange = onReplaceExistingChange,
            grantPermissions = grantPermissions,
            onGrantPermissionsChange = onGrantPermissionsChange,
            onInstall = onInstall,
            onSwitchInstallerPackage = onSwitchInstallerPackage,
            isInstallEnabled = isInstallEnabled,
            isInstalling = isInstalling
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}
