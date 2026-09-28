package dev.huidoudour.installer.util

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
import java.util.concurrent.Executors

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
    private val persistenceExecutor = Executors.newSingleThreadExecutor()
    private var context: Context? = null
    private var logFile: File? = null

    interface LogListener {
        fun onLogAdded(log: LogEntry, index: Int)
        fun onLogCleared()
    }

    @Synchronized
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
                        logs.add(LogEntry(text = line, timestamp = ""))
                    }
                }
            }
            // 如果历史日志超过上限，从头部裁剪
            trimToMaxEntries()
        } catch (_: Exception) {
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
        // Keep a stable copy and serialize writes: installation callbacks can arrive from
        // multiple worker threads, and an older asynchronous write must not overwrite newer logs.
        val snapshot = ArrayList(logs)
        persistenceExecutor.execute {
            try {
                BufferedWriter(FileWriter(file, false)).use { writer ->
                    for (log in snapshot) {
                        writer.write(log.text)
                        writer.newLine()
                    }
                }
            } catch (_: Exception) {
                // 忽略写入错误
            }
        }
    }

    @Synchronized
    fun addLog(message: String) {
        addLog(message, "App")
    }

    @Synchronized
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

    @Synchronized
    fun clearLogs() {
        logs.clear()
        for (listener in ArrayList(listeners)) {
            listener.onLogCleared()
        }
        saveLogsAsync()
    }

    @Synchronized
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

    @Synchronized
    fun getLogsSnapshot(): List<LogEntry> {
        return Collections.unmodifiableList(ArrayList(logs))
    }

    @Synchronized
    fun getLogCount(): Int {
        return logs.size
    }

    @Synchronized
    fun addListener(listener: LogListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    @Synchronized
    fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }
}
