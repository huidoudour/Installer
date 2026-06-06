package io.github.huidoudour.Installer.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.huidoudour.Installer.util.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

data class OutputSegment(
    val text: String,
    val color: Color,
    val isBold: Boolean = false
)

class ShellViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    private val _outputText = MutableStateFlow("")
    val outputText: StateFlow<String> = _outputText.asStateFlow()

    private val _outputSegments = MutableStateFlow<List<OutputSegment>>(emptyList())
    val outputSegments: StateFlow<List<OutputSegment>> = _outputSegments.asStateFlow()

    private val _commandText = MutableStateFlow("")
    val commandText: StateFlow<String> = _commandText.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _searchResult = MutableStateFlow("")
    val searchResult: StateFlow<String> = _searchResult.asStateFlow()

    private var fullOutputText = ""
    private var fullOutputSegments = mutableListOf<OutputSegment>()

    var userScrolledAwayFromBottom by mutableStateOf(false)
    var outputLineCount by mutableStateOf(0)

    private var historyIndex = -1

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val colorPrimary = Color(0xFF6750A4)
    private val colorOnSurface = Color(0xFF1C1B1F)
    private val colorError = Color(0xFFB3261E)
    private val colorTertiary = Color(0xFF7D5260)
    private val colorOnSurfaceVariant = Color(0xFF49454F)

    init {
        val isShizuku = ShellExecutor.isShizukuAvailable()
        val welcomeSegments = mutableListOf<OutputSegment>()

        welcomeSegments.add(OutputSegment("欢迎来到~终端\n", colorPrimary, true))
        welcomeSegments.add(OutputSegment("~v2.0\n", colorOnSurfaceVariant, false))

        if (isShizuku) {
            welcomeSegments.add(OutputSegment("[*] Root mode enabled via Shizuku\n", colorPrimary, false))
            welcomeSegments.add(OutputSegment("[*] Working directory: /data/local/tmp\n", colorPrimary, false))
        } else {
            welcomeSegments.add(OutputSegment("[!] User mode (grant Shizuku for root)\n", colorTertiary, false))
            welcomeSegments.add(OutputSegment("[*] Working directory: /sdcard\n", colorPrimary, false))
        }

        welcomeSegments.add(OutputSegment("Type help for command list\n", colorOnSurfaceVariant, false))
        appendSegments(welcomeSegments)
    }

    fun updateCommandText(text: String) {
        _commandText.value = text
        historyIndex = -1
    }

    fun executeCommand() {
        val command = _commandText.value.trim()
        if (command.isEmpty() || _isExecuting.value) return

        ShellExecutor.CommandHistory.addCommand(command)
        historyIndex = -1

        val prompt = if (ShellExecutor.isShizukuAvailable()) "# " else "$ "
        val promptSegments = listOf(OutputSegment("$prompt$command\n", colorPrimary, true))

        _commandText.value = ""
        _isExecuting.value = true
        userScrolledAwayFromBottom = false

        appendSegments(promptSegments)

        viewModelScope.launch(Dispatchers.IO) {
            ShellExecutor.executeCommand(command, object : ShellExecutor.ExecuteCallback {
                override fun onOutput(line: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        appendSegments(listOf(OutputSegment("$line\n", colorOnSurface, false)))
                    }
                }

                override fun onError(error: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        appendSegments(listOf(OutputSegment("$error\n", colorError, false)))
                    }
                }

                override fun onComplete(exitCode: Int) {
                    viewModelScope.launch(Dispatchers.Main) {
                        if (exitCode != 0) {
                            appendSegments(listOf(OutputSegment(
                                "[Process completed with exit code $exitCode]\n", colorTertiary, false)))
                        }
                        _isExecuting.value = false
                    }
                }
            })
        }
    }

    fun clearScreen() {
        _outputText.value = ""
        fullOutputText = ""
        fullOutputSegments.clear()
        _outputSegments.value = emptyList()
        outputLineCount = 0
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
        _commandText.value += "\t"
    }

    fun cancelCommand() {
        if (_isExecuting.value) {
            ShellExecutor.resetSession()
            appendSegments(listOf(
                OutputSegment("^C\n", colorError, true),
                OutputSegment("[Command cancelled, session reset]\n", colorTertiary, false)
            ))
            _isExecuting.value = false
        } else {
            _commandText.value = ""
        }
    }

    fun clearInput() {
        _commandText.value = ""
    }

    fun saveOutput(context: android.content.Context): Result<java.io.File> {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())
            val fileName = "shell_output_$timestamp.txt"
            val outputFile = java.io.File(context.externalCacheDir, fileName)
            java.io.FileWriter(outputFile).use { writer ->
                writer.write(fullOutputText)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun toggleSearch() {
        _isSearchVisible.value = !_isSearchVisible.value
        if (!_isSearchVisible.value) {
            _searchText.value = ""
            _searchResult.value = ""
            _outputText.value = fullOutputText
        }
    }

    fun updateSearchText(text: String) {
        _searchText.value = text
        if (text.isEmpty()) {
            _outputText.value = fullOutputText
            _searchResult.value = ""
        } else {
            performSearch(text)
        }
    }

    private fun performSearch(query: String) {
        val lines = fullOutputText.split("\n")
        val matchingLines = mutableListOf<String>()
        for (line in lines) {
            if (line.contains(query, ignoreCase = true)) {
                matchingLines.add(line)
            }
        }
        val result = matchingLines.joinToString("\n")
        _outputText.value = result
        _searchResult.value = result
    }

    private fun appendSegments(segments: List<OutputSegment>) {
        fullOutputSegments.addAll(segments)
        for (seg in segments) {
            fullOutputText += seg.text
        }

        if (!_isSearchVisible.value) {
            _outputText.value = fullOutputText
        }

        _outputSegments.value = fullOutputSegments.toList()
        outputLineCount = fullOutputText.count { it == '\n' }
    }

    fun getAnnotatedOutput(): androidx.compose.ui.text.AnnotatedString {
        return buildAnnotatedString {
            for (segment in fullOutputSegments) {
                withStyle(SpanStyle(
                    color = segment.color,
                    fontWeight = if (segment.isBold) {
                        androidx.compose.ui.text.font.FontWeight.Bold
                    } else {
                        androidx.compose.ui.text.font.FontWeight.Normal
                    }
                )) {
                    append(segment.text)
                }
            }
        }
    }

    fun getSearchAnnotatedOutput(query: String): androidx.compose.ui.text.AnnotatedString {
        return buildAnnotatedString {
            for (segment in fullOutputSegments) {
                val text = segment.text
                if (text.contains(query, ignoreCase = true)) {
                    withStyle(SpanStyle(
                        color = segment.color,
                        fontWeight = if (segment.isBold) {
                            androidx.compose.ui.text.font.FontWeight.Bold
                        } else {
                            androidx.compose.ui.text.font.FontWeight.Normal
                        },
                        background = Color(0x40FFEB3B)
                    )) {
                        append(text)
                    }
                }
            }
        }
    }
}
