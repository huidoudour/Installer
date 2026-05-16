package io.github.huidoudour.Installer.util

import android.content.Context
import android.content.SharedPreferences
import android.util.JsonReader
import android.util.JsonWriter
import io.github.huidoudour.Installer.R
import java.io.StringReader
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * 全局日志管理器
 */
class LogManager private constructor() {

    companion object {
        private const val PREFS_NAME = "log_manager_prefs"
        private const val KEY_LOGS_JSON = "logs_json"

        @Volatile
        private var instance: LogManager? = null

        fun getInstance(): LogManager {
            return instance ?: synchronized(this) {
                instance ?: LogManager().also { instance = it }
            }
        }
    }

    private val logs = mutableListOf<String>()
    private val listeners = mutableListOf<LogListener>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var context: Context? = null
    private var prefs: SharedPreferences? = null

    interface LogListener {
        fun onLogAdded(log: String, index: Int)
        fun onLogCleared()
    }

    fun setContext(context: Context) {
        this.context = context
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadLogs()
    }

    private fun loadLogs() {
        if (prefs == null) return
        val json = prefs?.getString(KEY_LOGS_JSON, null) ?: return
        if (json.isEmpty()) return
        try {
            logs.clear()
            val reader = JsonReader(StringReader(json))
            reader.beginArray()
            while (reader.hasNext()) {
                logs.add(reader.nextString())
            }
            reader.endArray()
            reader.close()
        } catch (e: Exception) {
            logs.clear()
        }
    }

    private fun saveLogsAsync() {
        if (prefs == null) return
        Thread {
            try {
                val stringWriter = StringWriter()
                val writer = JsonWriter(stringWriter)
                writer.beginArray()
                for (log in logs) {
                    writer.value(log)
                }
                writer.endArray()
                writer.close()
                prefs?.edit()?.putString(KEY_LOGS_JSON, stringWriter.toString())?.apply()
            } catch (e: Exception) {
                // 忽略写入错误
            }
        }.start()
    }

    fun addLog(message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "$timestamp: $message"

        logs.add(logMessage)
        val insertedIndex = logs.size - 1

        // 通知所有监听器
        for (listener in ArrayList(listeners)) {
            listener.onLogAdded(logMessage, insertedIndex)
        }

        // 持久化保存
        saveLogsAsync()

        // 同时输出到 System.out
        val logPrefix = context?.getString(R.string.app_name) ?: "Installer"
        println("$logPrefix: $logMessage")
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
            return context?.getString(R.string.log_manager_waiting) ?: "等待操作..."
        }
        val sb = StringBuilder()
        for (log in logs) {
            sb.append(log).append("\n")
        }
        return sb.toString()
    }

    fun getLogsSnapshot(): List<String> {
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
