package dev.huidoudour.installer.ui.components

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 预测性返回退出动画的共享参数与曲线。
 *
 * 数值对齐参考项目 InstallerX-Revived 的 `ui/animation/predictiveback`
 * （`ScaleNavTransition` / `CrossActivityPredictive`），让实验室、关于页与更新日志页
 * 的退出表现出同一套节奏。
 */
object PredictiveBackMotion {

    /** 手势段缩放下限，对应参考项目 `SCALE_MIN` / `CROSS_ACTIVITY_MIN_SCALE`。 */
    const val MIN_SCALE = 0.85f

    /** 手势段缓动，对应参考项目 `BackGestureEasing`。 */
    val GestureEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    /** 退出时的水平漂移量，对应参考项目 `CrossActivityDrift` / `ScaleExitDrift`。 */
    const val EXIT_DRIFT_DP = 96f

    /** 提交后几何位移的总时长，对应参考项目 `NavSettleSpec.Tween(450, FastOutExtraSlowIn)`。 */
    const val GEOMETRY_DURATION_MILLIS = 450

    /**
     * 提交后淡出的墙钟时长，与 [GEOMETRY_DURATION_MILLIS] 的几何位移解耦。
     *
     * 参考项目用 `elapsedMillis / 450f * 5f` 把不透明度直接压到 0（约等于 450ms 中的前 1/5），
     * 这里改用独立时长，既保留「先淡出、后走完位移」的节奏，又不会快到像瞬间消失。
     */
    const val FADE_DURATION_MILLIS = 160

    /**
     * 取消手势后的回弹刚度。
     *
     * 对齐参考项目 `ScaleNavTransition` 的 `NavSettleSpec.Spring(stiffness = 1500f)`。
     * 原先使用的 [Spring.StiffnessHigh]（10000）过刚：回弹在极短时间内冲完行程，
     * 视觉上接近"瞬移"而非回弹；配合松手速度种子后，1500 能在同样的墙钟窗口内
     * 给出连续可读的减速过程。
     */
    const val CANCEL_STIFFNESS = 1500f

    /**
     * 参考项目 `NavTransitionEasing.FastOutExtraSlowIn`：两段三次贝塞尔拼接的全局缓动，
     * 前段极快、后段极缓，用于提交后的几何位移。
     */
    val CommitEasing: Easing = run {
        val knotX = 0.166666f
        val knotY = 0.4f
        val first = CubicBezierEasing(0.05f / knotX, 0f, 0.133333f / knotX, 0.06f / knotY)
        val second = CubicBezierEasing(
            (0.208333f - knotX) / (1f - knotX),
            (0.82f - knotY) / (1f - knotY),
            (0.25f - knotX) / (1f - knotX),
            (1f - knotY) / (1f - knotY),
        )
        Easing { fraction ->
            if (fraction < knotX) {
                knotY * first.transform(fraction / knotX)
            } else {
                knotY + (1f - knotY) * second.transform((fraction - knotX) / (1f - knotX))
            }
        }
    }

