package io.github.huidoudour.Installer.util

import android.content.Context

/**
 * 命令自动补全工具类
 */
object CommandAutocomplete {

    private val commonCommands = listOf(
        "ls", "ls -la", "ls -l", "ls -a",
        "cd", "pwd", "cat", "echo",
        "rm", "rm -rf", "cp", "mv",
        "mkdir", "rmdir", "chmod", "chown",
        "ps", "ps aux", "top", "htop",
        "pm list packages", "pm install", "pm uninstall",
        "getprop", "setprop", "dumpsys",
        "logcat", "logcat -d", "logcat -c",
        "input", "input text", "input keyevent",
        "am start", "am force-stop", "am kill",
        "service list", "dumpsys battery",
        "netstat", "ifconfig", "ping",
        "su", "sudo", "exit",
        "find", "grep", "awk", "sed",
        "tar", "unzip", "zip", "gzip",
        "df", "du", "free", "mount",
        "reboot", "reboot recovery", "reboot bootloader"
    )

    private val pathCompletions = listOf(
        "/", "/sdcard", "/sdcard/Download",
        "/data", "/data/local", "/data/local/tmp",
        "/system", "/system/bin", "/system/app",
        "/storage", "/storage/emulated", "/storage/emulated/0"
    )

    /**
     * 获取命令建议
     */
    fun getSuggestions(input: String): List<String> {
        if (input.isBlank()) return emptyList()

        val lowerInput = input.lowercase()

        return commonCommands
            .filter { it.lowercase().startsWith(lowerInput) }
            .sortedBy { it.length }
    }

    /**
     * 获取路径补全建议
     */
    fun getPathSuggestions(input: String): List<String> {
        if (input.isBlank() || !input.startsWith("/")) return emptyList()

        return pathCompletions
            .filter { it.lowercase().startsWith(input.lowercase()) }
            .sortedBy { it.length }
    }

    /**
     * 补全命令
     */
    fun autocomplete(input: String): String? {
        val suggestions = getSuggestions(input)
        return suggestions.firstOrNull()
    }

    /**
     * 补全路径
     */
    fun autocompletePath(input: String): String? {
        val suggestions = getPathSuggestions(input)
        return suggestions.firstOrNull()
    }

    /**
     * 保存自定义命令到历史
     */
    fun addToHistory(context: Context, command: String) {
        CommandHistory.addCommand(command)
    }

    /**
     * 获取命令历史
     */
    fun getHistory(): List<String> {
        return CommandHistory.getAll()
    }

    /**
     * 清空历史
     */
    fun clearHistory() {
        CommandHistory.clear()
    }

    private object CommandHistory {
        private const val MAX_HISTORY = 100
        private val history = mutableListOf<String>()

        fun addCommand(command: String) {
            if (command.isBlank()) return
            if (history.isNotEmpty() && history.last() == command) return
            history.add(command)
            if (history.size > MAX_HISTORY) {
                history.removeAt(0)
            }
        }

        fun getAll(): List<String> = history.toList()
        fun clear() = history.clear()
    }
}
