package dev.huidoudour.terminal

import android.view.Choreographer
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Compose terminal Canvas with a native, composition-aware IME input connection. */
@Composable
fun TerminalView(
    terminal: TerminalEmulator,
    onKeyInput: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    onTerminalSizeChanged: (rows: Int, cols: Int) -> Unit = { rows, cols -> terminal.resize(rows, cols) }
) {
    var screenVersion by remember(terminal) { mutableIntStateOf(0) }
    var showCursor by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val inputView = remember(context) { TerminalInputView(context) }
    val inputMethodManager = remember(context) { context.getSystemService(InputMethodManager::class.java) }

    // 注册终端更新 + 光标闪烁 (帧同步限流防卡顿)
    val choreographer = remember { Choreographer.getInstance() }
    DisposableEffect(terminal) {
        var pendingFrame = false
        var frameCallback: Choreographer.FrameCallback? = null

        terminal.onScreenUpdated = {
            if (!pendingFrame) {
                pendingFrame = true
                val cb = Choreographer.FrameCallback {
                    screenVersion++
                    pendingFrame = false
                }
                frameCallback = cb
                choreographer.postFrameCallback(cb)
            }
        }

        onDispose {
            terminal.onScreenUpdated = null
            frameCallback?.let { choreographer.removeFrameCallback(it) }
        }
    }

    LaunchedEffect(terminal) {
        showCursor = true
        while (true) {
            delay(500.milliseconds)
            showCursor = !showCursor
        }
    }

    val density = LocalDensity.current
    val fontSizePx = with(density) { 12f.sp.toPx() }
    val renderer = remember(context, fontSizePx) { TerminalRenderer(context, fontSizePx) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(terminal, viewportSize, renderer) {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            onTerminalSizeChanged(renderer.rowsFor(viewportSize.height), renderer.columnsFor(viewportSize.width))
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFF1E1E1E))
    ) {
        AndroidView(
            factory = { inputView },
            modifier = Modifier.fillMaxSize(),
            update = { it.onInput = onKeyInput }
        )

        // ====== Layer 2: 终端 Canvas 渲染 ======
        Canvas(
            modifier = Modifier.fillMaxSize().onSizeChanged { viewportSize = it }
        ) {
            // Read state in the draw phase: output and blinking only invalidate Canvas,
            // and cursor/screen data are read together instead of capturing stale positions.
            @Suppress("UNUSED_EXPRESSION")
            screenVersion
            renderer.draw(drawContext.canvas.nativeCanvas, size.width, size.height, terminal, showCursor)
        }

        // 触摸拖拽累加器，累加偏移量超过一个 cell 才触发滚动，避免抖动
        var dragAccumulator by remember { mutableStateOf(0f) }
        
        // ====== 触摸区域: 点击聚焦 + 上下滑动滚动回看 ======
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(terminal, renderer) {
                    detectTapGestures {
                        inputView.requestFocus()
                        inputMethodManager?.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
                .pointerInput(terminal, renderer) {
                    detectDragGestures(
                        onDragEnd = {
                            dragAccumulator = 0f
                        }
                    ) { _, dragAmount ->
                        dragAccumulator += dragAmount.y
                        val lines = (dragAccumulator / renderer.cellHeight).toInt()
                        if (lines != 0) {
                            if (lines > 0) {
                                // 手指下滑 → 显示更旧历史
                                terminal.scrollHistoryUp(lines)
                            } else {
                                // 手指上滑 → 显示更新内容
                                terminal.scrollHistoryDown(-lines)
                            }
                            dragAccumulator -= lines * renderer.cellHeight
                        }
                    }
                }
        )
    }
}
