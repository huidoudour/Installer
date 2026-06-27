package io.github.huidoudour.Installer.ui

import android.graphics.Typeface
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.huidoudour.Installer.util.TerminalEmulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Compose 终端视图 - 渲染 TerminalEmulator 的字符网格
 *
 * 双通道输入:
 * 1. 全尺寸隐藏 BasicTextField - 捕获 IME (软键盘) 输入
 * 2. onKeyEvent - 捕获硬件键盘特殊键 (方向键、Ctrl组合等)
 *
 * IME 输入采用累积 buffer + 同步消费:
 * - onValueChange 中同步处理新字符 / 删除，保持光标实时同步
 * - 不清空 buffer (只累积)，避免打断 Android 14 IME 组合态
 * - buffer 超过 256 字符时由 snapshotFlow 异步重置
 */
@Composable
fun TerminalView(
    terminal: TerminalEmulator,
    onKeyInput: (ByteArray) -> Unit,
    needLocalEcho: Boolean = false,
    modifier: Modifier = Modifier
) {
    var screenVersion by remember { mutableIntStateOf(0) }
    var showCursor by remember { mutableStateOf(true) }
    var blinkJob by remember { mutableStateOf<Job?>(null) }
    val textFieldFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // IME 输入缓冲区 — onValueChange 同步处理输入，不清空 buffer 以防打断 IME 组合态
    var imeText by remember { mutableStateOf("") }
    var imeConsumedLen by remember { mutableIntStateOf(0) }

    // 定期重置 buffer 以防内存无限增长 (远大于单次输入，不会在组合中触发)
    LaunchedEffect(Unit) {
        snapshotFlow { imeText }
            .filter { it.length > 256 }
            .collect {
                imeText = ""
                imeConsumedLen = 0
            }
    }

    // 注册终端更新 + 光标闪烁 (帧同步限流防卡顿)
    val choreographer = remember { Choreographer.getInstance() }
    DisposableEffect(terminal) {
        var pendingFrame = false
        var frameCallback: Choreographer.FrameCallback? = null

        terminal.onScreenUpdated = {
            if (!pendingFrame) {
                pendingFrame = true
                val cb = object : Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        screenVersion++
                        pendingFrame = false
                    }
                }
                frameCallback = cb
                choreographer.postFrameCallback(cb)
            }
        }

        val scope = CoroutineScope(Dispatchers.Main)
        blinkJob = scope.launch {
            while (true) {
                delay(500)
                showCursor = !showCursor
            }
        }

        onDispose {
            terminal.onScreenUpdated = null
            frameCallback?.let { choreographer.removeFrameCallback(it) }
            blinkJob?.cancel()
        }
    }

    val currentScreen = terminal.getVisibleScreen(terminal.rows)
    val curRow = terminal.cursorRow
    val curCol = terminal.cursorCol
    val isScrollback = terminal.isScrollbackActive()
    val isCursorVis = showCursor && terminal.cursorVisible && !isScrollback
    val termRows = terminal.rows
    val termCols = terminal.cols

    // 固定单元格尺寸 (不随 Canvas 大小变化，消除键盘弹出时的缩放效果)
    val density = LocalDensity.current
    val fixedFontSizePx = with(density) { 12f.sp.toPx() }

    // 渲染用 TextPaint — 与 Canvas drawText 共享同一实例
    val textPaint = remember {
        android.text.TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
    }

    // 等宽字体字符 advance 宽度 (含 bearing) — drawText 内部依此间距放置字符
    val fixedCellWidth = remember(fixedFontSizePx) {
        textPaint.textSize = fixedFontSizePx
        val widths = FloatArray(1)
        textPaint.getTextWidths("M", widths)
        widths[0].coerceAtLeast(1f)
    }
    val fixedCellHeight = fixedFontSizePx * 1.25f

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFF1E1E1E))
            .onKeyEvent { event ->
                // 硬件键盘特殊键 (方向键、Ctrl组合、F功能键等)
                if (event.type == KeyEventType.KeyDown) {
                    val bytes = keyEventToBytes(event)
                    if (bytes != null) {
                        onKeyInput(bytes)
                        if (needLocalEcho) {
                            // 本地回显: 将发送到 PTY 的字节同步喂给模拟器
                            when (event.key) {
                                Key.Enter -> terminal.feed(byteArrayOf(0x0A), 1)
                                Key.Backspace, Key.Delete ->
                                    terminal.feed(bytes, bytes.size) // 0x7F
                                in letterKeys -> {
                                    val char = charFor(event.key)
                                    if (char != null) {
                                        val c = if (event.isShiftPressed) char.uppercaseChar() else char
                                        val b = c.toString().toByteArray(Charsets.UTF_8)
                                        terminal.feed(b, b.size)
                                    }
                                }
                                else -> {}
                            }
                        }
                        true
                    } else {
                        false
                    }
                } else false
            }
    ) {
        // ====== Layer 1: 全尺寸隐藏 BasicTextField 捕获 IME ======
        // 全尺寸确保 IME 框架正确路由输入，Canvas 渲染在上层遮盖
        BasicTextField(
            value = imeText,
            onValueChange = { newValue ->
                val consumed = imeConsumedLen

                if (newValue.length < consumed) {
                    // IME 直接删除了 buffer 内的字符 (backspace 等)
                    val deletedCount = consumed - newValue.length
                    repeat(deletedCount) {
                        onKeyInput(byteArrayOf(0x7F))
                        if (needLocalEcho) terminal.feed(byteArrayOf(0x7F), 1)
                    }
                } else if (newValue.length > consumed) {
                    // 有新输入的字符
                    val typed = newValue.substring(consumed)
                    for (char in typed) {
                        when (char) {
                            // IME Enter → LF (0x0A)
                            '\n' -> {
                                onKeyInput(byteArrayOf(0x0A))
                                if (needLocalEcho) terminal.feed(byteArrayOf(0x0A), 1)
                            }
                            '\t' -> {
                                onKeyInput(byteArrayOf(0x09))
                            }
                            '\b' -> {
                                onKeyInput(byteArrayOf(0x7F))
                                if (needLocalEcho) terminal.feed(byteArrayOf(0x7F), 1)
                            }
                            else -> {
                                val bytes = char.toString().toByteArray(Charsets.UTF_8)
                                onKeyInput(bytes)
                                if (needLocalEcho) terminal.feed(bytes, bytes.size)
                            }
                        }
                    }
                }
                // 不在此清空 buffer，只更新消费位 — 避免打断 IME 组合
                imeConsumedLen = newValue.length
                imeText = newValue
            },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(textFieldFocusRequester),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 1.sp,
                color = Color.Transparent
            ),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onKeyInput(byteArrayOf(0x0A))
                    if (needLocalEcho) terminal.feed(byteArrayOf(0x0A), 1)
                }
            )
        )

        // ====== Layer 2: 终端 Canvas 渲染 ======
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // 使用已测量的实际字符宽度，与光标/终端尺寸计算保持一致
            val cellWidth = fixedCellWidth
            val cellHeight = fixedCellHeight
            val visibleRows = (size.height / cellHeight).toInt().coerceAtLeast(1)
                .coerceAtMost(termRows)

            textPaint.textSize = fixedFontSizePx
            val baseline = cellHeight * 0.78f

            for (row in 0 until visibleRows) {
                if (row >= currentScreen.size) break
                val rowCells = currentScreen[row]
                val y = row * cellHeight

                var col = 0
                while (col < termCols && col < rowCells.size) {
                    val cell = rowCells[col]
                    val startCol = col
                    val cellBg = cell.bg
                    val cellFg = cell.fg
                    val cellBold = cell.bold

                    while (col < termCols && col < rowCells.size) {
                        val next = rowCells[col]
                        if (next.bg != cellBg || next.fg != cellFg || next.bold != cellBold) break
                        col++
                    }
                    val endCol = col
                    val spanWidth = (endCol - startCol) * cellWidth

                    drawRect(
                        color = Color(cellBg or (0xFF shl 24)),
                        topLeft = androidx.compose.ui.geometry.Offset(startCol * cellWidth, y),
                        size = androidx.compose.ui.geometry.Size(spanWidth, cellHeight)
                    )

                    val sb = StringBuilder()
                    for (c in startCol until endCol) {
                        sb.append(rowCells[c].char)
                    }
                    val text = sb.toString()
                    if (text.isNotEmpty() && text.any { it != ' ' }) {
                        textPaint.color = cellFg or (0xFF shl 24)
                        textPaint.isFakeBoldText = cellBold
                        drawContext.canvas.nativeCanvas.drawText(
                            text, startCol * cellWidth, y + baseline, textPaint
                        )
                    }
                }

                if (isCursorVis && row == curRow) {
                    val cursorX = curCol * cellWidth
                    drawRect(
                        color = Color(0xFFFFFFFF),
                        topLeft = androidx.compose.ui.geometry.Offset(cursorX, y),
                        size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                        alpha = 0.7f
                    )
                    if (curCol < termCols && curCol < rowCells.size) {
                        val cursorCell = rowCells[curCol]
                        textPaint.color = android.graphics.Color.BLACK
                        textPaint.isFakeBoldText = cursorCell.bold
                        drawContext.canvas.nativeCanvas.drawText(
                            cursorCell.char.toString(), cursorX, y + baseline, textPaint
                        )
                    }
                }
            }

            // 回看模式指示条
            if (isScrollback) {
                drawRect(
                    color = Color(0x80FFFFFF),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width - 4f, 0f),
                    size = androidx.compose.ui.geometry.Size(4f, size.height)
                )
            }

            @Suppress("UNUSED_EXPRESSION")
            screenVersion
        }

        // 触摸拖拽累加器，累加偏移量超过一个 cell 才触发滚动，避免抖动
        var dragAccumulator by remember { mutableStateOf(0f) }
        
        // ====== 触摸区域: 点击聚焦 + 上下滑动滚动回看 ======
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        textFieldFocusRequester.requestFocus()
                        keyboardController?.show()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            dragAccumulator = 0f
                        }
                    ) { _, dragAmount ->
                        dragAccumulator += dragAmount.y
                        val lines = (dragAccumulator / fixedCellHeight).toInt()
                        if (lines != 0) {
                            if (lines > 0) {
                                // 手指下滑 → 显示更旧历史
                                terminal.scrollHistoryUp(lines)
                            } else {
                                // 手指上滑 → 显示更新内容
                                terminal.scrollHistoryDown(-lines)
                            }
                            dragAccumulator -= lines * fixedCellHeight
                        }
                    }
                }
        )
    }
}

