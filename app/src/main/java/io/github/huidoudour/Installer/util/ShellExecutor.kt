package io.github.huidoudour.Installer.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import io.github.huidoudour.Installer.R
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Shell 终端执行助手
 */
object ShellExecutor {

    private const val TAG = "ShellExecutor"

    // ====== 传统模式 (pipe-based) ======
    private var persistentShellProcess: Process? = null
    private var persistentShellWriter: BufferedWriter? = null
    private var persistentShellStdout: BufferedReader? = null
    private var persistentShellStderr: BufferedReader? = null
    private var isShizukuSession = false
    private var currentWorkingDirectory = "/"

    // ====== PTY / Shizuku 模式 ======
    @Volatile
    private var ptySession: PtyShellSession? = null
    @Volatile
    private var shizukuSession: ShizukuShellSession? = null

    /**
     * PTY (伪终端) Shell 会话
     * 基于 TermuxBridge / libtermux_bridge.so
     * 参考: https://github.com/termux/termux-app/wiki/Termux-Libraries
     */
    class PtyShellSession(
        val pty: TermuxBridge.PtyProcess,
        private val callback: ExecuteCallback?
    ) {
        private var running = true
        private var readThread: Thread? = null

        /** 启动输出读取线程 */
        fun startReading() {
            readThread = Thread({
                val buffer = ByteArray(4096)
                while (running) {
                    try {
                        val n = pty.inputStream.readWithTimeout(buffer, 0, buffer.size, 50)
                        if (n > 0) {
                            // 实时推送数据到终端模拟器，不按行缓冲
                            val text = String(buffer, 0, n, Charsets.UTF_8)
                            callback?.onOutput(text)
                        } else if (n < 0) {
                            Log.w(TAG, "PTY read error: $n")
                            break
                        }
                    } catch (e: Exception) {
                        if (running) Log.e(TAG, "PTY read error", e)
                        break
                    }
                }
                Log.d(TAG, "PTY reading stopped")
            }, "pty-read-thread")
            readThread?.isDaemon = true
            readThread?.start()
        }

        /** 执行命令 (写入 PTY) */
        fun executeCommand(command: String) {
            try {
                pty.outputStream.write((command + "\n").toByteArray(Charsets.UTF_8))
                pty.outputStream.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write command to PTY", e)
                callback?.onError("PTY write error: ${e.message}")
            }
        }

        /** 发送 Ctrl+C 信号 */
        fun sendCtrlC() {
            try {
                // 发送 Ctrl+C (0x03) 到 PTY
                pty.outputStream.write(byteArrayOf(0x03))
                pty.outputStream.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send Ctrl+C", e)
            }
        }

        /** 设置终端尺寸 */
        fun setWindowSize(rows: Int, cols: Int) {
            TermuxBridge.setPtyWindowSize(pty.ptyFd, rows, cols)
        }

        /** 关闭会话 */
        fun close() {
            running = false
            try {
                // 发送 exit 命令
                pty.outputStream.write("exit\n".toByteArray(Charsets.UTF_8))
                pty.outputStream.flush()
            } catch (_: Exception) {}
            try {
                TermuxBridge.close(pty.ptyFd)
            } catch (_: Exception) {}
            try {
                readThread?.join(1000)
            } catch (_: Exception) {}
        }

        /** 检查会话是否存活 */
        fun isAlive(): Boolean {
            return running && TermuxBridge.isAlive(pty.pid)
        }
    }

