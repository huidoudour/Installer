package dev.huidoudour.installer.ui.lab

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.huidoudour.installer.R
import dev.huidoudour.installer.ui.components.PredictiveBackPage
import dev.huidoudour.installer.auth.Authorizer
import dev.huidoudour.installer.auth.PrivilegeHelper
import dev.huidoudour.installer.auth.SmartAuthorizerCandidate
import dev.huidoudour.installer.ui.settings.SettingItemData
import dev.huidoudour.installer.ui.settings.SettingListItem
import dev.huidoudour.installer.ui.settings.SettingsSwitchItem
import dev.huidoudour.installer.ui.settings.SettingsViewModel
import dev.huidoudour.installer.ui.theme.CardShape
import dev.huidoudour.installer.ui.theme.SegmentedGap
import dev.huidoudour.installer.ui.theme.SmallShape
import dev.huidoudour.installer.ui.theme.segmentedShape
import dev.huidoudour.installer.ui.theme.singleShape
import dev.huidoudour.installer.util.UpdateChecker
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 高级设置页面：智能授权（特权安装开关、回退顺序等）。
 */
@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    enterProgress: Float = 1f,
    onScrimChange: (Float) -> Unit = {},
    viewModel: LabViewModel = viewModel(),
) {
    val tryMultipleAuthorizers by viewModel.tryMultipleAuthorizers.collectAsState()
    val authorizerByInstallState by viewModel.authorizerByInstallState.collectAsState()
    val useShizuku by viewModel.useShizuku.collectAsState()
    val useDhizuku by viewModel.useDhizuku.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val availability by viewModel.availability.collectAsState()

    var showFallbackDialog by remember { mutableStateOf(false) }

    // 从后台返回时刷新授权器可用性
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAvailability()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LabScaffold(
        title = stringResource(R.string.advanced_settings),
        showTip = false,
        onBack = onBack,
        enterProgress = enterProgress,
        onScrimChange = onScrimChange,
    ) {
        SectionContainer(title = stringResource(R.string.lab_authorizer_section)) {
            SettingsSwitchItem(
                title = stringResource(R.string.lab_use_shizuku_install),
                subtitle = stringResource(R.string.lab_use_shizuku_install_desc),
                checked = useShizuku,
                onCheckedChange = { viewModel.setUseShizuku(it) },
                shape = segmentedShape(0, 5),
                isLast = false
            )

            SettingsSwitchItem(
                title = stringResource(R.string.lab_use_dhizuku_install),
                subtitle = stringResource(R.string.lab_use_dhizuku_install_desc),
                checked = useDhizuku,
                onCheckedChange = { viewModel.setUseDhizuku(it) },
                shape = segmentedShape(1, 5),
                isLast = false
            )

            SettingsSwitchItem(
                title = stringResource(R.string.lab_try_multiple_authorizers),
                subtitle = stringResource(R.string.lab_try_multiple_authorizers_desc),
                checked = tryMultipleAuthorizers,
                onCheckedChange = { viewModel.setTryMultipleAuthorizers(it) },
                shape = segmentedShape(2, 5),
                isLast = false
            )

            SettingsSwitchItem(
                title = stringResource(R.string.lab_authorizer_by_install_state),
                subtitle = stringResource(R.string.lab_authorizer_by_install_state_desc),
                checked = authorizerByInstallState,
                onCheckedChange = { viewModel.setAuthorizerByInstallState(it) },
                shape = segmentedShape(3, 5),
                isLast = false
            )

            SettingListItem(
                item = SettingItemData(
                    icon = ImageVector.vectorResource(R.drawable.ic_list),
                    title = stringResource(R.string.lab_smart_authorizer_fallback_list),
                    subtitle = viewModel.enabledOrderLabel(),
                    colorPreview = null,
                    onClick = { showFallbackDialog = true }
                ),
                shape = segmentedShape(4, 5),
                isLast = true,
                showArrow = true
            )
        }
    }

    if (showFallbackDialog) {
        val context = LocalContext.current
        FallbackListDialog(
            candidates = candidates,
            availability = availability,
            onDismiss = { showFallbackDialog = false },
            onConfirm = { newList ->
                if (viewModel.updateCandidates(newList)) {
                    showFallbackDialog = false
                    true
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.lab_authorizer_must_choose_one),
                        Toast.LENGTH_SHORT
                    ).show()
                    false
                }
            }
        )
    }
}

/**
 * 实验功能页面：签名校验、终端，以及更新检测（手动检查 / GitHub 令牌）。
 */