/**
 * 硬件键盘 KeyEvent → 终端字节序列
 */
private fun keyEventToBytes(event: androidx.compose.ui.input.key.KeyEvent): ByteArray? {
    val ctrl = event.isCtrlPressed
    val alt = event.isAltPressed
    val shift = event.isShiftPressed
    val k = event.key

    // 特殊功能键 (硬件键盘)
    when (k) {
        // Enter → CR (0x0D)，由终端驱动 (PTY) 或 Shizuku 层处理
        Key.Enter -> return byteArrayOf(0x0D)
        Key.Tab -> return byteArrayOf(0x09)
        Key.Escape -> return byteArrayOf(0x1B)
        Key.Backspace -> return byteArrayOf(0x7F)
        Key.DirectionUp -> return byteArrayOf(0x1B, 0x5B, 0x41)
        Key.DirectionDown -> return byteArrayOf(0x1B, 0x5B, 0x42)
        Key.DirectionRight -> return byteArrayOf(0x1B, 0x5B, 0x43)
        Key.DirectionLeft -> return byteArrayOf(0x1B, 0x5B, 0x44)
        Key.PageUp -> return byteArrayOf(0x1B, 0x5B, 0x35, 0x7E)
        Key.PageDown -> return byteArrayOf(0x1B, 0x5B, 0x36, 0x7E)
        Key.Delete -> return byteArrayOf(0x1B, 0x5B, 0x33, 0x7E)
        Key.MoveHome -> return byteArrayOf(0x1B, 0x5B, 0x48)
        Key.MoveEnd -> return byteArrayOf(0x1B, 0x5B, 0x46)
        Key.Insert -> return byteArrayOf(0x1B, 0x5B, 0x32, 0x7E)
        Key.F1 -> return byteArrayOf(0x1B, 0x4F, 0x50)
        Key.F2 -> return byteArrayOf(0x1B, 0x4F, 0x51)
        Key.F3 -> return byteArrayOf(0x1B, 0x4F, 0x52)
        Key.F4 -> return byteArrayOf(0x1B, 0x4F, 0x53)
        Key.Spacebar -> return " ".toByteArray(Charsets.UTF_8)
        else -> {}
    }

    // Ctrl+字母 (A-Z)
    if (k in letterKeys && ctrl) {
        val code = ctrlCodeFor(k)
        if (code != null) return byteArrayOf(code)
    }

    // 硬件键盘普通字母 (a-z, A-Z)
    if (k in letterKeys && !ctrl) {
        val char = charFor(k)
        if (char != null) {
            return if (shift) char.uppercaseChar().toString().toByteArray(Charsets.UTF_8)
            else char.toString().toByteArray(Charsets.UTF_8)
        }
    }

    // 数字 + Shift 符号
    return when (k) {
        Key.Zero -> if (shift) ")".toByteArray(Charsets.UTF_8) else "0".toByteArray(Charsets.UTF_8)
        Key.One -> if (shift) "!".toByteArray(Charsets.UTF_8) else "1".toByteArray(Charsets.UTF_8)
        Key.Two -> if (shift) "@".toByteArray(Charsets.UTF_8) else "2".toByteArray(Charsets.UTF_8)
        Key.Three -> if (shift) "#".toByteArray(Charsets.UTF_8) else "3".toByteArray(Charsets.UTF_8)
        Key.Four -> if (shift) "$".toByteArray(Charsets.UTF_8) else "4".toByteArray(Charsets.UTF_8)
        Key.Five -> if (shift) "%".toByteArray(Charsets.UTF_8) else "5".toByteArray(Charsets.UTF_8)
        Key.Six -> if (shift) "^".toByteArray(Charsets.UTF_8) else "6".toByteArray(Charsets.UTF_8)
        Key.Seven -> if (shift) "&".toByteArray(Charsets.UTF_8) else "7".toByteArray(Charsets.UTF_8)
        Key.Eight -> if (shift) "*".toByteArray(Charsets.UTF_8) else "8".toByteArray(Charsets.UTF_8)
        Key.Nine -> if (shift) "(".toByteArray(Charsets.UTF_8) else "9".toByteArray(Charsets.UTF_8)
        Key.Minus -> if (shift) "_".toByteArray(Charsets.UTF_8) else "-".toByteArray(Charsets.UTF_8)
        Key.Equals -> if (shift) "+".toByteArray(Charsets.UTF_8) else "=".toByteArray(Charsets.UTF_8)
        Key.LeftBracket -> if (shift) "{".toByteArray(Charsets.UTF_8) else "[".toByteArray(Charsets.UTF_8)
        Key.RightBracket -> if (shift) "}".toByteArray(Charsets.UTF_8) else "]".toByteArray(Charsets.UTF_8)
        Key.Backslash -> if (shift) "|".toByteArray(Charsets.UTF_8) else "\\".toByteArray(Charsets.UTF_8)
        Key.Semicolon -> if (shift) ":".toByteArray(Charsets.UTF_8) else ";".toByteArray(Charsets.UTF_8)
        Key.Apostrophe -> if (shift) "\"".toByteArray(Charsets.UTF_8) else "'".toByteArray(Charsets.UTF_8)
        Key.Comma -> if (shift) "<".toByteArray(Charsets.UTF_8) else ",".toByteArray(Charsets.UTF_8)
        Key.Period -> if (shift) ">".toByteArray(Charsets.UTF_8) else ".".toByteArray(Charsets.UTF_8)
        Key.Slash -> if (shift) "?".toByteArray(Charsets.UTF_8) else "/".toByteArray(Charsets.UTF_8)
        else -> null
    }
}