    /**
     * Shizuku Shell 会话 (管道 I/O, shell UID 权限)
     * 使用 Shizuku.newProcess 创建特权 shell, 替代 PTY 模式
     */
    class ShizukuShellSession(
        private val process: Process,
        private val callback: ExecuteCallback?
    ) {
        private var running = true
        private var readThread: Thread? = null
        private val writer: BufferedWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
        private val errorReader: BufferedReader = BufferedReader(InputStreamReader(process.errorStream))

        fun startReading() {
            // 合并读取 stdout+stderr，确保输出串行化，避免 PS1 交错插入
            readThread = Thread({
                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stderrReader = errorReader
                val buffer = CharArray(4096)
                while (running) {
                    try {
                        var hasData = false
                        if (stdoutReader.ready()) {
                            val n = stdoutReader.read(buffer, 0, buffer.size)
                            if (n > 0) {
                                callback?.onOutput(String(buffer, 0, n))
                                hasData = true
                            } else {
                                break
                            }
                        }
                        if (stderrReader.ready()) {
                            val n = stderrReader.read(buffer, 0, buffer.size)
                            if (n > 0) {
                                callback?.onOutput(String(buffer, 0, n))
                                hasData = true
                            } else {
                                break
                            }
                        }
                        if (!hasData) {
                            Thread.sleep(30)
                        }
                    } catch (e: Exception) {
                        if (running) Log.e(TAG, "Shizuku read error", e)
                        break
                    }
                }
            }, "shizuku-read-thread")
            readThread?.isDaemon = true
            readThread?.start()
        }

        fun writeBytes(bytes: ByteArray) {
            try {
                // Pipe 模式没有终端驱动做 ICRNL 转换，手动将 CR(0x0D) 转为 LF(0x0A)
                val data = if (bytes.contains(0x0D.toByte())) {
                    ByteArray(bytes.size) { i -> if (bytes[i] == 0x0D.toByte()) 0x0A else bytes[i] }
                } else {
                    bytes
                }
                writer.write(data.toString(Charsets.UTF_8))
                writer.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Shizuku write error", e)
            }
        }

        fun executeCommand(command: String) {
            writeBytes((command + "\n").toByteArray(Charsets.UTF_8))
        }

        fun sendCtrlC() {
            writeBytes(byteArrayOf(0x03))
        }

        fun close() {
            running = false
            try {
                writer.write("exit\n"); writer.flush(); writer.close()
            } catch (_: Exception) {}
            try { process.destroy() } catch (_: Exception) {}
            try { readThread?.join(1000) } catch (_: Exception) {}
        }

        fun isAlive(): Boolean {
            if (!running) return false
            return try {
                // ShizukuRemoteProcess.exitValue() 抛出 IllegalArgumentException
                // 而非标准 IllegalThreadStateException，导致 Process.isAlive() 崩溃
                process.exitValue()
                false // 进程已退出
            } catch (e: Exception) {
                true // 进程仍在运行
            }
        }
    }

    // ========== 通过反射调用 Shizuku.newProcess ==========

    private fun createShizukuProcess(args: Array<String>): Process? {
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            for (method in shizukuClass.declaredMethods) {
                if (method.name == "newProcess") {
                    method.isAccessible = true
                    return method.invoke(null, args, null, null) as Process
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku.newProcess failed", e)
            null
        }
    }

    interface ExecuteCallback {
        fun onOutput(line: String)
        fun onError(error: String)
        fun onComplete(exitCode: Int)
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED &&
                    !Shizuku.isPreV11() &&
                    Shizuku.getVersion() >= 11
        } catch (t: Throwable) {
            false
        }
    }

    fun executeShizukuCommand(command: String, callback: ExecuteCallback) {
        Thread {
            var process: Process? = null
            var stdoutReader: BufferedReader? = null
            var stderrReader: BufferedReader? = null

            try {
                val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                var newProcessMethod: java.lang.reflect.Method? = null

                for (method in shizukuClass.declaredMethods) {
                    if (method.name == "newProcess") {
                        method.isAccessible = true
                        newProcessMethod = method
                        break
                    }
                }

                if (newProcessMethod == null) {
                    throw Exception("Failed to find newProcess method")
                }

                process = newProcessMethod.invoke(
                    null,
                    arrayOf("sh", "-c", command),
                    null,
                    null
                ) as Process

                stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                val finalStdoutReader = stdoutReader
                val finalStderrReader = stderrReader

                val stdoutThread = Thread {
                    try {
                        var line: String?
                        while (finalStdoutReader.readLine().also { line = it } != null) {
                            callback.onOutput(line!!)
                        }
                    } catch (e: Exception) {
                        // 忽略
                    }
                }

                val stderrThread = Thread {
                    try {
                        var line: String?
                        while (finalStderrReader.readLine().also { line = it } != null) {
                            callback.onError(line!!)
                        }
                    } catch (e: Exception) {
                        // 忽略
                    }
                }

                stdoutThread.start()
                stderrThread.start()

                val exitCode = process.waitFor()

                stdoutThread.join(1000)
                stderrThread.join(1000)

                callback.onComplete(exitCode)

            } catch (e: Exception) {
                callback.onError("Shizuku error: ${e.message}")
                callback.onError("Falling back to normal mode...")
                executeNormalCommand(command, callback)
                return@Thread
            } finally {
                try {
                    stdoutReader?.close()
                    stderrReader?.close()
                    process?.destroy()
                } catch (e: Exception) {
                    // 忽略
                }
            }
        }.start()
    }

