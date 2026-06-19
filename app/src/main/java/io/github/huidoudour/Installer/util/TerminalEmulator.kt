package io.github.huidoudour.Installer.util

import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * 终端模拟核心引擎
 *
 * 实现 ANSI X3.64 / ECMA-48 转义序列解析、屏幕缓冲区管理、滚动缓冲区。
 * 参考 Termux-app 的 terminal-emulator 模块设计思路。
 */
class TerminalEmulator(
    initialRows: Int = 24,
    initialCols: Int = 80
) {

    companion object {
        private const val TAG = "TerminalEmulator"

        // ANSI 颜色
        private val ANSI_COLORS = intArrayOf(
            0x000000, // Black
            0xCD0000, // Red
            0x00CD00, // Green
            0xCDCD00, // Yellow
            0x0000EE, // Blue
            0xCD00CD, // Magenta
            0x00CDCD, // Cyan
            0xE5E5E5  // White
        )

        private val ANSI_BRIGHT_COLORS = intArrayOf(
            0x7F7F7F, // Bright Black (Gray)
            0xFF0000, // Bright Red
            0x00FF00, // Bright Green
            0xFFFF00, // Bright Yellow
            0x5C5CFF, // Bright Blue
            0xFF00FF, // Bright Magenta
            0x00FFFF, // Bright Cyan
            0xFFFFFF  // Bright White
        )

        const val DEFAULT_FG = 0xE0E0E0
        const val DEFAULT_BG = 0x1E1E1E
    }

    /** 终端字符单元 */
    data class Cell(
        val char: Char = ' ',
        val fg: Int = DEFAULT_FG,
        val bg: Int = DEFAULT_BG,
        val bold: Boolean = false
    )

    /** 屏幕尺寸变化监听 */
    var onSizeChanged: ((rows: Int, cols: Int) -> Unit)? = null

    /** 屏幕更新通知 */
    var onScreenUpdated: (() -> Unit)? = null

    var rows: Int = initialRows
        private set
    var cols: Int = initialCols
        private set

    // 可视屏幕缓冲区
    private var screen: MutableList<MutableList<Cell>>
    // 滚动缓冲区 (行数上限)
    private val scrollbackBuffer = ArrayDeque<MutableList<Cell>>()
    private val maxScrollbackLines = 5000

    // 光标位置
    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set
    var cursorVisible: Boolean = true
        private set

    // 当前 SGR 状态
    private var currentFg: Int = DEFAULT_FG
    private var currentBg: Int = DEFAULT_BG
    private var currentBold: Boolean = false

    // ANSI 解析状态
    private enum class ParseState { NORMAL, ESC, CSI, CSI_PARAM, OSC }
    private var parseState = ParseState.NORMAL
    private val params = mutableListOf<Int>()
    private var paramAccum = 0
    private var hasParam = false
    private val oscBuffer = StringBuilder()

    // 滚动区域
    private var scrollTop = 0
    private var scrollBottom: Int = initialRows - 1

    // 保存的光标位置
    private var savedCursorRow = 0
    private var savedCursorCol = 0

    // Tab 停止位 (每8列)
    private val tabStops: BooleanArray

    // UTF-8 解码状态
    private var utf8Lead = 0
    private var utf8ContBytes = 0
    private val utf8Buf = ByteArray(3)
    private var utf8BufIdx = 0

    init {
        screen = MutableList(initialRows) { MutableList(initialCols) { Cell() } }
        tabStops = BooleanArray(initialCols) { it % 8 == 0 }
        scrollBottom = initialRows - 1
        initTabStops()
    }

    private fun initTabStops() {
        for (i in tabStops.indices) {
            tabStops[i] = i % 8 == 0
        }
    }

    // ==================== PTY 数据喂入 ====================

    /** 从 PTY 读取的数据喂入终端模拟器 */
    fun feed(data: ByteArray, length: Int) {
        for (i in 0 until length) {
            feedByte(data[i].toInt() and 0xFF)
        }
    }

    /** 喂入单个字节 */
    private fun feedByte(b: Int) {
        when (parseState) {
            ParseState.NORMAL -> handleNormal(b)
            ParseState.ESC -> handleESC(b)
            ParseState.CSI -> handleCSI(b)
            ParseState.CSI_PARAM -> handleCSIParam(b)
            ParseState.OSC -> handleOSC(b)
        }
    }

    // ==================== 普通字符处理 ====================

    private fun handleNormal(b: Int) {
        // UTF-8 续延字节处理（处于多字节序列中时）
        if (utf8ContBytes > 0) {
            if (b in 0x80..0xBF) {
                utf8Buf[utf8BufIdx++] = b.toByte()
                if (--utf8ContBytes == 0) {
                    val cp = decodeUtf8(utf8Lead, utf8Buf, utf8BufIdx)
                    utf8Reset()
                    if (cp >= 0) putChar(cp.toChar())
                }
                return
            }
            // 无效续延，丢弃不完整序列后按普通字节处理
            utf8Reset()
        }

        // UTF-8 引导字节
        when (b) {
            in 0xC2..0xDF -> { utf8Start(b, 1); return }
            in 0xE0..0xEF -> { utf8Start(b, 2); return }
            in 0xF0..0xF4 -> { utf8Start(b, 3); return }
        }

        // 标准字节处理（ASCII 和控制字符）
        when (b) {
            0x07 -> {} // BEL
            0x08 -> backspace()
            0x09 -> tab()
            0x0A, 0x0B, 0x0C -> lineFeed()
            0x0D -> carriageReturn()
            0x1B -> parseState = ParseState.ESC
            in 0x20..0x7E -> putChar(b.toChar())
            else -> {} // 忽略无效字节
        }
    }

    // ==================== UTF-8 解码辅助 ====================

    private fun utf8Start(lead: Int, contCount: Int) {
        utf8Lead = lead
        utf8ContBytes = contCount
        utf8BufIdx = 0
    }

    private fun utf8Continue(b: Int) {
        // 已在 handleNormal 中处理，此方法保留以备扩展
    }

    private fun utf8Reset() {
        utf8ContBytes = 0
        utf8BufIdx = 0
    }

    /** 将 UTF-8 字节序列解码为 Unicode 码点 */
    private fun decodeUtf8(lead: Int, buf: ByteArray, len: Int): Int {
        return when (len) {
            1 -> ((lead and 0x1F) shl 6) or (buf[0].toInt() and 0x3F)
            2 -> ((lead and 0x0F) shl 12) or ((buf[0].toInt() and 0x3F) shl 6) or (buf[1].toInt() and 0x3F)
            3 -> ((lead and 0x07) shl 18) or ((buf[0].toInt() and 0x3F) shl 12) or ((buf[1].toInt() and 0x3F) shl 6) or (buf[2].toInt() and 0x3F)
            else -> -1
        }
    }

    // ==================== ESC 序列处理 ====================

    private fun handleESC(b: Int) {
        parseState = ParseState.NORMAL
        when (b) {
            '['.code -> {
                parseState = ParseState.CSI
                params.clear()
                paramAccum = 0
                hasParam = false
            }
            ']'.code -> {
                parseState = ParseState.OSC
                oscBuffer.clear()
            }
            '7'.code -> { // DECSC - 保存光标
                savedCursorRow = cursorRow
                savedCursorCol = cursorCol
            }
            '8'.code -> { // DECRC - 恢复光标
                cursorRow = min(savedCursorRow, rows - 1)
                cursorCol = min(savedCursorCol, cols - 1)
            }
            'c'.code -> { // RIS - 重置
                reset()
            }
            'D'.code -> { // IND - 索引 (换行)
                lineFeed()
            }
            'M'.code -> { // RI - 反向索引 (反向换行)
                reverseLineFeed()
            }
            '='.code -> {} // 应用键盘模式
            '>'.code -> {} // 普通键盘模式
            '('.code, ')'.code -> parseState = ParseState.NORMAL // 字符集选择 - 忽略
        }
    }

    // ==================== CSI 序列处理 ====================

    private fun handleCSI(b: Int) {
        when {
            b in 0x30..0x3F -> { // 中间字节 (数字、分号)
                parseState = ParseState.CSI_PARAM
                handleCSIParam(b)
            }
            b in 0x20..0x2F -> {} // 忽略中间字符
            b in 0x40..0x7E -> { // 最终字节
                parseState = ParseState.NORMAL
                executeCSI(b.toChar())
            }
            else -> parseState = ParseState.NORMAL
        }
    }

    private fun handleCSIParam(b: Int) {
        when {
            b == ';'.code -> {
                params.add(if (hasParam) paramAccum else 0)
                paramAccum = 0
                hasParam = false
            }
            b in 0x30..0x39 -> {
                paramAccum = paramAccum * 10 + (b - 0x30)
                hasParam = true
            }
            b in 0x40..0x7E -> { // 最终字节
                params.add(if (hasParam) paramAccum else 0)
                parseState = ParseState.NORMAL
                executeCSI(b.toChar())
            }
            else -> parseState = ParseState.NORMAL
        }
    }

    /** 解析 CSI 参数 (默认值处理) */
    private fun paramOr(index: Int, default: Int): Int {
        return params.getOrElse(index) { default }
    }

    /** 执行 CSI 命令 */
    private fun executeCSI(cmd: Char) {
        when (cmd) {
            'A' -> cursorUp(paramOr(0, 1))
            'B' -> cursorDown(paramOr(0, 1))
            'C' -> cursorForward(paramOr(0, 1))
            'D' -> cursorBack(paramOr(0, 1))
            'H', 'f' -> cursorPosition(paramOr(0, 1) - 1, paramOr(1, 1) - 1)
            'J' -> eraseInDisplay(paramOr(0, 0))
            'K' -> eraseInLine(paramOr(0, 0))
            'L' -> insertLines(paramOr(0, 1))
            'M' -> deleteLines(paramOr(0, 1))
            'P' -> deleteCharacters(paramOr(0, 1))
            '@' -> insertCharacters(paramOr(0, 1))
            'X' -> eraseCharacters(paramOr(0, 1))
            'S' -> scrollUp(paramOr(0, 1))
            'T' -> scrollDown(paramOr(0, 1))
            'm' -> applySGR()
            'r' -> setScrollRegion(paramOr(0, 1) - 1, paramOr(1, rows) - 1)
            's' -> { // 保存光标 (DEC)
                savedCursorRow = cursorRow
                savedCursorCol = cursorCol
            }
            'u' -> { // 恢复光标 (DEC)
                cursorRow = min(savedCursorRow, rows - 1)
                cursorCol = min(savedCursorCol, cols - 1)
            }
            'h', 'l' -> setDecMode(cmd == 'h')
        }
    }

    // ==================== OSC 序列处理 ====================

    private fun handleOSC(b: Int) {
        when {
            b == 0x07 || (b == 0x1B) -> { // BEL 或 ESC 终止 OSC
                parseState = ParseState.NORMAL
                if (b == 0x1B) parseState = ParseState.ESC
            }
            b == 0x5C && oscBuffer.endsWith("\u001B") -> { // ST终止符
                parseState = ParseState.NORMAL
            }
            else -> oscBuffer.append(b.toChar())
        }
    }

    // ==================== 光标操作 ====================

    private fun cursorUp(n: Int) {
        cursorRow = max(0, cursorRow - n)
    }

    private fun cursorDown(n: Int) {
        cursorRow = min(rows - 1, cursorRow + n)
    }

    private fun cursorForward(n: Int) {
        cursorCol = min(cols - 1, cursorCol + n)
    }

    private fun cursorBack(n: Int) {
        cursorCol = max(0, cursorCol - n)
    }

    private fun cursorPosition(row: Int, col: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorCol = col.coerceIn(0, cols - 1)
    }

    // ==================== 字符操作 ====================

    private fun putChar(c: Char) {
        when (c) {
            '\n' -> lineFeed()
            '\r' -> carriageReturn()
            '\t' -> tab()
            '\b' -> backspace()
            else -> {
                if (cursorCol >= cols) {
                    cursorCol = 0
                    lineFeed()
                }
                if (cursorRow >= rows) {
                    cursorRow = rows - 1
                }
                screen[cursorRow][cursorCol] = Cell(c, currentFg, currentBg, currentBold)
                cursorCol++
                notifyUpdate()
            }
        }
    }

    private fun backspace() {
        if (cursorCol > 0) cursorCol--
    }

    private fun tab() {
        var col = cursorCol + 1
        while (col < cols && !tabStops.getOrElse(col) { false }) col++
        if (col < cols) cursorCol = col
    }

    private fun carriageReturn() {
        cursorCol = 0
    }

    private fun lineFeed() {
        cursorCol = 0
        if (cursorRow == scrollBottom) {
            // 滚动一行
            scrollUp(1)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    private fun reverseLineFeed() {
        if (cursorRow == scrollTop) {
            scrollDown(1)
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    // ==================== 擦除操作 ====================

    private fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> { // 从光标擦除到屏幕末尾
                eraseLine(cursorCol, cols - 1)
                for (r in cursorRow + 1 until rows) {
                    eraseLine(r, 0, cols - 1)
                }
            }
            1 -> { // 从屏幕开头擦除到光标
                for (r in 0 until cursorRow) {
                    eraseLine(r, 0, cols - 1)
                }
                eraseLine(0, cursorCol)
            }
            2, 3 -> { // 清除全部
                for (r in 0 until rows) {
                    eraseLine(r, 0, cols - 1)
                }
            }
        }
        notifyUpdate()
    }

    private fun eraseInLine(mode: Int) {
        when (mode) {
            0 -> eraseLine(cursorCol, cols - 1)
            1 -> eraseLine(0, cursorCol)
            2 -> eraseLine(0, cols - 1)
        }
        notifyUpdate()
    }

    private fun eraseLine(colStart: Int, colEnd: Int) {
        for (c in colStart..colEnd) {
            if (c < cols) {
                screen[cursorRow][c] = Cell()
            }
        }
    }

    private fun eraseLine(row: Int, colStart: Int, colEnd: Int) {
        if (row < 0 || row >= rows) return
        for (c in colStart..colEnd) {
            if (c in 0 until cols) {
                screen[row][c] = Cell()
            }
        }
    }

    private fun eraseCharacters(n: Int) {
        val end = min(cursorCol + n - 1, cols - 1)
        for (c in cursorCol..end) {
            screen[cursorRow][c] = Cell()
        }
        notifyUpdate()
    }

    // ==================== 插入/删除 ====================

    private fun insertLines(n: Int) {
        val count = min(n, scrollBottom - cursorRow + 1)
        if (count <= 0) return
        // 将行向下移动
        for (r in scrollBottom downTo cursorRow + count) {
            for (c in 0 until cols) {
                screen[r][c] = screen[r - count][c]
            }
        }
        // 清空插入的行
        for (r in cursorRow until cursorRow + count) {
            for (c in 0 until cols) {
                screen[r][c] = Cell()
            }
        }
        notifyUpdate()
    }

    private fun deleteLines(n: Int) {
        val count = min(n, scrollBottom - cursorRow + 1)
        if (count <= 0) return
        // 将行向上移动
        for (r in cursorRow..scrollBottom - count) {
            for (c in 0 until cols) {
                screen[r][c] = screen[r + count][c]
            }
        }
        // 清空最后几行
        for (r in scrollBottom - count + 1..scrollBottom) {
            for (c in 0 until cols) {
                screen[r][c] = Cell()
            }
        }
        notifyUpdate()
    }

    private fun deleteCharacters(n: Int) {
        val count = min(n, cols - cursorCol)
        if (count <= 0) return
        for (c in cursorCol until cols - count) {
            screen[cursorRow][c] = screen[cursorRow][c + count]
        }
        for (c in cols - count until cols) {
            screen[cursorRow][c] = Cell()
        }
        notifyUpdate()
    }

    private fun insertCharacters(n: Int) {
        val count = min(n, cols - cursorCol)
        if (count <= 0) return
        for (c in cols - 1 downTo cursorCol + count) {
            screen[cursorRow][c] = screen[cursorRow][c - count]
        }
        for (c in cursorCol until cursorCol + count) {
            screen[cursorRow][c] = Cell()
        }
        notifyUpdate()
    }

    // ==================== 滚动操作 ====================

    private fun scrollUp(n: Int) {
        val count = min(n, scrollBottom - scrollTop + 1)
        if (count <= 0) return

        // 将滚动出去的行保存到 scrollback
        for (r in scrollTop until scrollTop + count) {
            val lineCopy = screen[r].toMutableList()
            scrollbackBuffer.addLast(lineCopy)
            if (scrollbackBuffer.size > maxScrollbackLines) {
                scrollbackBuffer.removeFirst()
            }
        }

        // 将下方的行上移
        for (r in scrollTop until scrollBottom - count + 1) {
            screen[r] = screen[r + count]
        }
        // 清空底部新行
        for (r in scrollBottom - count + 1..scrollBottom) {
            screen[r] = MutableList(cols) { Cell() }
        }
        notifyUpdate()
    }

    private fun scrollDown(n: Int) {
        val count = min(n, scrollBottom - scrollTop + 1)
        if (count <= 0) return

        // 将行下移
        for (r in scrollBottom downTo scrollTop + count) {
            screen[r] = screen[r - count]
        }
        // 清空顶部新行
        for (r in scrollTop until scrollTop + count) {
            screen[r] = MutableList(cols) { Cell() }
        }
        notifyUpdate()
    }

    // ==================== SGR (选择图形再现) ====================

    private fun applySGR() {
        if (params.isEmpty() || params[0] == 0) {
            // 重置
            currentFg = DEFAULT_FG
            currentBg = DEFAULT_BG
            currentBold = false
            return
        }

        var i = 0
        while (i < params.size) {
            val p = params[i]
            if (p == 0) {
                currentFg = DEFAULT_FG
                currentBg = DEFAULT_BG
                currentBold = false
            } else if (p == 1) {
                currentBold = true
            } else if (p == 22) {
                currentBold = false
            } else if (p == 7 || p == 27) {
                // 反转/取消反转 - 简化忽略
            } else if (p in 30..37) {
                val index = p - 30
                currentFg = if (currentBold) ANSI_BRIGHT_COLORS[index] else ANSI_COLORS[index]
            } else if (p == 38) {
                i++
                if (i < params.size && params[i] == 5) {
                    i++
                    if (i < params.size) {
                        currentFg = getColor256(params[i])
                    }
                }
            } else if (p == 39) {
                currentFg = DEFAULT_FG
            } else if (p in 40..47) {
                val index = p - 40
                currentBg = ANSI_COLORS[index]
            } else if (p == 48) {
                i++
                if (i < params.size && params[i] == 5) {
                    i++
                    if (i < params.size) {
                        currentBg = getColor256(params[i])
                    }
                }
            } else if (p == 49) {
                currentBg = DEFAULT_BG
            } else if (p in 90..97) {
                val index = p - 90
                currentFg = ANSI_BRIGHT_COLORS[index]
            } else if (p in 100..107) {
                val index = p - 100
                currentBg = ANSI_COLORS[index]
            }
            i++
        }
    }

    /** 256色映射 */
    private fun getColor256(index: Int): Int {
        return when {
            index < 16 -> {
                if (index < 8) ANSI_COLORS[index]
                else ANSI_BRIGHT_COLORS[index - 8]
            }
            index < 232 -> {
                // 6x6x6 立方体
                val r = (index - 16) / 36
                val g = ((index - 16) / 6) % 6
                val b = (index - 16) % 6
                val rr = if (r == 0) 0 else r * 40 + 55
                val gg = if (g == 0) 0 else g * 40 + 55
                val bb = if (b == 0) 0 else b * 40 + 55
                (rr shl 16) or (gg shl 8) or bb
            }
            else -> {
                // 灰度
                val gray = (index - 232) * 10 + 8
                (gray shl 16) or (gray shl 8) or gray
            }
        }
    }

    // ==================== DEC 模式设置 ====================

    private fun setDecMode(set: Boolean) {
        for (p in params) {
            when (p) {
                25 -> cursorVisible = set // 显示/隐藏光标
                7 -> {} // 自动换行模式 - 忽略
            }
        }
    }

    // ==================== 滚动区域 ====================

    private fun setScrollRegion(top: Int, bottom: Int) {
        scrollTop = max(0, top)
        scrollBottom = min(rows - 1, bottom)
        cursorRow = 0
        cursorCol = 0
    }

    // ==================== 公开 API ====================

    /** 获取当前可视屏幕 */
    fun getScreen(): List<List<Cell>> {
        return screen
    }

    /** 获取指定行 */
    fun getRow(row: Int): List<Cell> {
        return if (row in 0 until rows) screen[row] else emptyList()
    }

    /** 获取光标所在行的文本 (用于复制) */
    fun getCursorLineText(): String {
        val sb = StringBuilder()
        for (c in 0 until cols) {
            val cell = screen[cursorRow][c]
            if (cell.char != ' ') sb.append(cell.char)
        }
        return sb.toString().trimEnd()
    }

    /** 获取所有文本 (含滚动缓冲区) */
    fun getAllText(): String {
        val sb = StringBuilder()
        for (line in scrollbackBuffer) {
            for (cell in line) {
                sb.append(cell.char)
            }
            sb.append('\n')
        }
        for (row in screen) {
            for (cell in row) {
                sb.append(cell.char)
            }
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    /** 调整终端尺寸 */
    fun resize(newRows: Int, newCols: Int) {
        if (newRows == rows && newCols == cols) return

        val oldRows = rows
        val oldScreen = screen

        rows = newRows
        cols = newCols

        // 重新初始化 tab 停止位
        if (newCols > tabStops.size) {
            // 保持现有的
        }

        // 创建新屏幕
        val newScreen = MutableList(newRows) { r ->
            MutableList(newCols) { c ->
                if (r < oldRows && c < oldScreen[r].size) {
                    oldScreen[r][c]
                } else {
                    Cell()
                }
            }
        }

        screen = newScreen

        // 调整滚动区域
        scrollBottom = newRows - 1
        cursorRow = min(cursorRow, newRows - 1)
        cursorCol = min(cursorCol, newCols - 1)

        onSizeChanged?.invoke(newRows, newCols)
        notifyUpdate()
    }

    /** 重置终端 */
    fun reset() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                screen[r][c] = Cell()
            }
        }
        cursorRow = 0
        cursorCol = 0
        cursorVisible = true
        currentFg = DEFAULT_FG
        currentBg = DEFAULT_BG
        currentBold = false
        scrollTop = 0
        scrollBottom = rows - 1
        savedCursorRow = 0
        savedCursorCol = 0
        scrollbackBuffer.clear()
        utf8Reset()
        parseState = ParseState.NORMAL
        notifyUpdate()
    }

    /** 清屏 (类似 Ctrl+L) */
    fun clearScreen() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                screen[r][c] = Cell()
            }
        }
        cursorRow = 0
        cursorCol = 0
        notifyUpdate()
    }

    private fun notifyUpdate() {
        onScreenUpdated?.invoke()
    }
}
