package dev.huidoudour.terminal

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText

/** Native editable IME buffer, hidden behind the terminal Canvas. */
internal class TerminalInputView(context: Context) : EditText(context) {
    var onInput: (ByteArray) -> Unit = {}
    private var clearing = false
    private var inputEditDepth = 0

    init {
        background = null
        setPadding(0, 0, 0, 0)
        gravity = Gravity.TOP or Gravity.START
        setTextColor(Color.TRANSPARENT)
        highlightColor = Color.TRANSPARENT
        isCursorVisible = false
        includeFontPadding = false
        setSingleLine(true)
        textSize = 1f
        isLongClickable = false
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_FORCE_ASCII
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { flushCommittedText() }
        })
        setOnEditorActionListener { _, action, _ ->
            if (action in listOf(EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_NEXT)) {
                finishComposition()
                onInput(byteArrayOf(0x0D))
                true
            } else false
        }
    }

    private fun flushCommittedText() {
        if (clearing || inputEditDepth > 0) return
        val value = editableText
        if (BaseInputConnection.getComposingSpanStart(value) >= 0) return
        if (value.isNotEmpty()) {
            val bytes = value.toString().toByteArray(Charsets.UTF_8)
            clearing = true
            try { value.clear() } finally { clearing = false }
            onInput(bytes)
        }
    }

    private fun finishComposition() {
        BaseInputConnection.removeComposingSpans(editableText)
        flushCommittedText()
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, false) {
            override fun beginBatchEdit(): Boolean {
                inputEditDepth++
                return super.beginBatchEdit()
            }
            override fun endBatchEdit(): Boolean {
                val result = super.endBatchEdit()
                inputEditDepth = (inputEditDepth - 1).coerceAtLeast(0)
                flushCommittedText()
                return result
            }
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean =
                editInput { super.setComposingText(text, newCursorPosition) }
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
                editInput { super.commitText(text, newCursorPosition) }
            override fun finishComposingText(): Boolean = editInput { super.finishComposingText() }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (editableText.isNotEmpty()) return editInput { super.deleteSurroundingText(beforeLength, afterLength) }
                repeat(beforeLength) { onInput(byteArrayOf(0x7F)) }
                repeat(afterLength) { onInput(byteArrayOf(0x1B, 0x5B, 0x33, 0x7E)) }
                return true
            }
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (editableText.isNotEmpty()) return editInput { super.deleteSurroundingTextInCodePoints(beforeLength, afterLength) }
                return deleteSurroundingText(beforeLength, afterLength)
            }
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (handleKey(event)) return true
                return super.sendKeyEvent(event)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (handleKey(event)) true else editInput { super.dispatchKeyEvent(event) }

    private inline fun editInput(block: () -> Boolean): Boolean {
        inputEditDepth++
        return try { block() } finally {
            inputEditDepth--
            flushCommittedText()
        }
    }

    private fun handleKey(event: KeyEvent): Boolean {
        val bytes = when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> byteArrayOf(0x0D)
            KeyEvent.KEYCODE_DEL -> if (editableText.isEmpty()) byteArrayOf(0x7F) else null
            KeyEvent.KEYCODE_FORWARD_DEL -> if (editableText.isEmpty()) "\u001B[3~".toByteArray() else null
            KeyEvent.KEYCODE_TAB -> byteArrayOf(0x09)
            KeyEvent.KEYCODE_ESCAPE -> byteArrayOf(0x1B)
            KeyEvent.KEYCODE_DPAD_UP -> "\u001B[A".toByteArray()
            KeyEvent.KEYCODE_DPAD_DOWN -> "\u001B[B".toByteArray()
            KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001B[C".toByteArray()
            KeyEvent.KEYCODE_DPAD_LEFT -> "\u001B[D".toByteArray()
            KeyEvent.KEYCODE_PAGE_UP -> "\u001B[5~".toByteArray()
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001B[6~".toByteArray()
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001B[H".toByteArray()
            KeyEvent.KEYCODE_MOVE_END -> "\u001B[F".toByteArray()
            KeyEvent.KEYCODE_INSERT -> "\u001B[2~".toByteArray()
            KeyEvent.KEYCODE_F1 -> "\u001BOP".toByteArray()
            KeyEvent.KEYCODE_F2 -> "\u001BOQ".toByteArray()
            KeyEvent.KEYCODE_F3 -> "\u001BOR".toByteArray()
            KeyEvent.KEYCODE_F4 -> "\u001BOS".toByteArray()
            else -> if (event.isCtrlPressed && event.keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
                byteArrayOf((event.keyCode - KeyEvent.KEYCODE_A + 1).toByte())
            } else null
        } ?: return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode != KeyEvent.KEYCODE_DEL && event.keyCode != KeyEvent.KEYCODE_FORWARD_DEL) finishComposition()
            onInput(bytes)
        }
        // Consume the matching key-up too, so Enter cannot also trigger editor actions.
        return true
    }
}