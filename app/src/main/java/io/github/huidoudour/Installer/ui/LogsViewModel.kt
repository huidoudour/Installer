package io.github.huidoudour.Installer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import io.github.huidoudour.Installer.util.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LogsScreen ViewModel
 */
class LogsViewModel(application: Application) : AndroidViewModel(application) {

    private val logManager = LogManager.getInstance()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _logCount = MutableStateFlow(0)
    val logCount: StateFlow<Int> = _logCount.asStateFlow()

    private val listener = object : LogManager.LogListener {
        override fun onLogAdded(log: String, index: Int) {
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

    override fun onCleared() {
        super.onCleared()
        logManager.removeListener(listener)
    }
}
