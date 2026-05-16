package io.github.huidoudour.Installer.util

import android.content.Context

/**
 * 本地原生助手类
 */
object NativeHelper {

    /**
     * 快速命令前缀
     */
    const val NATIVE_PREFIX = "native:"

    /**
     * 检查是否为原生命令
     */
    fun isNativeCommand(command: String): Boolean {
        return command.startsWith(NATIVE_PREFIX)
    }

    /**
     * 解析原生命令
     */
    fun parseNativeCommand(command: String): String? {
        return if (isNativeCommand(command)) {
            command.substring(NATIVE_PREFIX.length)
        } else null
    }

    /**
     * 执行原生命令
     */
    fun executeNativeCommand(command: String, context: Context): String {
        val nativeCommand = parseNativeCommand(command)
            ?: return "Unknown native command"

        return when (nativeCommand.lowercase()) {
            "sysinfo" -> getSystemInfo()
            "meminfo" -> getMemoryInfo()
            "cpuinfo" -> getCpuInfo()
            "battery" -> getBatteryInfo()
            "storage" -> getStorageInfo()
            else -> "Unknown command: $nativeCommand"
        }
    }

    private fun getSystemInfo(): String {
        return buildString {
            appendLine("=== System Information ===")
            appendLine("OS: Android")
            appendLine("Version: ${android.os.Build.VERSION.RELEASE}")
            appendLine("SDK: ${android.os.Build.VERSION.SDK_INT}")
            appendLine("Device: ${android.os.Build.DEVICE}")
            appendLine("Model: ${android.os.Build.MODEL}")
            appendLine("Manufacturer: ${android.os.Build.MANUFACTURER}")
        }
    }

    private fun getMemoryInfo(): String {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024
        val usedMemory = totalMemory - freeMemory

        return buildString {
            appendLine("=== Memory Information ===")
            appendLine("Max Memory: ${maxMemory}MB")
            appendLine("Total Memory: ${totalMemory}MB")
            appendLine("Free Memory: ${freeMemory}MB")
            appendLine("Used Memory: ${usedMemory}MB")
        }
    }

    private fun getCpuInfo(): String {
        return buildString {
            appendLine("=== CPU Information ===")
            appendLine("ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Features: ${android.os.Build.VERSION.SDK_INT}")
        }
    }

    private fun getBatteryInfo(): String {
        return buildString {
            appendLine("=== Battery Information ===")
            appendLine("Battery status requires dumpsys")
        }
    }

    private fun getStorageInfo(): String {
        return buildString {
            appendLine("=== Storage Information ===")
            appendLine("Storage info requires df command")
        }
    }
}
