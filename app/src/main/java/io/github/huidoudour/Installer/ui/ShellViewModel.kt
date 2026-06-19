package io.github.huidoudour.Installer.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.huidoudour.Installer.util.ShellExecutor
import io.github.huidoudour.Installer.util.TerminalEmulator
import java.text.SimpleDateFormat
import java.util.Locale

class ShellViewModel(application: Application) : AndroidViewModel(application) {

    override fun onCleared() {
        super.onCleared()
        ShellExecutor.destroyPtySession()
    }

    private val context: Context get() = getApplication()

    // ====== 终端模拟器 ======
    val terminal: TerminalEmulator = TerminalEmulator(24, 80)

    var ptyMode by mutableStateOf(false)
        private set
    var ptySupported by mutableStateOf(true)
        private set

    /** Shizuku pipe 模式需要本地回显，PTY 模式自带回显 */
    val needLocalEcho: Boolean
        get() = ShellExecutor.isShizukuAvailable()
    var ptyRowCount by mutableIntStateOf(24)
        private set
    var ptyColCount by mutableIntStateOf(80)
        private set

    // 命令历史索引 (用于上下键导航)
    private var historyIndex = -1
    var currentInputLine by mutableStateOf("")
        private set

    init {
        val isShizuku = ShellExecutor.isShizukuAvailable()
        val welcome = buildString {
            append("Type help for command list\n")
        }
        terminal.feed(welcome.toByteArray(), welcome.toByteArray().size)

        // 尝试启动 PTY 会话
        initPtySession()
    }

    private fun initPtySession() {
        val session = ShellExecutor.startTerminalSession(
            callback = object : ShellExecutor.ExecuteCallback {
                override fun onOutput(line: String) {
                    // PTY 输出直接喂入终端模拟器 (已包含 \n, 不额外追加)
                    terminal.feed(line.toByteArray(Charsets.UTF_8), line.toByteArray(Charsets.UTF_8).size)
                }
                override fun onError(error: String) {
                    terminal.feed(error.toByteArray(Charsets.UTF_8), error.toByteArray(Charsets.UTF_8).size)
                }
                override fun onComplete(exitCode: Int) {}
            },
            rows = ptyRowCount,
            cols = ptyColCount
        )
        if (session != null) {
            ptyMode = true
            ptySupported = true
            val mode = if (ShellExecutor.isShizukuAvailable()) "Shizuku" else "PTY"
            val msg = "[*] $mode terminal mode enabled\n"
            terminal.feed(msg.toByteArray(Charsets.UTF_8), msg.toByteArray(Charsets.UTF_8).size)
        } else {
            ptyMode = false
            ptySupported = false
            val msg = "[!] Terminal unavailable\n"
            terminal.feed(msg.toByteArray(Charsets.UTF_8), msg.toByteArray(Charsets.UTF_8).size)
        }
    }

    // ========== 键盘/命令输入 ==========

    /**
     * 发送键盘输入到 PTY 会话
     * 无论 ptyMode 状态都尝试发送, PTY 断开时自动重连
     */
    fun sendKeyInput(bytes: ByteArray) {
        val sent = ShellExecutor.executePtyCommandRaw(bytes)
        // 如果 PTY 会话已断开, 尝试重建
        if (!sent && ptySupported) {
            initPtySession()
            ShellExecutor.executePtyCommandRaw(bytes)
        }
    }

    /**
     * 发送特殊功能键序列到终端
     */
    fun sendSpecialKey(sequence: ByteArray) {
        val sent = ShellExecutor.executePtyCommandRaw(sequence)
        if (!sent && ptySupported) {
            initPtySession()
            ShellExecutor.executePtyCommandRaw(sequence)
        }
    }

    /**
     * 调整终端尺寸
     */
    fun setTerminalSize(rows: Int, cols: Int) {
        ptyRowCount = rows
        ptyColCount = cols
        terminal.resize(rows, cols)
        if (ptyMode) {
            ShellExecutor.setPtyWindowSize(rows, cols)
        }
    }

    /**
     * 清屏 (Ctrl+L)
     */
    fun clearScreen() {
        terminal.clearScreen()
        // 发送 Ctrl+L 到 PTY
        sendSpecialKey(byteArrayOf(0x0C))
    }

    /**
     * 发送 Ctrl+C 中断
     */
    fun sendCtrlC() {
        sendSpecialKey(byteArrayOf(0x03))
    }

    /**
     * 发送 Ctrl+D (EOF)
     */
    fun sendCtrlD() {
        sendSpecialKey(byteArrayOf(0x04))
    }

    /**
     * 发送 Escape
     */
    fun sendEscape() {
        sendSpecialKey(byteArrayOf(0x1B))
    }

    /**
     * 发送 Tab
     */
    fun sendTab() {
        sendSpecialKey(byteArrayOf(0x09))
    }

    /**
     * 复制终端内容到剪贴板
     */
    fun copyOutput() {
        val text = terminal.getAllText()
        ShellExecutor.copyToClipboard(context, text)
    }

    /**
     * 保存终端输出到文件
     */
    fun saveOutput(context: android.content.Context): Result<java.io.File> {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())
            val fileName = "terminal_output_$timestamp.txt"
            val outputFile = java.io.File(context.externalCacheDir, fileName)
            java.io.FileWriter(outputFile).use { writer ->
                writer.write(terminal.getAllText())
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
