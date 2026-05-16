package io.github.huidoudour.Installer.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地崩溃处理器
 */
class NativeCrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    companion object {
        private const val TAG = "NativeCrashHandler"
        private const val CRASH_DIR = "crashes"
    }

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "Uncaught exception: ${throwable.message}", throwable)

        // 保存崩溃信息
        saveCrashReport(thread, throwable)

        // 调用默认处理器
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrashReport(thread: Thread, throwable: Throwable) {
        try {
            val crashDir = File(context.cacheDir, CRASH_DIR)
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }

            val timestamp = dateFormat.format(Date())
            val crashFile = File(crashDir, "crash_$timestamp.txt")

            PrintWriter(FileWriter(crashFile)).use { writer ->
                writer.println("=== Crash Report ===")
                writer.println("Time: $timestamp")
                writer.println("App Version: ${getAppVersion()}")
                writer.println("Android Version: ${Build.VERSION.RELEASE}")
                writer.println("SDK Version: ${Build.VERSION.SDK_INT}")
                writer.println("Device: ${Build.DEVICE}")
                writer.println("Model: ${Build.MODEL}")
                writer.println("Manufacturer: ${Build.MANUFACTURER}")
                writer.println()
                writer.println("=== Thread ===")
                writer.println("Name: ${thread.name}")
                writer.println("ID: ${thread.id}")
                writer.println("Priority: ${thread.priority}")
                writer.println()
                writer.println("=== Exception ===")
                throwable.printStackTrace(writer)
                writer.println()
                writer.println("=== Cause ===")
                var cause = throwable.cause
                while (cause != null) {
                    writer.println("Cause: ${cause.javaClass.name}")
                    writer.println("Message: ${cause.message}")
                    cause.printStackTrace(writer)
                    cause = cause.cause
                }
            }

            Log.i(TAG, "Crash report saved to: ${crashFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash report: ${e.message}")
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * 获取所有崩溃报告
     */
    fun getCrashReports(): List<File> {
        val crashDir = File(context.cacheDir, CRASH_DIR)
        return if (crashDir.exists()) {
            crashDir.listFiles()?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 清空崩溃报告
     */
    fun clearCrashReports() {
        val crashDir = File(context.cacheDir, CRASH_DIR)
        if (crashDir.exists()) {
            crashDir.listFiles()?.forEach { it.delete() }
        }
    }
}
