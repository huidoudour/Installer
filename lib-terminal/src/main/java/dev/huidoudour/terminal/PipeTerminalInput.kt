package dev.huidoudour.terminal

/** A pipe has no tty line discipline: edit locally and write only completed lines. */
internal class PipeTerminalInput(
    private val terminal: TerminalEmulator,
    private val write: (ByteArray) -> Unit
) {
    private var line = ""
    private var caret = 0
    private var anchorRow: Int? = null
    private var anchorCol = 0
    private var escape = ""
    private val history = mutableListOf<String>()
    private var historyIndex = 0
    private var historyDraft = ""

    fun reset() {
        line = ""
        caret = 0
        anchorRow = null
        escape = ""
        historyIndex = history.size
    }

    fun accept(bytes: ByteArray) {
        // Text commits arrive as complete UTF-8 strings; key sequences use ASCII bytes.
        val text = bytes.toString(Charsets.UTF_8).replace("\r\n", "\n")
        var start = 0
        while (start < text.length) {
            val c = text[start]
            if (escape.isNotEmpty()) {
                if (escape == "\u001B" && c != '[' && c != 'O') {
                    escape = ""
                    continue // A lone Escape must not swallow the next printable character.
                }
                escape += c
                start++
                if (escape.length > 2 && c in '@'..'~') {
                    handleEscape(escape)
                    escape = ""
                }
                continue
            }
            if (c >= ' ' && c != '\u007F') {
                var end = start + 1
                while (end < text.length && text[end] >= ' ' && text[end] != '\u007F') end++
                line = line.substring(0, caret) + text.substring(start, end) + line.substring(caret)
                caret += end - start
                redraw()
                start = end
                continue
            }
            start++
            when (c) {
                '\u001B' -> escape = c.toString()
                '\r', '\n' -> commit()
                '\b', '\u007F' -> if (caret > 0) {
                    val previous = Character.offsetByCodePoints(line, caret, -1)
                    line = line.removeRange(previous, caret)
                    caret = previous
                    redraw()
                }
                '\t' -> { line = line.substring(0, caret) + c + line.substring(caret); caret++; redraw() }
                '\u0001' -> { caret = 0; redraw() }
                '\u0005' -> { caret = line.length; redraw() }
                '\u0002' -> move(-1)
                '\u0006' -> move(1)
                '\u000B' -> { line = line.substring(0, caret); redraw() }
                '\u0015' -> { line = line.substring(caret); caret = 0; redraw() }
                '\u0017' -> {
                    var previous = caret
                    while (previous > 0 && line[previous - 1].isWhitespace()) previous--
                    while (previous > 0 && !line[previous - 1].isWhitespace()) {
                        previous = Character.offsetByCodePoints(line, previous, -1)
                    }
                    line = line.removeRange(previous, caret); caret = previous; redraw()
                }
                '\u0010' -> recall(-1)
                '\u000E' -> recall(1)
                '\u000C' -> { terminal.clearScreen(); anchorRow = null; redraw() }
                '\u0003' -> {
                    line = ""; caret = 0; redraw()
                    echo("^C\n")
                    reset()
                    // A literal ETX in a pipe would become part of the next shell command.
                    write(byteArrayOf(0x0A))
                }
                '\u0004' -> if (line.isNotEmpty()) commit()
            }
        }
    }

    private fun handleEscape(sequence: String) {
        when (sequence) {
            "\u001B[D", "\u001BOD" -> move(-1)
            "\u001B[C", "\u001BOC" -> move(1)
            "\u001B[A", "\u001BOA" -> recall(-1)
            "\u001B[B", "\u001BOB" -> recall(1)
            "\u001B[H", "\u001BOH", "\u001B[1~", "\u001B[7~" -> { caret = 0; redraw() }
            "\u001B[F", "\u001BOF", "\u001B[4~", "\u001B[8~" -> { caret = line.length; redraw() }
            "\u001B[3~" -> if (caret < line.length) {
                line = line.removeRange(caret, Character.offsetByCodePoints(line, caret, 1))
                redraw()
            }
        }
    }

    private fun move(delta: Int) {
        if (delta < 0 && caret > 0 || delta > 0 && caret < line.length) {
            caret = Character.offsetByCodePoints(line, caret, delta)
            redraw()
        }
    }

    private fun recall(delta: Int) {
        if (history.isEmpty()) return
        if (historyIndex == history.size) historyDraft = line
        historyIndex = (historyIndex + delta).coerceIn(0, history.size)
        line = if (historyIndex == history.size) historyDraft else history[historyIndex]
        caret = line.length
        redraw()
    }

    private fun commit() {
        caret = line.length
        if (anchorRow != null) redraw()
        echo("\n")
        val command = (line + "\n").toByteArray(Charsets.UTF_8)
        if (line.isNotEmpty() && history.lastOrNull() != line) {
            history.add(line)
            if (history.size > 100) history.removeAt(0)
        }
        reset()
        write(command)
    }

    private fun echo(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        terminal.feed(bytes, bytes.size)
    }

    private fun position(end: Int): Pair<Int, Int> {
        var row = anchorRow ?: terminal.cursorRow
        var col = anchorCol
        var index = 0
        while (index < end) {
            val cp = Character.codePointAt(line, index)
            val width = terminalCharacterWidth(cp).coerceAtMost(terminal.cols)
            if (cp == '\t'.code) {
                val nextTab = (col / 8 + 1) * 8
                if (nextTab < terminal.cols) col = nextTab
            } else if (width > 0) {
                if (col + width > terminal.cols) { row++; col = 0 }
                col += width
            }
            index += Character.charCount(cp)
        }
        return Pair(row, col)
    }

    private fun redraw() {
        if (anchorRow == null) { anchorRow = terminal.cursorRow; anchorCol = terminal.cursorCol }
        anchorRow = anchorRow!!.coerceIn(0, terminal.rows - 1)
        echo("\u001B[${anchorRow!! + 1};${anchorCol + 1}H\u001B[J")
        echo(line)
        val expectedEnd = position(line.length).first
        val scrolled = (expectedEnd - terminal.cursorRow).coerceAtLeast(0)
        anchorRow = (anchorRow!! - scrolled).coerceAtLeast(0)
        if (caret != line.length) {
            val (row, col) = position(caret)
            echo("\u001B[${row + 1};${col + 1}H")
        }
    }
}