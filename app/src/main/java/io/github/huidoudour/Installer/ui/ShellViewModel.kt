package io.github.huidoudour.Installer.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.util.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * ShellScreen ViewModel
 */
class ShellViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    private val _outputText = MutableStateFlow("")
    val outputText: StateFlow<String> = _outputText.asStateFlow()

    private val _commandText = MutableStateFlow("")
    val commandText: StateFlow<String> = _commandText.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private var fullOutputText = ""

    private var historyIndex = -1

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Material You 颜色
    private val colorPrimary = Color(0xFF6750A4)
    private val colorOnSurface = Color(0xFF1C1B1F)
    private val colorError = Color(0xFFB3261E)
    private val colorTertiary = Color(0xFF7D5260)
    private val colorOnSurfaceVariant = Color(0xFF49454F)

    init {
        appendOutput(context.getString(R.string.shell_hint), colorOnSurfaceVariant, false)
    }

    fun updateCommandText(text: String) {
        _commandText.value = text
        historyIndex = -1
    }

    fun executeCommand() {
        val command = _commandText.value.trim()
        if (command.isEmpty() || _isExecuting.value) return

        // 添加到历史
        ShellExecutor.CommandHistory.addCommand(command)
        historyIndex = -1

        // 显示命令
        val prompt = if (ShellExecutor.isShizukuAvailable()) {
            "# "
        } else {
            "$ "
        }
        appendOutput(prompt + command, colorPrimary, true)

        // 清空输入
        _commandText.value = ""
        _isExecuting.value = true

        // 执行命令
        viewModelScope.launch(Dispatchers.IO) {
            ShellExecutor.executeCommand(command, object : ShellExecutor.ExecuteCallback {
                override fun onOutput(line: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        appendOutput(line, colorOnSurface, false)
                    }
                }

                override fun onError(error: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        appendOutput(error, colorError, false)
                    }
                }

                override fun onComplete(exitCode: Int) {
                    viewModelScope.launch(Dispatchers.Main) {
                        if (exitCode != 0) {
                            appendOutput("Process completed with exit code $exitCode", colorTertiary, false)
                        }
                        appendOutput("", colorOnSurface, false)
                        _isExecuting.value = false
                    }
                }
            })
        }
    }

    fun clearScreen() {
        _outputText.value = ""
        fullOutputText = ""
    }

    fun copyOutput() {
        ShellExecutor.copyToClipboard(context, _outputText.value)
    }

    fun navigateHistoryUp() {
        val history = ShellExecutor.CommandHistory.getAll()
        if (history.isEmpty()) return

        if (historyIndex == -1) {
            historyIndex = history.size - 1
        } else if (historyIndex > 0) {
            historyIndex--
        }

        if (historyIndex >= 0 && historyIndex < history.size) {
            _commandText.value = history[historyIndex]
        }
    }

    fun navigateHistoryDown() {
        val history = ShellExecutor.CommandHistory.getAll()
        if (historyIndex == -1) return

        if (historyIndex < history.size - 1) {
            historyIndex++
            _commandText.value = history[historyIndex]
        } else {
            historyIndex = -1
            _commandText.value = ""
        }
    }

    fun insertTab() {
        _commandText.value += "  "
    }

    fun cancelCommand() {
        if (_isExecuting.value) {
            ShellExecutor.resetSession()
            appendOutput("^C", colorError, true)
            appendOutput("Command cancelled, session reset", colorTertiary, false)
            appendOutput("", colorOnSurface, false)
            _isExecuting.value = false
        } else {
            _commandText.value = ""
        }
    }

    fun clearInput() {
        _commandText.value = ""
    }

    fun toggleSearch() {
        _isSearchVisible.value = !_isSearchVisible.value
        if (!_isSearchVisible.value) {
            _searchText.value = ""
        }
    }

    fun updateSearchText(text: String) {
        _searchText.value = text
        if (text.isEmpty()) {
            _outputText.value = fullOutputText
        } else {
            performSearch(text)
        }
    }

    private fun performSearch(query: String) {
        val lines = fullOutputText.split("\n")
        val filtered = lines.filter { it.lowercase().contains(query.lowercase()) }
        _outputText.value = filtered.joinToString("\n")
    }

    private fun appendOutput(text: String, color: Color, bold: Boolean) {
        val timestamp = timeFormat.format(java.util.Date())
        val coloredText = "[$timestamp] $text"

        fullOutputText += "$coloredText\n"
        _outputText.value = fullOutputText
    }
}
