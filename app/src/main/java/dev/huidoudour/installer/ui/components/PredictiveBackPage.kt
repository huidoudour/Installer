package dev.huidoudour.installer.ui.components

import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import kotlinx.coroutines.launch
import kotlin.math.max

/** 顶部返回按钮的滑出时长：与普通页面返回的转场节奏保持一致。 */
private const val BUTTON_EXIT_DURATION_MILLIS = 280

/**
 * 与实验室页面一致的预测性返回容器。
 *
 * 页面在手势过程中以触点为中心缩小；确认返回后继续向手势方向淡出，
 * 从而让其下方的页面在整个过程里保持可见。
 *
 * [content] 会收到一个 `requestBack`：顶部返回按钮等主动返回入口应调用它。
 * 主动返回是**普通返回**——整页向右滑出（RTL 向左），不播放预测性返回的缩放 / 漂移 / 淡出；
 * 那套缩放预览只留给系统返回手势本身，因为它是手势进度的实时预览。
 * 不支持预测性返回手势的版本上，系统返回（滑动/按键）同样只是普通返回：直接关闭。
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
    // 顶部返回按钮的退场进度：0 → 1 表示整页从原位滑出屏幕。
    val slideExitProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var gestureTouchY by remember { mutableFloatStateOf(0f) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    val backGestureEasing = remember { CubicBezierEasing(0.1f, 0.1f, 0f, 1f) }
    val layoutDirection = LocalLayoutDirection.current
    val currentOnBackProgressChange by rememberUpdatedState(onBackProgressChange)
    val currentOnBack by rememberUpdatedState(onBack)
    val scope = rememberCoroutineScope()
    var exiting by remember { mutableStateOf(false) }

    // 顶部返回按钮：普通返回，整页向右滑出后再关闭页面（不做缩放 / 漂移 / 淡出）。
    val requestBack: () -> Unit = remember(scope) {
        {
            if (!exiting) {
                exiting = true
                scope.launch {
                    slideExitProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = BUTTON_EXIT_DURATION_MILLIS,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                    currentOnBack()
                }
            }
        }
    }

    // 手势进度与按钮滑出进度共用同一条上报通道：容器用它把下层页面从「让位」位置平滑归位。
    LaunchedEffect(gestureProgress, slideExitProgress) {
        snapshotFlow { max(gestureProgress.value, slideExitProgress.value) }
            .collect { currentOnBackProgressChange(it.coerceIn(0f, 1f)) }
    }

    // 预测性返回的手势进度事件（onBackStarted / onBackProgressed）需要 API 34 及以上；
    // 更低版本拿不到手势进度，退化为普通返回：直接关闭，不播放退场动画。
    val supportsPredictiveBackGesture =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    PredictiveBackHandler(enabled = supportsPredictiveBackGesture) { events ->
        var completed = false
        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                gestureTouchY = event.touchY
                // 直接采用系统给出的手势进度。
                //
                // 曾用 `abs(event.touchX - startTouchX)` 重新归一化（固定 120dp 提交距离），
                // 那会在拖拽回退时产生非单调的进度：`abs()` 丢掉方向符号，把回退过程中的
                // 任何抖动翻倍成反向位移；且 `event.touchX != 0f` 这个判断会在 `touchX` 恰好
                // 为 0（手指正回到左边缘的合法坐标）时，把量纲从「px / 120dp 归一化」切成
                // 系统的 0..1，两者互相跳变。卡片位置是这个值的橡皮图章，于是表现为
                // 「连停三四帧、再猛跳一次」。
                //
                // 系统的 `progress` 本身就是 0..1、随手势单调变化的量，且参考项目
                // （miuix `NavBackEvent.progress`）用的也是它。
                gestureProgress.snapTo(event.progress.coerceIn(0f, 1f))
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

    // 不支持预测性返回的版本上，系统返回（滑动/按键）退化为普通返回：直接关闭，
    // 不播放退场动画，避免普通返回也播放一整套“缩小 + 漂移”。
    BackHandler(enabled = !supportsPredictiveBackGesture) { currentOnBack() }

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
                // 顶部返回按钮：整页按普通返回的方向滑出（LTR 向右、RTL 向左），不缩放也不漂移。
                val slideExitTranslation = enterDirection * slideExitProgress.value * size.width
                translationX = enterTranslation + exitTranslation + slideExitTranslation
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
