package dev.huidoudour.installer.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.huidoudour.installer.R
import dev.huidoudour.installer.auth.PrivilegeHelper
import dev.huidoudour.installer.install.InstallCacheCleaner
import dev.huidoudour.installer.ui.theme.CardShape
import dev.huidoudour.installer.ui.theme.SmallShape
import dev.huidoudour.installer.ui.theme.segmentedShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val DHIZUKU_FALLBACK_PACKAGE = "com.rosan.dhizuku"

/**
 * 「安装与权限设置」页面。
 *
 * 原设置页的「安装参数」与「权限授权设置」两个入口合并到此页面，
 * 并在末尾提供安装缓存占用查看与手动清理。
 */
@Composable
fun InstallSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val replaceExisting by viewModel.replaceExisting.collectAsState()
    val grantPermissions by viewModel.grantPermissions.collectAsState()
    val useShizuku by viewModel.useShizuku.collectAsState()
    val useDhizuku by viewModel.useDhizuku.collectAsState()
    // null 表示尚未统计完成
    var cacheSize by remember { mutableStateOf<Long?>(null) }

    fun refreshCacheSize() {
        scope.launch {
            cacheSize = withContext(Dispatchers.IO) { InstallCacheCleaner.currentSize(context) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPrivilegeStatus()
        refreshCacheSize()
    }

    // 从后台返回（例如去授权后回来）时刷新权限状态与缓存占用
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPrivilegeStatus()
                refreshCacheSize()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // 顶部返回栏
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.install_privilege_settings),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }

        InstallSettingsCard(title = stringResource(R.string.install_parameters)) {
            SettingsSwitchItem(
                title = stringResource(R.string.replace_existing_app),
                checked = replaceExisting,
                onCheckedChange = viewModel::setReplaceExisting,
                shape = segmentedShape(0, 2),
                isLast = false
            )
            SettingsSwitchItem(
                title = stringResource(R.string.auto_grant_permissions),
                checked = grantPermissions,
                onCheckedChange = viewModel::setGrantPermissions,
                shape = segmentedShape(1, 2),
                isLast = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        InstallSettingsCard(
            title = stringResource(R.string.privilege_settings),
            subtitle = stringResource(R.string.privilege_settings_hint)
        ) {
            PrivilegeModeCards(
                viewModel = viewModel,
                shizukuEnabled = useShizuku,
                dhizukuEnabled = useDhizuku
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        InstallSettingsCard(title = stringResource(R.string.install_cache)) {
            CacheUsageRow(
                sizeText = cacheSize?.let { InstallCacheCleaner.formatSize(context, it) },
                onClear = {
                    scope.launch {
                        val freed = withContext(Dispatchers.IO) {
                            InstallCacheCleaner.deleteStaleInstallFiles(context)
                        }
                        cacheSize = withContext(Dispatchers.IO) {
                            InstallCacheCleaner.currentSize(context)
                        }
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.install_cache_cleared,
                                InstallCacheCleaner.formatSize(context, freed)
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** 与设置页一致的卡片容器：标题 + 可选说明 + 内容。 */
@Composable
private fun InstallSettingsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
        }

        content()

        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * 授权器选择卡片：点击即持久化所选授权器，不再需要额外的确认步骤。
 *
 * @param shizukuEnabled Shizuku 开关关闭时置灰并禁用选择（此模式下安装不使用 Shizuku）。
 * @param dhizukuEnabled Dhizuku 开关关闭时置灰并禁用选择（此模式下安装不使用 Dhizuku）。
 */
@Composable
private fun PrivilegeModeCards(
    viewModel: SettingsViewModel,
    shizukuEnabled: Boolean,
    dhizukuEnabled: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privilegeMode by viewModel.privilegeMode.collectAsState()
    var shizukuStatus by remember { mutableStateOf<PrivilegeHelper.PrivilegeStatus?>(null) }
    var dhizukuStatus by remember { mutableStateOf<PrivilegeHelper.PrivilegeStatus?>(null) }

    // 对应开关关闭时跳过检测，不进行任何 Shizuku/Dhizuku 交互
    LaunchedEffect(shizukuEnabled) {
        if (shizukuEnabled) {
            withContext(Dispatchers.IO) {
                shizukuStatus = PrivilegeHelper.checkShizukuStatus(context)
            }
        }
    }

    LaunchedEffect(dhizukuEnabled) {
        if (dhizukuEnabled) {
            withContext(Dispatchers.IO) {
                dhizukuStatus = PrivilegeHelper.checkDhizukuStatus(context)
            }
        }
    }

    val applyMode: (PrivilegeHelper.PrivilegeMode) -> Unit = { mode ->
        if (mode != privilegeMode) {
            PrivilegeHelper.saveCurrentMode(context, mode)
            viewModel.refreshPrivilegeStatus()
        }
    }

    PrivilegeModeCard(
        selected = privilegeMode == PrivilegeHelper.PrivilegeMode.SHIZUKU,
        iconPackage = SHIZUKU_PACKAGE,
        title = stringResource(R.string.shizuku),
        statusText = if (shizukuEnabled) {
            privilegeStatusText(shizukuStatus, PrivilegeHelper.PrivilegeMode.SHIZUKU)
        } else {
            stringResource(R.string.disabled)
        },
        enabled = shizukuEnabled,
        onSelect = { applyMode(PrivilegeHelper.PrivilegeMode.SHIZUKU) },
        trailing = {
            if (shizukuEnabled && shizukuStatus == PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED) {
                TextButton(onClick = { PrivilegeHelper.requestShizukuPermission(456) }) {
                    Text(stringResource(R.string.request_authorization))
                }
            }
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    PrivilegeModeCard(
        selected = privilegeMode == PrivilegeHelper.PrivilegeMode.DHIZUKU,
        iconPackage = PrivilegeHelper.getInstalledDhizukuPackage(context) ?: DHIZUKU_FALLBACK_PACKAGE,
        title = stringResource(R.string.dhizuku),
        statusText = if (dhizukuEnabled) {
            privilegeStatusText(dhizukuStatus, PrivilegeHelper.PrivilegeMode.DHIZUKU)
        } else {
            stringResource(R.string.disabled)
        },
        enabled = dhizukuEnabled,
        onSelect = { applyMode(PrivilegeHelper.PrivilegeMode.DHIZUKU) },
        trailing = {
            if (dhizukuEnabled && dhizukuStatus == PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED) {
                TextButton(onClick = {
                    PrivilegeHelper.requestDhizukuPermission(context) { _ ->
                        scope.launch(Dispatchers.IO) {
                            dhizukuStatus = PrivilegeHelper.checkDhizukuStatus(context)
                        }
                    }
                }) {
                    Text(stringResource(R.string.request_authorization))
                }
            }
        }
    )
}

@Composable
private fun PrivilegeModeCard(
    selected: Boolean,
    iconPackage: String,
    title: String,
    statusText: String,
    enabled: Boolean,
    onSelect: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .clickable(enabled = enabled) { onSelect() },
        shape = SmallShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceBright
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = iconPackage, size = 40)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing()
        }
    }
}

/** 安装缓存占用 + 一键清理。 */
@Composable
private fun CacheUsageRow(
    sizeText: String?,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = SmallShape,
        color = MaterialTheme.colorScheme.surfaceBright,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sizeText?.let { stringResource(R.string.install_cache_usage, it) }
                    ?: stringResource(R.string.checking),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClear, enabled = sizeText != null) {
                Text(stringResource(R.string.clear))
            }
        }
    }
}

@Composable
private fun privilegeStatusText(
    status: PrivilegeHelper.PrivilegeStatus?,
    mode: PrivilegeHelper.PrivilegeMode
): String = when (status) {
    PrivilegeHelper.PrivilegeStatus.AUTHORIZED -> stringResource(
        if (mode == PrivilegeHelper.PrivilegeMode.SHIZUKU) {
            R.string.shizuku_connected_and_authorized
        } else {
            R.string.dhizuku_connected_and_authorized
        }
    )
    PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED -> stringResource(
        if (mode == PrivilegeHelper.PrivilegeMode.SHIZUKU) {
            R.string.shizuku_connected_but_not_authorized
        } else {
            R.string.dhizuku_connected_but_not_authorized
        }
    )
    PrivilegeHelper.PrivilegeStatus.NOT_RUNNING -> stringResource(
        if (mode == PrivilegeHelper.PrivilegeMode.SHIZUKU) {
            R.string.shizuku_not_running
        } else {
            R.string.dhizuku_not_running
        }
    )
    PrivilegeHelper.PrivilegeStatus.VERSION_TOO_LOW -> stringResource(
        if (mode == PrivilegeHelper.PrivilegeMode.SHIZUKU) {
            R.string.shizuku_version_too_low
        } else {
            R.string.dhizuku_version_too_low
        }
    )
    else -> stringResource(R.string.checking)
}
