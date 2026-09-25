package dev.huidoudour.installer.ui.components

import androidx.activity.BackEventCompat
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
import kotlinx.coroutines.flow.collect

/**
 * 与实验室页面一致的预测性返回容器。
 *
 * 页面在手势过程中以触点为中心缩小；确认返回后继续向手势方向淡出，
 * 从而让其下方的页面在整个过程里保持可见。
 */
@Composable
fun PredictiveBackPage(
    enterProgress: Float,
    onBackProgressChange: (Float) -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val gestureProgress = remember { Animatable(0f) }
    val fadeProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var gestureTouchY by remember { mutableFloatStateOf(0f) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    val backGestureEasing = remember { CubicBezierEasing(0.1f, 0.1f, 0f, 1f) }
    val layoutDirection = LocalLayoutDirection.current
    val currentOnBackProgressChange by rememberUpdatedState(onBackProgressChange)

    LaunchedEffect(gestureProgress) {
        snapshotFlow { gestureProgress.value }.collect { currentOnBackProgressChange(it) }
    }

    PredictiveBackHandler { events ->
        var completed = false
        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                gestureTouchY = event.touchY
                gestureProgress.snapTo(event.progress)
            }
            completed = true
            // 确认返回：几何位移从松手位置继续推进，不透明度用更短的墙钟窗口先归零。
            commitPredictiveBackExit(
                gestureProgress = gestureProgress,
                fadeProgress = fadeProgress,
                onFinished = onBack,
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
                val exitTranslation = PredictiveBackMotion.exitDirection(swipeEdge) *
                    exitDriftPx * gestureProgress.value
                translationX = enterTranslation + exitTranslation
                alpha = 1f - fadeProgress.value
                shape = predictiveShape
                clip = enterProgress < 1f || gestureProgress.value > 0f || fadeProgress.value > 0f
            },
        color = MaterialTheme.colorScheme.background
    ) {
        content()
    }
}
