package io.github.huidoudour.Installer.util

import android.content.Context
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 日志条目，携带唯一 ID 用于 LazyColumn key 避免重复 key 崩溃
 */
data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: String
)

/**
 * 全局日志管理器
 * 内存上限 MAX_LOG_ENTRIES 条，超出时移除最旧条目。
 * 持久化使用文件存储，无 SharedPreferences 大小限制。
 */
class LogManager private constructor() {

    companion object {
        private const val LOG_FILE_NAME = "installer_logs.txt"
        private const val MAX_LOG_ENTRIES = 10_000

        @Volatile
        private var instance: LogManager? = null

        fun getInstance(): LogManager {
            return instance ?: synchronized(this) {
                instance ?: LogManager().also { instance = it }
            }
        }
    }

    private val logs = mutableListOf<LogEntry>()
    private val listeners = mutableListOf<LogListener>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var context: Context? = null
    private var logFile: File? = null

    interface LogListener {
        fun onLogAdded(log: LogEntry, index: Int)
        fun onLogCleared()
    }

    fun setContext(context: Context) {
        this.context = context
        this.logFile = File(context.filesDir, LOG_FILE_NAME)
        loadLogs()
    }

    private fun loadLogs() {
        val file = logFile ?: return
        if (!file.exists()) return
        try {
            logs.clear()
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line != null) {
                        logs.add(LogEntry(text = line!!, timestamp = ""))
                    }
                }
            }
            // 如果历史日志超过上限，从头部裁剪
            trimToMaxEntries()
        } catch (e: Exception) {
            logs.clear()
        }
    }

    private fun trimToMaxEntries() {
        while (logs.size > MAX_LOG_ENTRIES) {
            logs.removeAt(0)
        }
    }

    private fun saveLogsAsync() {
        val file = logFile ?: return
        Thread {
            try {
                BufferedWriter(FileWriter(file, false)).use { writer ->
                    for (log in logs) {
                        writer.write(log.text)
                        writer.newLine()
                    }
                }
            } catch (e: Exception) {
                // 忽略写入错误
            }
        }.start()
    }

    fun addLog(message: String) {
        addLog(message, "App")
    }

    fun addLog(message: String, tag: String?) {
        val timestamp = dateFormat.format(Date())
        val logMessage = if (tag != null) "$timestamp [$tag]: $message" else "$timestamp: $message"

        val entry = LogEntry(text = logMessage, timestamp = timestamp)
        logs.add(entry)
        // 超出上限时移除最旧条目
        while (logs.size > MAX_LOG_ENTRIES) {
            logs.removeAt(0)
        }
        val insertedIndex = logs.size - 1

        // 通知所有监听器
        for (listener in ArrayList(listeners)) {
            listener.onLogAdded(entry, insertedIndex)
        }

        // 持久化保存
        saveLogsAsync()

        // 同时输出到 System.out
        println("Installer: $logMessage")
    }

    fun clearLogs() {
        logs.clear()
        for (listener in ArrayList(listeners)) {
            listener.onLogCleared()
        }
        saveLogsAsync()
    }

    fun getAllLogs(): String {
        if (logs.isEmpty()) {
            return "Waiting for operation..."
        }
        val sb = StringBuilder()
        for (log in logs) {
            sb.append(log.text).append("\n")
        }
        return sb.toString()
    }

    fun getLogsSnapshot(): List<LogEntry> {
        return Collections.unmodifiableList(ArrayList(logs))
    }

    fun getLogCount(): Int {
        return logs.size
    }

    fun getLastUpdateTime(): String {
        if (logs.isEmpty()) {
            return "--:--:--"
        }
        return dateFormat.format(Date())
    }

    fun addListener(listener: LogListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }
}