@Composable
fun ExperimentalScreen(
    onBack: () -> Unit,
    enterProgress: Float = 1f,
    onScrimChange: (Float) -> Unit = {},
    viewModel: LabViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val useShizukuTerminal by viewModel.useShizukuTerminal.collectAsState()
    val checkSignature by viewModel.checkSignature.collectAsState()
    val showSignatureDetails by viewModel.showSignatureDetails.collectAsState()

    // 用局部 val 承接，便于智能转换为非空
    val updateInfo = settingsViewModel.updateInfo.collectAsState().value
    val githubPat by settingsViewModel.githubPat.collectAsState()
    var showPatDialog by remember { mutableStateOf(false) }

    // 进入实验功能页触发一次检测（ViewModel 内部已做 24h 节流）
    LaunchedEffect(Unit) {
        settingsViewModel.checkUpdate()
    }

    LabScaffold(
        title = stringResource(R.string.experimental_features),
        showTip = true,
        onBack = onBack,
        enterProgress = enterProgress,
        onScrimChange = onScrimChange,
    ) {
        // 分区：签名校验
        SectionContainer(title = stringResource(R.string.lab_signature_section)) {
            SettingsSwitchItem(
                title = stringResource(R.string.lab_check_signature),
                subtitle = stringResource(R.string.lab_check_signature_desc),
                checked = checkSignature,
                onCheckedChange = { viewModel.setCheckSignature(it) },
                shape = segmentedShape(0, 2),
                isLast = false
            )

            SettingsSwitchItem(
                title = stringResource(R.string.lab_show_signature_details),
                subtitle = null,
                checked = showSignatureDetails,
                onCheckedChange = { viewModel.setShowSignatureDetails(it) },
                shape = segmentedShape(1, 2),
                isLast = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 分区：终端
        SectionContainer(title = stringResource(R.string.lab_terminal_section)) {
            SettingsSwitchItem(
                title = stringResource(R.string.lab_terminal_use_shizuku),
                subtitle = stringResource(R.string.lab_terminal_use_shizuku_desc),
                checked = useShizukuTerminal,
                onCheckedChange = { viewModel.setUseShizukuTerminal(it) },
                shape = segmentedShape(0, 1),
                isLast = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 分区：更新检测
        SectionContainer(title = stringResource(R.string.update_section)) {
            val scope = rememberCoroutineScope()
            var checking by remember { mutableStateOf(false) }
            SettingListItem(
                item = SettingItemData(
                    icon = Icons.Filled.SystemUpdate,
                    title = stringResource(R.string.check_update),
                    subtitle = null,
                    colorPreview = null,
                    onClick = {
                        if (!checking) {
                            checking = true
                            scope.launch {
                                when (val result = settingsViewModel.checkUpdateNow()) {
                                    is UpdateChecker.UpdateCheckResult.NewVersion -> Toast.makeText(
                                        context,
                                        context.getString(R.string.new_version_available, result.info.latestVersion),
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    UpdateChecker.UpdateCheckResult.UpToDate -> Toast.makeText(
                                        context,
                                        context.getString(R.string.up_to_date),
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    is UpdateChecker.UpdateCheckResult.Failed -> Toast.makeText(
                                        context,
                                        context.getString(R.string.update_check_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                checking = false
                            }
                        }
                    }
                ),
                shape = segmentedShape(0, 2),
                isLast = false,
                showArrow = false
            )

            SettingListItem(
                item = SettingItemData(
                    icon = ImageVector.vectorResource(R.drawable.ic_lock),
                    title = stringResource(R.string.github_token),
                    subtitle = stringResource(
                        if (githubPat.isNotBlank()) R.string.token_configured else R.string.token_not_set
                    ),
                    colorPreview = null,
                    onClick = { showPatDialog = true }
                ),
                shape = segmentedShape(1, 2),
                isLast = true,
                showArrow = true
            )

            // 有新版本时：下方展示可点击提示，跳转到发布页
            if (updateInfo != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    shape = SmallShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseUrl))
                            )
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.cannot_open_link),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_version),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.new_version_available, updateInfo.latestVersion),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }

    // GitHub PAT 对话框：填写后检测走认证 API，留空则回退网页跳转检测
    if (showPatDialog) {
        var patInput by remember { mutableStateOf(githubPat) }
        AlertDialog(
            onDismissRequest = { showPatDialog = false },
            title = { Text(stringResource(R.string.github_token)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.github_token_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 说明令牌所需最小权限，避免用户不知道该怎么建 token
                    Text(
                        text = stringResource(R.string.github_token_scope_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = patInput,
                        onValueChange = { patInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsViewModel.setGithubPat(patInput)
                    showPatDialog = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.github_token_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPatDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 页面脚手架：预测式返回容器 + 顶栏 + 可滚动内容区，供高级设置 / 实验功能共用。
 *
 * 退场行为统一由 [PredictiveBackPage] 提供：
 * - 左上角返回按钮 = 普通返回，整页向右滑出，不做预测性返回的缩放 / 漂移；
 * - 系统返回手势 = 预测性返回预览（缩放 + 漂移 + 淡出）。
 */
@Composable
private fun LabScaffold(
    title: String,
    showTip: Boolean,
    onBack: () -> Unit,
    enterProgress: Float,
    onScrimChange: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    PredictiveBackPage(
        enterProgress = enterProgress,
        onScrimChange = onScrimChange,
        onBack = onBack,
    ) { requestBack ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 顶部返回栏
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = requestBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.previous_step)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showTip) {
                // 实验性提示
                InfoTipCard(text = stringResource(R.string.lab_tip))
                Spacer(modifier = Modifier.height(16.dp))
            }

            content()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 分区卡片容器：标题 + 内容，使用统一的 [CardShape] 圆角与背景。
 */
@Composable
private fun SectionContainer(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        content()
        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * 实验性功能提示卡片。
 */
@Composable
private fun InfoTipCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SmallShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 回退列表编辑对话框：勾选启用 + 长按拖动重排。
 */
@Composable
private fun FallbackListDialog(
    candidates: List<SmartAuthorizerCandidate>,
    availability: Map<Authorizer, Boolean>,
    onDismiss: () -> Unit,
    onConfirm: (List<SmartAuthorizerCandidate>) -> Boolean,
) {
    val editable = remember(candidates) {
        mutableStateListOf<SmartAuthorizerCandidate>().apply { addAll(candidates) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.lab_smart_authorizer_fallback_list),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.lab_smart_authorizer_fallback_list_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                DraggableCandidateList(
                    items = editable,
                    availability = availability
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(editable.toList()) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private val CandidateRowHeight = 64.dp

/**
 * 可拖拽重排的候选列表（长按触发，[graphicsLayer] 位移）。
 */
@Composable
private fun DraggableCandidateList(
    items: SnapshotStateList<SmartAuthorizerCandidate>,
    availability: Map<Authorizer, Boolean>,
) {
    val density = LocalDensity.current
    val rowPitchPx = with(density) { (CandidateRowHeight + SegmentedGap).toPx() }

    var draggingAuthorizer by remember { mutableStateOf<Authorizer?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column {
        items.forEachIndexed { index, candidate ->
            val isDragging = draggingAuthorizer == candidate.authorizer
            CandidateRow(
                candidate = candidate,
                available = availability[candidate.authorizer] ?: false,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .pointerInput(candidate.authorizer) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingAuthorizer = candidate.authorizer
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                            },
                            onDragEnd = {
                                val from = items.indexOfFirst { it.authorizer == draggingAuthorizer }
                                if (from >= 0 && rowPitchPx > 0f) {
                                    val shift = (dragOffset / rowPitchPx).roundToInt()
                                    val to = (from + shift).coerceIn(0, items.lastIndex)
                                    if (to != from) {
                                        val moved = items.removeAt(from)
                                        items.add(to, moved)
                                    }
                                }
                                draggingAuthorizer = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingAuthorizer = null
                                dragOffset = 0f
                            }
                        )
                    },
                onToggle = { enabled ->
                    val idx = items.indexOfFirst { it.authorizer == candidate.authorizer }
                    if (idx >= 0) items[idx] = items[idx].copy(enabled = enabled)
                }
            )
            if (index != items.lastIndex) {
                Spacer(modifier = Modifier.height(SegmentedGap))
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: SmartAuthorizerCandidate,
    available: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val statusText = remember(candidate.authorizer) {
        availabilityText(context, candidate.authorizer, available)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(CandidateRowHeight),
        shape = SmallShape,
        color = MaterialTheme.colorScheme.surfaceBright,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = { onToggle(!candidate.enabled) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = candidate.enabled,
                onCheckedChange = { onToggle(it) }
            )

            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_package),
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 4.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = context.getString(candidate.authorizer.displayNameRes),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = if (candidate.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 生成候选的可用性描述文本（已授权/未授权/未安装/系统安装器）。
 */
private fun availabilityText(
    context: Context,
    authorizer: Authorizer,
    available: Boolean,
): String {
    if (authorizer == Authorizer.None) {
        return context.getString(R.string.authorizer_none)
    }

    val mode = when (authorizer) {
        Authorizer.Shizuku -> PrivilegeHelper.PrivilegeMode.SHIZUKU
        Authorizer.Dhizuku -> PrivilegeHelper.PrivilegeMode.DHIZUKU
        Authorizer.None -> return context.getString(R.string.authorizer_none)
    }

    return if (available) {
        context.getString(R.string.privilege_status_authorized)
    } else {
        PrivilegeHelper.getStatusDescription(PrivilegeHelper.getStatus(context, mode), context)
    }
}
