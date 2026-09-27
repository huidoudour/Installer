package dev.huidoudour.installer.ui.components

import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 与实验室页面一致的预测性返回容器。
 *
 * 页面在手势过程中以触点为中心缩小；确认返回后继续向手势方向淡出，
 * 从而让其下方的页面在整个过程里保持可见。
 *
 * [content] 会收到一个 `requestBack`：顶部返回按钮等主动返回入口应调用它，
 * 这样「点击返回」与「系统返回手势」共用同一套退场动画。
 */
@Composable
fun PredictiveBackPage(
    enterProgress: Float,
    onBackProgressChange: (Float) -> Unit,
    onBack: () -> Unit,
    content: @Composable (requestBack: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val gestureProgress = remember { Animatable(0f) }
    val fadeProgress = remember { Animatable(0f) }
    // 仅在确认返回后驱动水平漂移：手势阶段页面只缩放、不位移，避免被推出屏幕。
    val driftProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var gestureTouchY by remember { mutableFloatStateOf(0f) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    val backGestureEasing = remember { CubicBezierEasing(0.1f, 0.1f, 0f, 1f) }
    val layoutDirection = LocalLayoutDirection.current
    val currentOnBackProgressChange by rememberUpdatedState(onBackProgressChange)
    val currentOnBack by rememberUpdatedState(onBack)
    val scope = rememberCoroutineScope()

    // 统一的退场入口：「顶部返回按钮」与「不支持预测性返回的系统返回」都走这里，
    // 保证任何入口触发的返回都有同一套淡出 + 漂移动画。
    val requestBack: () -> Unit = remember(scope) {
        {
            scope.launch {
                commitPredictiveBackExit(
                    gestureProgress = gestureProgress,
                    fadeProgress = fadeProgress,
                    driftProgress = driftProgress,
                    onFinished = { currentOnBack() },
                )
            }
        }
    }

    LaunchedEffect(gestureProgress) {
        snapshotFlow { gestureProgress.value }.collect { currentOnBackProgressChange(it) }
    }

    // 预测性返回的手势进度事件（onBackStarted / onBackProgressed）需要 API 34 及以上；
    // 更低版本拿不到手势进度，退化为普通返回：确认返回时直接播放退场动画。
    val supportsPredictiveBackGesture =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    PredictiveBackHandler(enabled = supportsPredictiveBackGesture) { events ->
        var completed = false
        // 手势起点 X：用固定 dp 位移重新归一化进度，消除跨 API / 屏幕尺寸的缩放差异。
        var startTouchX = Float.NaN
        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                gestureTouchY = event.touchY
                val progress = if (event.touchX != 0f) {
                    if (startTouchX.isNaN()) startTouchX = event.touchX
                    PredictiveBackMotion.normalizeProgress(
                        abs(event.touchX - startTouchX),
                        density.density
                    )
                } else {
                    // 部分设备/低版本不提供 touchX，回退到系统进度。
                    event.progress
                }
                gestureProgress.snapTo(progress)
            }
            completed = true
            // 确认返回：几何位移从松手位置继续推进，不透明度用更短的墙钟窗口先归零。
            commitPredictiveBackExit(
                gestureProgress = gestureProgress,
                fadeProgress = fadeProgress,
                driftProgress = driftProgress,
                onFinished = { currentOnBack() },
            )
        } finally {
            if (!completed) {
                cancelPredictiveBackExit(
                    gestureProgress = gestureProgress,
                    fadeProgress = fadeProgress,
                )
            }
        }
    }

    // 不支持预测性返回的版本上，用普通返回回调补上一致的退场动画。
    BackHandler(enabled = !supportsPredictiveBackGesture) { requestBack() }

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
                // 漂移方向跟随手势边缘：左边缘挥出右移，右边缘挥出左移。
                // 漂移只在确认返回后发生，手势阶段页面始终完整留在屏幕内。
                val exitTranslation = PredictiveBackMotion.exitDirection(swipeEdge) *
                    exitDriftPx * driftProgress.value
                translationX = enterTranslation + exitTranslation
                alpha = 1f - fadeProgress.value
                shape = predictiveShape
                clip = enterProgress < 1f || gestureProgress.value > 0f ||
                    fadeProgress.value > 0f || driftProgress.value > 0f
            },
        color = MaterialTheme.colorScheme.background
    ) {
        content(requestBack)
    }
}
