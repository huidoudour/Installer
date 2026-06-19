package io.github.huidoudour.Installer.ui

import android.graphics.Typeface
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.huidoudour.Installer.util.TerminalEmulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Compose 终端视图 - 渲染 TerminalEmulator 的字符网格
 *
 * 双通道输入:
 * 1. 隐藏 BasicTextField - 捕获 IME (软键盘) 输入
 * 2. onKeyEvent - 捕获硬件键盘特殊键 (方向键、Ctrl组合等)
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

    // 页面加载时自动获取焦点并弹出键盘
    LaunchedEffect(Unit) {
        textFieldFocusRequester.requestFocus()
        keyboardController?.show()
    }

    // 隐藏 TextField 输入缓冲区
    var imeBuffer by remember { mutableStateOf("") }

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

    val currentScreen = terminal.getScreen()
    val curRow = terminal.cursorRow
    val curCol = terminal.cursorCol
    val isCursorVis = showCursor && terminal.cursorVisible
    val termRows = terminal.rows
    val termCols = terminal.cols

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFF1E1E1E))
            .onFocusChanged {
                if (it.isFocused) {
                    textFieldFocusRequester.requestFocus()
                    keyboardController?.show()
                }
            }
            .onKeyEvent { event ->
                // 硬件键盘特殊键 (方向键、Ctrl组合、F功能键等)
                if (event.type == KeyEventType.KeyDown) {
                    val bytes = keyEventToBytes(event)
                    if (bytes != null) {
                        onKeyInput(bytes)
                        if (needLocalEcho) {
                            // 本地回显 Enter→LF，普通字母回显自身
                            when (event.key) {
                                Key.Enter -> terminal.feed(byteArrayOf(0x0A), 1)
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
        // ====== 隐藏的 TextField 用于捕获软键盘输入 ======
        BasicTextField(
            value = imeBuffer,
            onValueChange = { newValue ->
                if (newValue.length > imeBuffer.length) {
                    // 新输入的字符
                    val typed = newValue.substring(imeBuffer.length)
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
                            }
                            else -> {
                                val bytes = char.toString().toByteArray(Charsets.UTF_8)
                                onKeyInput(bytes)
                                if (needLocalEcho) terminal.feed(bytes, bytes.size)
                            }
                        }
                    }
                }
                // 清空缓冲区以接收下一批输入
                imeBuffer = ""
            },
            modifier = Modifier
                .size(1.dp)            // 不可见 (但必须存在才能接收 IME)
                .focusRequester(textFieldFocusRequester),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 1.sp,        // 极小字体确保不可见
                color = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.None,
                keyboardType = KeyboardType.Ascii
            )
        )

        // ====== 终端 Canvas 渲染 ======
        val paint = remember {
            android.text.TextPaint().apply {
                isAntiAlias = true
                typeface = Typeface.MONOSPACE
            }
        }

        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val cellWidth = size.width / termCols
            val cellHeight = size.height / termRows

            paint.textSize = cellHeight * 0.8f
            val baseline = cellHeight * 0.78f

            for (row in 0 until termRows) {
                val rowCells = currentScreen[row]
                val y = row * cellHeight

                var col = 0
                while (col < termCols) {
                    val cell = rowCells[col]
                    val startCol = col
                    val cellBg = cell.bg
                    val cellFg = cell.fg
                    val cellBold = cell.bold

                    while (col < termCols) {
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
                        paint.color = cellFg or (0xFF shl 24)
                        paint.isFakeBoldText = cellBold
                        drawContext.canvas.nativeCanvas.drawText(
                            text, startCol * cellWidth, y + baseline, paint
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
                    if (curCol < termCols) {
                        val cursorCell = rowCells[curCol]
                        paint.color = android.graphics.Color.BLACK
                        paint.isFakeBoldText = cursorCell.bold
                        drawContext.canvas.nativeCanvas.drawText(
                            cursorCell.char.toString(), cursorX, y + baseline, paint
                        )
                    }
                }
            }

            @Suppress("UNUSED_EXPRESSION")
            screenVersion
        }

        // ====== 点击区域 - 请求 TextField 焦点 (弹出软键盘) ======
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    textFieldFocusRequester.requestFocus()
                    keyboardController?.show()
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
