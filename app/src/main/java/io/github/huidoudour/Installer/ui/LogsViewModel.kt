package io.github.huidoudour.Installer.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import io.github.huidoudour.Installer.util.LogEntry
import io.github.huidoudour.Installer.util.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LogsScreen ViewModel
 */
class LogsViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val logManager = LogManager.getInstance()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _logCount = MutableStateFlow(0)
    val logCount: StateFlow<Int> = _logCount.asStateFlow()

    private val listener = object : LogManager.LogListener {
        override fun onLogAdded(log: LogEntry, index: Int) {
            _logs.value = logManager.getLogsSnapshot()
            _logCount.value = logManager.getLogCount()
        }

        override fun onLogCleared() {
            _logs.value = emptyList()
            _logCount.value = 0
        }
    }

    init {
        logManager.addListener(listener)
        refreshLogs()
    }

    fun refreshLogs() {
        _logs.value = logManager.getLogsSnapshot()
        _logCount.value = logManager.getLogCount()
    }

    fun clearLogs() {
        logManager.clearLogs()
    }

    fun getAllLogsText(): String {
        return logManager.getAllLogs()
    }

    /**
     * 导出日志到文件
     */
    fun exportLogs(): Result<File> {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "installer_log_$timestamp.txt"
            val logFile = File(context.externalCacheDir, fileName)

            FileWriter(logFile).use { writer ->
                writer.write("=== Installer Log Export ===\n")
                writer.write("Export Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                writer.write("Total Logs: ${logManager.getLogCount()}\n\n")
                writer.write("--- Log Content ---\n")
                writer.write(logManager.getAllLogs())
            }

            Result.success(logFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 分享日志文件
     */
    fun shareLogFile(logFile: File): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Installer Log")
            putExtra(Intent.EXTRA_TEXT, "Log file generated: ${logFile.name}")
            putExtra(
                Intent.EXTRA_STREAM,
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    logFile
                )
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return shareIntent
    }

    override fun onCleared() {
        super.onCleared()
        logManager.removeListener(listener)
    }
}
