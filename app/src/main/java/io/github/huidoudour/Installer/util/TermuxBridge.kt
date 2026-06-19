package io.github.huidoudour.Installer.util

import android.util.Log
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * TermuxBridge - PTY 子进程管理桥接库
 *
 * 封装 [libtermux_bridge.so] 的 JNI 方法，提供类似 Termux-app 的
 * [libtermux.so](https://github.com/termux/termux-app/wiki/Termux-Libraries)
 * 的 PTY (伪终端) 子进程创建与管理功能。
 *
 * 参考:
 *   - com.termux.terminal.JNI (termux-app/terminal-emulator)
 *   - termux-app/terminal-emulator/src/main/jni/termux.c
 *
 * 功能:
 *   - createSubprocess: 创建 PTY 子进程，返回 PTY master FD 和 PID
 *   - setPtyWindowSize: 设置 PTY 窗口尺寸
 *   - waitFor: 等待子进程结束
 *   - readFromPty / writeToPty: PTY 读写
 *   - isAlive: 检查进程存活
 */
object TermuxBridge {

    private const val TAG = "TermuxBridge"

    /**
     * PTY 子进程信息
     */
    data class PtyProcess(
        val ptyFd: Int,
        val pid: Int,
        val inputStream: PtyInputStream,
        val outputStream: PtyOutputStream
    )

    /**
     * PTY 输入流 - 从 PTY master FD 读取子进程输出
     */
    class PtyInputStream(private val fd: Int) : java.io.InputStream() {
        private val singleByte = ByteArray(1)

        override fun read(): Int {
            val n = read(singleByte, 0, 1)
            return if (n > 0) singleByte[0].toInt() and 0xFF else -1
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return nativeReadFromPty(fd, buffer, offset, length, 1000)
        }

        /**
         * 非阻塞读取（无超时等待）
         */
        fun readNonBlocking(buffer: ByteArray, offset: Int, length: Int): Int {
            return nativeReadFromPty(fd, buffer, offset, length, 0)
        }

        /**
         * 带超时的读取
         */
        fun readWithTimeout(buffer: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int {
            return nativeReadFromPty(fd, buffer, offset, length, timeoutMs)
        }

        override fun close() {
            // PTY FD 由外部管理
        }
    }

    /**
     * PTY 输出流 - 向 PTY master FD 写入数据（发送到子进程）
     */
    class PtyOutputStream(private val fd: Int) : java.io.OutputStream() {
        override fun write(byte: Int) {
            writeByteArray(byteArrayOf(byte.toByte()), 0, 1)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            writeByteArray(buffer, offset, length)
        }

        private fun writeByteArray(buffer: ByteArray, offset: Int, length: Int) {
            val written = nativeWriteToPty(fd, buffer, offset, length)
            if (written < 0) {
                throw java.io.IOException("Failed to write to PTY (fd=$fd)")
            }
            if (written < length) {
                // 部分写入，继续重试剩余部分
                writeByteArray(buffer, offset + written, length - written)
            }
        }

        override fun close() {
            // PTY FD 由外部管理
        }
    }

    init {
        try {
            System.loadLibrary("termux_bridge")
            Log.i(TAG, "libtermux_bridge.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libtermux_bridge.so: ${e.message}")
        }
    }

    // ========== JNI Native Methods ==========

    /**
     * 创建 PTY 子进程。
     *
     * @param cmd 要执行的命令路径 (如 "sh", "/system/bin/sh")
     * @param cwd 子进程工作目录 (如 "/sdcard", "/data/local/tmp")
     * @param args 命令参数数组 (第一个元素通常是命令本身，如 ["sh", "-c", "ls"])
     * @param envVars 环境变量数组 (如 ["PATH=/system/bin", "HOME=/data/data/..."])
     * @param processId [0] 会被设置为子进程 PID
     * @param rows 终端行数
     * @param cols 终端列数
     * @return PTY master 文件描述符 (int)
     */
    @JvmStatic
    private external fun nativeCreateSubprocess(
        cmd: String,
        cwd: String,
        args: Array<String>,
        envVars: Array<String>?,
        processId: IntArray,
        rows: Int,
        cols: Int
    ): Int

    /** 设置 PTY 窗口尺寸 */
    @JvmStatic
    private external fun nativeSetPtyWindowSize(fd: Int, rows: Int, cols: Int)

    /** 启用 PTY UTF-8 模式 */
    @JvmStatic
    private external fun nativeSetPtyUTF8Mode(fd: Int)

    /** 等待子进程结束，返回退出码 */
    @JvmStatic
    private external fun nativeWaitFor(pid: Int): Int

    /** 关闭文件描述符 */
    @JvmStatic
    private external fun nativeClose(fd: Int)

    /** 从 PTY 读取数据 (支持超时) */
    @JvmStatic
    private external fun nativeReadFromPty(fd: Int, buffer: ByteArray, offset: Int, length: Int, timeoutMs: Int): Int

    /** 向 PTY 写入数据 */
    @JvmStatic
    private external fun nativeWriteToPty(fd: Int, buffer: ByteArray, offset: Int, length: Int): Int

    /** 检查进程是否存活 */
    @JvmStatic
    private external fun nativeIsAlive(pid: Int): Boolean

    /** 设置 FD 非阻塞模式 */
    @JvmStatic
    private external fun nativeSetNonBlocking(fd: Int, nonBlocking: Boolean)

    /** 非阻塞获取退出码 (-2=运行中, -1=错误, >=0=退出码) */
    @JvmStatic
    private external fun nativeGetExitCode(pid: Int): Int

    // ========== 公开 API ==========

    /**
     * 创建 PTY 子进程，返回 [PtyProcess] 包装。
     */
    fun createSubprocess(
        cmd: String,
        cwd: String = "/sdcard",
        args: Array<String> = arrayOf(cmd),
        envVars: Array<String>? = null,
        rows: Int = 24,
        cols: Int = 80
    ): PtyProcess {
        val processId = IntArray(1)
        val ptyFd = nativeCreateSubprocess(cmd, cwd, args, envVars, processId, rows, cols)

        if (ptyFd < 0) {
            throw RuntimeException("Failed to create PTY subprocess (fd=$ptyFd)")
        }

        val pid = processId[0]
        Log.d(TAG, "PTY subprocess created: pid=$pid, fd=$ptyFd")

        val inputStream = PtyInputStream(ptyFd)
        val outputStream = PtyOutputStream(ptyFd)

        return PtyProcess(ptyFd, pid, inputStream, outputStream)
    }

    /**
     * 创建交互式 Shell (sh) 子进程。
     */
    fun createShellSession(
        cwd: String = "/sdcard",
        rows: Int = 24,
        cols: Int = 80
    ): PtyProcess {
        // 使用标准的 sh，直接传递 sh 作为命令
        // 通过 PTY 交互
        val processId = IntArray(1)
        val ptyFd = nativeCreateSubprocess(
            cmd = "sh",
            cwd = cwd,
            args = arrayOf("sh"),
            envVars = null,
            processId = processId,
            rows = rows,
            cols = cols
        )

        if (ptyFd < 0) {
            throw RuntimeException("Failed to create shell PTY session")
        }

        val pid = processId[0]
        Log.d(TAG, "Shell PTY session created: pid=$pid, fd=$ptyFd")

        return PtyProcess(ptyFd, pid, PtyInputStream(ptyFd), PtyOutputStream(ptyFd))
    }

    /**
     * 设置终端窗口尺寸。
     */
    fun setPtyWindowSize(fd: Int, rows: Int, cols: Int) {
        nativeSetPtyWindowSize(fd, rows, cols)
    }

    /**
     * 等待进程结束。
     */
    fun waitFor(pid: Int): Int {
        return nativeWaitFor(pid)
    }

    /**
     * 关闭 PTY FD。
     */
    fun close(fd: Int) {
        nativeClose(fd)
    }

    /**
     * 检查进程是否存活。
     */
    fun isAlive(pid: Int): Boolean {
        return nativeIsAlive(pid)
    }

    /**
     * 非阻塞获取退出码。
     * @return -2=运行中, -1=错误, >=0=退出码
     */
    fun getExitCode(pid: Int): Int {
        return nativeGetExitCode(pid)
    }

    /**
     * 设置非阻塞模式。
     */
    fun setNonBlocking(fd: Int, enabled: Boolean) {
        nativeSetNonBlocking(fd, enabled)
    }
}