    fun executeNormalCommand(command: String, callback: ExecuteCallback) {
        Thread {
            var process: Process? = null
            var stdoutReader: BufferedReader? = null
            var stderrReader: BufferedReader? = null

            try {
                process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

                stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                val finalStdoutReader = stdoutReader
                val finalStderrReader = stderrReader

                val stdoutThread = Thread {
                    try {
                        var line: String?
                        while (finalStdoutReader.readLine().also { line = it } != null) {
                            callback.onOutput(line!!)
                        }
                    } catch (e: Exception) {
                        // 忽略
                    }
                }

                val stderrThread = Thread {
                    try {
                        var line: String?
                        while (finalStderrReader.readLine().also { line = it } != null) {
                            callback.onError(line!!)
                        }
                    } catch (e: Exception) {
                        // 忽略
                    }
                }

                stdoutThread.start()
                stderrThread.start()

                val exitCode = process.waitFor()

                stdoutThread.join(1000)
                stderrThread.join(1000)

                callback.onComplete(exitCode)

            } catch (e: Exception) {
                callback.onError("Command failed: ${e.message}")
                callback.onComplete(-1)
            } finally {
                try {
                    stdoutReader?.close()
                    stderrReader?.close()
                    process?.destroy()
                } catch (e: Exception) {
                    // 忽略
                }
            }
        }.start()
    }

    fun executeCommand(command: String, callback: ExecuteCallback) {
        executePersistentCommand(null, command, callback)
    }

    fun executeCommand(context: Context?, command: String, callback: ExecuteCallback) {
        executePersistentCommand(context, command, callback)
    }

