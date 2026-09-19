package dev.huidoudour.installer.ui.lab

import android.content.Context
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.huidoudour.installer.auth.Authorizer
import dev.huidoudour.installer.auth.PrivilegeHelper
import dev.huidoudour.installer.auth.SmartAuthorizerCandidate
import dev.huidoudour.installer.ui.settings.SettingItemData
import dev.huidoudour.installer.ui.settings.SettingListItem
import dev.huidoudour.installer.ui.settings.SettingsSwitchItem
import dev.huidoudour.installer.ui.theme.CardShape
import dev.huidoudour.installer.ui.theme.SegmentedGap
import dev.huidoudour.installer.ui.theme.SmallShape
import dev.huidoudour.installer.ui.theme.segmentedShape
import dev.huidoudour.installer.R
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 实验室页面：智能授权回退列表 + 签名校验开关（实验性功能）。
 */
@Composable
fun LabScreen(
    onBack: () -> Unit,
    enterProgress: Float = 1f,
    onBackProgressChange: (Float) -> Unit = {},
    viewModel: LabViewModel = viewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val gestureProgress = remember { Animatable(0f) }
    val commitProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var gestureTouchY by remember { mutableFloatStateOf(0f) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    val backGestureEasing = remember { CubicBezierEasing(0.1f, 0.1f, 0f, 1f) }
    val layoutDirection = LocalLayoutDirection.current
    val currentOnBackProgressChange by rememberUpdatedState(onBackProgressChange)

    val tryMultipleAuthorizers by viewModel.tryMultipleAuthorizers.collectAsState()
    val authorizerByInstallState by viewModel.authorizerByInstallState.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val checkSignature by viewModel.checkSignature.collectAsState()
    val showSignatureDetails by viewModel.showSignatureDetails.collectAsState()
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

    LaunchedEffect(gestureProgress) {
        snapshotFlow { gestureProgress.value }.collect { currentOnBackProgressChange(it) }
    }

    PredictiveBackHandler {
        var completed = false
        try {
            it.collect { event ->
                swipeEdge = event.swipeEdge
                gestureTouchY = event.touchY
                gestureProgress.snapTo(event.progress)
            }
            completed = true
            coroutineScope {
                launch {
                    gestureProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 450,
                            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                        )
                    )
                }
                launch {
                    commitProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 450,
                            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                        )
                    )
                }
            }
            onBack()
        } finally {
            if (!completed) {
                withContext(NonCancellable) {
                    coroutineScope {
                        launch {
                            gestureProgress.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(stiffness = Spring.StiffnessHigh)
                            )
                        }
                        launch { commitProgress.snapTo(0f) }
                    }
                }
            }
        }
    }

    val predictiveShape = RoundedCornerShape(32.dp)
    val exitDriftPx = with(density) { 96.dp.toPx() }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { pageSize = it }
            .graphicsLayer {
                val easedProgress = backGestureEasing.transform(gestureProgress.value)
                val scale = 1f - 0.15f * easedProgress
                val pivotX = if (swipeEdge == BackEventCompat.EDGE_LEFT) 0.8f else 0.2f
                val pivotY = if (pageSize.height > 0) {
                    (gestureTouchY / pageSize.height).coerceIn(0.1f, 0.9f)
                } else {
                    0.5f
                }
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(pivotX, pivotY)
                val enterDirection = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                val enterTranslation = enterDirection * (1f - enterProgress) * size.width
                val exitTranslation = if (swipeEdge == BackEventCompat.EDGE_RIGHT) {
                    -exitDriftPx * commitProgress.value
                } else {
                    exitDriftPx * commitProgress.value
                }
                translationX = enterTranslation + exitTranslation
                alpha = 1f - commitProgress.value
                shape = predictiveShape
                clip = enterProgress < 1f || gestureProgress.value > 0f || commitProgress.value > 0f
            },
        color = MaterialTheme.colorScheme.background
    ) {
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
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.previous_step)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.lab),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 实验性提示
            InfoTipCard(text = stringResource(R.string.lab_tip))

            Spacer(modifier = Modifier.height(16.dp))

            // 分区：智能授权
            SectionContainer(title = stringResource(R.string.lab_authorizer_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.lab_try_multiple_authorizers),
                    subtitle = stringResource(R.string.lab_try_multiple_authorizers_desc),
                    checked = tryMultipleAuthorizers,
                    onCheckedChange = { viewModel.setTryMultipleAuthorizers(it) },
                    shape = segmentedShape(0, 3),
                    isLast = false
                )

                SettingsSwitchItem(
                    title = stringResource(R.string.lab_authorizer_by_install_state),
                    subtitle = stringResource(R.string.lab_authorizer_by_install_state_desc),
                    checked = authorizerByInstallState,
                    onCheckedChange = { viewModel.setAuthorizerByInstallState(it) },
                    shape = segmentedShape(1, 3),
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
                    shape = segmentedShape(2, 3),
                    isFirst = false,
                    isLast = true,
                    showArrow = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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
        }
    }

    if (showFallbackDialog) {
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
    val context = LocalContext.current
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