// ========== Key helper functions ==========

private val letterKeys: Set<Key> = setOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H,
    Key.I, Key.J, Key.K, Key.L, Key.M, Key.N, Key.O, Key.P,
    Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X,
    Key.Y, Key.Z
)

private fun ctrlCodeFor(key: Key): Byte? {
    return when (key) {
        Key.A -> 0x01; Key.B -> 0x02; Key.C -> 0x03; Key.D -> 0x04
        Key.E -> 0x05; Key.F -> 0x06; Key.G -> 0x07; Key.H -> 0x08
        Key.I -> 0x09; Key.J -> 0x0A; Key.K -> 0x0B; Key.L -> 0x0C
        Key.M -> 0x0D; Key.N -> 0x0E; Key.O -> 0x0F; Key.P -> 0x10
        Key.Q -> 0x11; Key.R -> 0x12; Key.S -> 0x13; Key.T -> 0x14
        Key.U -> 0x15; Key.V -> 0x16; Key.W -> 0x17; Key.X -> 0x18
        Key.Y -> 0x19; Key.Z -> 0x1A
        else -> null
    }
}

private fun charFor(key: Key): Char? {
    return when (key) {
        Key.A -> 'a'; Key.B -> 'b'; Key.C -> 'c'; Key.D -> 'd'
        Key.E -> 'e'; Key.F -> 'f'; Key.G -> 'g'; Key.H -> 'h'
        Key.I -> 'i'; Key.J -> 'j'; Key.K -> 'k'; Key.L -> 'l'
        Key.M -> 'm'; Key.N -> 'n'; Key.O -> 'o'; Key.P -> 'p'
        Key.Q -> 'q'; Key.R -> 'r'; Key.S -> 's'; Key.T -> 't'
        Key.U -> 'u'; Key.V -> 'v'; Key.W -> 'w'; Key.X -> 'x'
        Key.Y -> 'y'; Key.Z -> 'z'
        else -> null
    }
}