    private fun executePersistentCommand(context: Context?, command: String, callback: ExecuteCallback) {
        Thread {
            try {
                var needNewSession = persistentShellProcess == null || !persistentShellProcess!!.isAlive
                val shizukuAvailable = isShizukuAvailable()

                if (!needNewSession && isShizukuSession != shizukuAvailable) {
                    destroyPersistentSession()
                    needNewSession = true
                }

                if (needNewSession) {
                    createPersistentSession(context, shizukuAvailable)
                }

                if (persistentShellWriter != null && persistentShellProcess != null) {
                    val endMarker = "__CMD_END_${System.currentTimeMillis()}__"
                    val exitCodeMarker = "__EXIT_CODE_${System.currentTimeMillis()}__"

                    persistentShellWriter!!.write("$command\n")
                    persistentShellWriter!!.write("echo $exitCodeMarker\$?\n")
                    persistentShellWriter!!.write("echo $endMarker\n")
                    persistentShellWriter!!.flush()

                    val exitCode = intArrayOf(0)
                    val commandEnded = booleanArrayOf(false)

                    val stdoutThread = Thread {
                        try {
                            var line: String?
                            while (persistentShellStdout!!.readLine().also { line = it } != null && !commandEnded[0]) {
                                when {
                                    line == endMarker -> {
                                        commandEnded[0] = true
                                        break
                                    }
                                    line!!.startsWith(exitCodeMarker) -> {
                                        try {
                                            exitCode[0] = line!!.substring(exitCodeMarker.length).toInt()
                                        } catch (e: Exception) {
                                            exitCode[0] = 0
                                        }
                                    }
                                    else -> callback.onOutput(line!!)
                                }
                            }
                        } catch (e: Exception) {
                            // 会话可能已断开
                        }
                    }

                    val stderrThread = Thread {
                        try {
                            while (!commandEnded[0]) {
                                if (persistentShellStderr!!.ready()) {
                                    val line = persistentShellStderr!!.readLine()
                                    if (line != null) {
                                        callback.onError(line)
                                    }
                                } else {
                                    Thread.sleep(50)
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略
                        }
                    }

                    stdoutThread.start()
                    stderrThread.start()

                    val startTime = System.currentTimeMillis()
                    while (!commandEnded[0] && System.currentTimeMillis() - startTime < 10000) {
                        Thread.sleep(100)
                    }

                    callback.onComplete(exitCode[0])

                } else {
                    throw Exception("Failed to create persistent shell session")
                }

            } catch (e: Exception) {
                callback.onError("Session error: ${e.message}")
                callback.onError("Trying to recreate session...")
                destroyPersistentSession()
                executeFallbackCommand(command, callback)
            }
        }.start()
    }

    private fun createPersistentSession(context: Context?, useShizuku: Boolean) {
        if (useShizuku) {
            try {
                val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                for (method in shizukuClass.declaredMethods) {
                    if (method.name == "newProcess") {
                        method.isAccessible = true
                        persistentShellProcess = method.invoke(
                            null,
                            arrayOf("sh"),
                            null,
                            null
                        ) as Process
                        isShizukuSession = true
                        break
                    }
                }
            } catch (e: Exception) {
                throw Exception(context?.getString(R.string.shizuku_session_creation_failed, e.message) ?: e.message)
            }
        } else {
            persistentShellProcess = Runtime.getRuntime().exec(arrayOf("sh"))
            isShizukuSession = false
        }

        persistentShellProcess?.let { process ->
            persistentShellWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
            persistentShellStdout = BufferedReader(InputStreamReader(process.inputStream))
            persistentShellStderr = BufferedReader(InputStreamReader(process.errorStream))

            persistentShellWriter!!.write("export PS1=''\n")
            persistentShellWriter!!.write("export PS2=''\n")
            persistentShellWriter!!.write("set +m\n")

            if (useShizuku) {
                persistentShellWriter!!.write("cd /data/local/tmp 2>/dev/null || cd /sdcard\n")
            } else {
                persistentShellWriter!!.write("cd /sdcard 2>/dev/null || cd /data/local/tmp\n")
            }

            persistentShellWriter!!.flush()
            Thread.sleep(200)

            while (persistentShellStdout!!.ready()) {
                persistentShellStdout!!.readLine()
            }
            while (persistentShellStderr!!.ready()) {
                persistentShellStderr!!.readLine()
            }
        }
    }

    private fun destroyPersistentSession() {
        try {
            persistentShellWriter?.write("exit\n")
            persistentShellWriter?.flush()
            persistentShellWriter?.close()
            persistentShellStdout?.close()
            persistentShellStderr?.close()
            persistentShellProcess?.destroy()
        } catch (e: Exception) {
            // 忽略
        } finally {
            persistentShellProcess = null
            persistentShellWriter = null
            persistentShellStdout = null
            persistentShellStderr = null
        }
    }

    private fun executeFallbackCommand(command: String, callback: ExecuteCallback) {
        if (isShizukuAvailable()) {
            executeShizukuCommand(command, callback)
        } else {
            executeNormalCommand(command, callback)
        }
    }

    // ========== PTY / Shizuku 会话公共 API ==========

    /**
     * 启动终端会话 (自动选择模式):
     * - Shizuku 可用: ShizukuShellSession (shell UID 权限, pipe I/O)
     * - Shizuku 不可用: PtyShellSession (app UID, 真 PTY)
     */
    fun startTerminalSession(
        callback: ExecuteCallback,
        rows: Int = 24,
        cols: Int = 80,
        cwd: String = if (isShizukuAvailable()) "/data/local/tmp" else "/sdcard"
    ): Any? {
        destroyPtySession()

        if (isShizukuAvailable()) {
            return startShizukuSession(callback)
        } else {
            return startPtySession(callback, rows, cols, cwd)
        }
    }

    /**
     * 创建并启动 Shizuku Shell 会话 (shell UID 权限).
     */
    fun startShizukuSession(
        callback: ExecuteCallback
    ): ShizukuShellSession? {
        destroyPtySession()

        return try {
            Log.d(TAG, "Starting Shizuku shell session")
            // 使用 -i 强制交互模式，pipe 模式下也能显示提示符
            val process = createShizukuProcess(arrayOf("sh", "-i"))
                ?: throw Exception("Failed to create Shizuku process")

            val session = ShizukuShellSession(process, callback)
            session.startReading()
            ptySession = null
            shizukuSession = session
            session
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Shizuku session", e)
            callback.onError("Shizuku init failed: ${e.message}")
            null
        }
    }

    /**
     * 创建并启动 PTY Shell 会话 (基于 TermuxBridge).
     * 仅用于 Shizuku 不可用时回落。
     */
    fun startPtySession(
        callback: ExecuteCallback,
        rows: Int = 24,
        cols: Int = 80,
        cwd: String = if (isShizukuAvailable()) "/data/local/tmp" else "/sdcard"
    ): PtyShellSession? {
        // 清理旧会话
        destroyPtySession()

        return try {
            Log.d(TAG, "Starting PTY shell session (${rows}x$cols, cwd=$cwd)")
            val pty = TermuxBridge.createShellSession(cwd = cwd, rows = rows, cols = cols)
            val session = PtyShellSession(pty, callback)
            session.startReading()
            ptySession = session
            session
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PTY session", e)
            callback.onError("PTY init failed: ${e.message}")
            null
        }
    }

    /**
     * 向当前活跃会话写入字节 (自动选择 PTY 或 Shizuku).
     */
    fun executePtyCommandRaw(bytes: ByteArray): Boolean {
        // 优先 Shizuku 会话
        val shizuku = shizukuSession
        if (shizuku != null && shizuku.isAlive()) {
            shizuku.writeBytes(bytes)
            return true
        }
        // 其次 PTY 会话
        val session = ptySession
        if (session != null && session.isAlive()) {
            try {
                session.pty.outputStream.write(bytes)
                session.pty.outputStream.flush()
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write bytes to PTY", e)
            }
        }
        return false
    }

    /**
     * 通过 PTY 会话执行命令.
     * @return true 如果成功写入
     */
    fun executePtyCommand(command: String): Boolean {
        val session = ptySession
        return if (session != null && session.isAlive()) {
            session.executeCommand(command)
            true
        } else {
            false
        }
    }

    /** 发送 Ctrl+C */
    fun sendPtyCtrlC() {
        shizukuSession?.sendCtrlC() ?: ptySession?.sendCtrlC()
    }

    /** 设置 PTY 终端尺寸 (仅 PTY 模式有效) */
    fun setPtyWindowSize(rows: Int, cols: Int) {
        ptySession?.setWindowSize(rows, cols)
    }

    /** 检查会话是否存活 */
    fun isSessionAlive(): Boolean {
        return shizukuSession?.isAlive() == true || ptySession?.isAlive() == true
    }

    /** 销毁所有会话 */
    fun destroyPtySession() {
        shizukuSession?.close()
        shizukuSession = null
        ptySession?.close()
        ptySession = null
    }

    // ========== 传统模式 API ==========

    fun getCurrentWorkingDirectory(): String {
        return currentWorkingDirectory
    }

    fun resetSession() {
        destroyPersistentSession()
        destroyPtySession()
    }

    fun copyToClipboard(context: Context, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(context.getString(R.string.terminal_output), text)
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            false
        }
    }

    object CommandHistory {
        private const val MAX_HISTORY = 100
        private val history = ArrayList<String>()
        private var currentIndex = -1

        fun addCommand(command: String) {
            if (command.isNullOrBlank()) return

            if (history.isNotEmpty() && history[history.size - 1] == command) {
                return
            }

            history.add(command)
            if (history.size > MAX_HISTORY) {
                history.removeAt(0)
            }
            currentIndex = history.size
        }

        fun getAll(): List<String> {
            return ArrayList(history)
        }

        fun clear() {
            history.clear()
            currentIndex = -1
        }
    }

    object QuickCommands {
        val COMMANDS = arrayOf(
            "ls -la",
            "pwd",
            "whoami",
            "uname -a",
            "df -h",
            "free -h",
            "ps aux",
            "pm list packages",
            "pm list packages -3",
            "getprop",
            "logcat -d -v time | tail -50",
            "dumpsys battery",
            "netstat",
            "top -n1",
            "service list"
        )

        val COMMAND_NAMES = arrayOf(
            "List files (detailed)",
            "Current directory",
            "Current user",
            "System information",
            "Disk space",
            "Memory information",
            "Process list",
            "All packages",
            "Third-party packages",
            "System properties",
            "Recent logs",
            "Battery status",
            "Network connections",
            "Top processes",
            "System services"
        )
    }
}
