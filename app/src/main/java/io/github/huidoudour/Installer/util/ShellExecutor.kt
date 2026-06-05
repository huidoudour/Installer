package io.github.huidoudour.Installer.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
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

    private var persistentShellProcess: Process? = null
    private var persistentShellWriter: BufferedWriter? = null
    private var persistentShellStdout: BufferedReader? = null
    private var persistentShellStderr: BufferedReader? = null
    private var isShizukuSession = false
    private var currentWorkingDirectory = "/"

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

    fun getCurrentWorkingDirectory(): String {
        return currentWorkingDirectory
    }

    fun resetSession() {
        destroyPersistentSession()
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
