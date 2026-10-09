package dev.huidoudour.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Owns the font and grid used for text, cursor, scrolling and terminal sizing. */
internal class TerminalRenderer(context: Context, fontSizePx: Float) {
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        // System monospace can be replaced by OEM/theme fonts with variable advances.
        typeface = requireNotNull(ResourcesCompat.getFont(context, R.font.terminal_monospace))
        textSize = fontSizePx
    }
    val cellWidth = textPaint.measureText("M").coerceAtLeast(1f)
    private val metrics = textPaint.fontMetrics
    val cellHeight = max(fontSizePx * 1.25f, ceil(metrics.descent - metrics.ascent))
    private val baseline = (cellHeight - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
    private val rectPaint = Paint()

    fun columnsFor(width: Int): Int = (width / cellWidth).toInt().coerceAtLeast(1)
    fun rowsFor(height: Int): Int = (height / cellHeight).toInt().coerceAtLeast(1)

    fun draw(canvas: Canvas, width: Float, height: Float, terminal: TerminalEmulator, showCursor: Boolean) {
        canvas.drawColor(0xFF000000.toInt() or TerminalEmulator.DEFAULT_BG)
        val visibleRows = min(rowsFor(height.toInt()), terminal.rows)
        val screen = terminal.getVisibleScreen(terminal.rows)
        val scrollback = terminal.isScrollbackActive()
        for (row in 0 until min(visibleRows, screen.size)) {
            val cells = screen[row]
            val y = row * cellHeight
            val columns = min(terminal.cols, cells.size)
            var col = 0
            while (col < columns) {
                val cell = cells[col]
                val start = col
                while (col < columns && cells[col].bg == cell.bg &&
                    cells[col].fg == cell.fg && cells[col].bold == cell.bold) {
                    col++
                }
                rectPaint.color = 0xFF000000.toInt() or cell.bg
                canvas.drawRect(start * cellWidth, y, col * cellWidth, y + cellHeight, rectPaint)
                textPaint.color = 0xFF000000.toInt() or cell.fg
                textPaint.isFakeBoldText = cell.bold
                // Fixed grid positions prevent shaping/advance differences from accumulating.
                for (c in start until col) {
                    drawCell(canvas, cells[c], c * cellWidth, y)
                }
            }
            if (showCursor && terminal.cursorVisible && !scrollback && row == terminal.cursorRow) {
                // A full last cell leaves the emulator in pending-wrap state (col == cols).
                var cursorCol = terminal.cursorCol.coerceIn(0, columns - 1)
                if (cells[cursorCol].width == 0 && cursorCol > 0) cursorCol--
                val x = cursorCol * cellWidth
                rectPaint.color = 0xB2FFFFFF.toInt()
                canvas.drawRect(x, y, x + cells[cursorCol].width * cellWidth, y + cellHeight, rectPaint)
                textPaint.color = Color.BLACK
                textPaint.isFakeBoldText = cells[cursorCol].bold
                drawCell(canvas, cells[cursorCol], x, y)
            }
        }
        if (scrollback) {
            rectPaint.color = 0x80FFFFFF.toInt()
            canvas.drawRect(width - 4f, 0f, width, height, rectPaint)
        }
    }
    private fun drawCell(canvas: Canvas, cell: TerminalEmulator.Cell, x: Float, y: Float) {
        if (cell.width == 0 || cell.text == " ") return
        val availableWidth = cell.width * cellWidth
        val advance = textPaint.measureText(cell.text)
        // A themed fallback glyph can be wider than its Unicode column allocation.
        val save = canvas.save()
        canvas.clipRect(x, y, x + availableWidth, y + cellHeight)
        if (advance > availableWidth) canvas.scale(availableWidth / advance, 1f, x, y)
        canvas.drawText(cell.text, x, y + baseline, textPaint)
        canvas.restoreToCount(save)
    }
}