    /** 退场淡出：起步柔和、收尾干脆。 */
    val ExitFadeEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.7f, 0.2f)

    /**
     * 手势段的缩放系数。
     *
     * 手势进度为 0 时必须精确返回 1f：静止状态不能残留任何缩放，
     * 否则页面会带着偏移渲染。缩放在整个手势里单调递减到 [MIN_SCALE]。
     */
    fun gestureScale(gestureProgress: Float): Float {
        val eased = GestureEasing.transform(gestureProgress.coerceIn(0f, 1f))
        return if (eased <= 0f) 1f else 1f - (1f - MIN_SCALE) * eased
    }

    /*
     * 关于「固定 dp 行程提交」的说明（已弃用，勿轻易恢复）：
     *
     * 这里曾定义 COMMIT_DISTANCE_DP = 120f 与 normalizeProgress()，用手势起点的 touchX
     * 自行反推进度。该方案已移除，手势进度改为直接采用系统的 BackEventCompat.progress。
     * 原因是自算版本在「拖到顶后原路返回」时会产生非单调的进度：
     *
     *   1. abs(touchX - startTouchX) 丢掉方向符号，回退过程中的任何抖动都会被翻倍成反向位移；
     *   2. `touchX != 0f` 的判断会在手指恰好回到左边缘（touchX 为 0 是合法坐标）时，
     *      把量纲从「px / (120dp × density)」切成系统的 0..1，两套值互相跳变。
     *
     * 卡片位置是 progress 的橡皮图章，于是表现为「连续三四帧不动、再猛跳一次」。
     * 若将来确实需要固定物理行程的手感，必须让全程量纲一致并施加单调约束，不要无条件 abs()。
     */

    /** 程序化入场的横向推入量。 */
    fun enterTranslation(enterProgress: Float, width: Float, direction: Float): Float =
        direction * (1f - enterProgress.coerceIn(0f, 1f)) * width

    /** 确认返回后的漂移方向：左侧边缘的返回手势向右推出，右侧边缘反之。 */
    fun exitDirection(swipeEdge: Int): Float =
        if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f

    /** 依据松手位置反推提交动画时长，让快滑松手不必多等满 450ms。 */
    fun commitDurationMillis(releaseProgress: Float): Int =
        (GEOMETRY_DURATION_MILLIS * (1f - releaseProgress.coerceIn(0f, 1f)))
            .toInt()
            .coerceAtLeast(1)
}

/**
 * 播放「确认返回」的收尾动画，结束后回调 [onFinished]。
 *
 * [gestureProgress] 从松手瞬间的进度继续推进到 1f，因此松手时不会跳变；
 * [driftProgress] 只在确认返回后才从 0 推进到 1f，用于驱动水平漂移，
 * 这样手势进行中页面只缩放、不位移，不会滑出屏幕；
 * [fadeProgress] 用独立且更短的墙钟窗口提前归零，页面在产生明显位移之前就已淡出，
 * 避免「拖着内容滑走」的观感。
 */
suspend fun commitPredictiveBackExit(
    gestureProgress: Animatable<Float, *>,
    fadeProgress: Animatable<Float, *>,
    driftProgress: Animatable<Float, *>,
    onFinished: () -> Unit,
) {
    val duration = PredictiveBackMotion.commitDurationMillis(gestureProgress.value)
    coroutineScope {
        launch {
            gestureProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = duration,
                    easing = PredictiveBackMotion.CommitEasing,
                )
            )
        }
        launch {
            driftProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = duration,
                    easing = PredictiveBackMotion.CommitEasing,
                )
            )
        }
        launch {
            fadeProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = PredictiveBackMotion.FADE_DURATION_MILLIS,
                    easing = PredictiveBackMotion.ExitFadeEasing,
                )
            )
        }
    }
    onFinished()
}

/**
 * 手势取消时的回弹：缩放回到 1f，不透明度立即复位。
 *
 * 回弹与手势之间是「snap → spring」的交接，必须让值**和一阶导数**同时连续，
 * 否则从零速度重新起步会在接缝处产生一次可见的顿挫。因此这里：
 * 1. 先 [Animatable.stop] 掉可能还在飞的动画（原子地停下，避免它与回弹抢同一个驱动值）；
 * 2. 取停稳瞬间的 [Animatable.velocity] 作为回弹的 `initialVelocity`；
 * 3. 用 [PredictiveBackMotion.CANCEL_STIFFNESS] 播放收敛。
 *
 * 这三步对应参考项目 `NavDriver.settleTo`（`initialVelocity: Float = velocity`）
 * 与 `drivePredictiveBack` 开头的 `animatedTop.stop()`。
 */
suspend fun cancelPredictiveBackExit(
    gestureProgress: Animatable<Float, *>,
    fadeProgress: Animatable<Float, *>,
) {
    withContext(NonCancellable) {
        // 原子抓取：先把在飞的动画停掉，再采样速度，否则两者之间还留着一帧的窗口。
        gestureProgress.stop()
        val releaseVelocity = gestureProgress.velocity
        coroutineScope {
            launch {
                gestureProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(stiffness = PredictiveBackMotion.CANCEL_STIFFNESS),
                    initialVelocity = releaseVelocity,
                )
            }
            launch { fadeProgress.snapTo(0f) }
        }
    }
}
