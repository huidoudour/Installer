/*
 * termux_bridge.c
 *
 * 仿照 Termux-app 的 libtermux.so (terminal-emulator 模块) 实现 PTY 子进程管理。
 *
 * 参考来源:
 *   - https://github.com/termux/termux-app/wiki/Termux-Libraries
 *   - termux-app/terminal-emulator/src/main/jni/termux.c
 *   - termux-app/terminal-emulator/src/main/java/com/termux/terminal/JNI.java
 *
 * 功能:
 *   - createSubprocess: 创建 PTY (伪终端) 子进程
 *   - setPtyWindowSize: 设置 PTY 窗口尺寸
 *   - setPtyUTF8Mode: 启用 PTY UTF-8 模式
 *   - waitFor: 等待子进程结束
 *   - close: 关闭文件描述符
 *   - readFromPty: 从 PTY 读取数据 (非阻塞 + 超时)
 *   - writeToPty: 向 PTY 写入数据
 *   - isAlive: 检查进程是否存活
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <sys/select.h>
#include <signal.h>
#include <dirent.h>
#include <errno.h>

#define TERMUX_UNUSED(x) x __attribute__((__unused__))

// ========== 工具函数 ==========

static int throw_runtime_exception(JNIEnv* env, const char* message) {
    jclass exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (exClass != NULL) {
        (*env)->ThrowNew(env, exClass, message);
    }
    return -1;
}

// ========== createSubprocess ==========
// 创建 PTY 子进程
// 返回: PTY master 文件描述符
// processId[0] 会被设置为子进程 PID

static int create_subprocess_internal(
    JNIEnv* env,
    const char* cmd,
    const char* cwd,
    char* const argv[],
    char** envp,
    int* pProcessId,
    jint rows,
    jint cols)
{
    // 打开 PTY master
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) {
        return throw_runtime_exception(env, "Cannot open /dev/ptmx");
    }

    char devname[64];
    if (grantpt(ptm) || unlockpt(ptm) || ptsname_r(ptm, devname, sizeof(devname))) {
        close(ptm);
        return throw_runtime_exception(env, "grantpt()/unlockpt()/ptsname_r() failed on /dev/ptmx");
    }

    // 设置 UTF-8 模式，禁用流控制 (防止 Ctrl+S 锁屏)
    struct termios tios;
    tcgetattr(ptm, &tios);
    tios.c_iflag |= IUTF8;
    tios.c_iflag &= ~(IXON | IXOFF);
    tcsetattr(ptm, TCSANOW, &tios);

    // 设置初始窗口大小
    struct winsize sz;
    memset(&sz, 0, sizeof(sz));
    sz.ws_row = (unsigned short)rows;
    sz.ws_col = (unsigned short)cols;
    sz.ws_xpixel = (unsigned short)(cols * 8);  // 默认字符宽度 8px
    sz.ws_ypixel = (unsigned short)(rows * 16); // 默认字符高度 16px
    ioctl(ptm, TIOCSWINSZ, &sz);

    pid_t pid = fork();
    if (pid < 0) {
        close(ptm);
        return throw_runtime_exception(env, "Fork failed");
    }

    if (pid > 0) {
        // 父进程: 返回 PTY FD 和 PID
        *pProcessId = (int)pid;
        return ptm;
    }

    // ======== 子进程 ========
    // 解除 Android Java 进程可能阻塞的信号
    sigset_t signals_to_unblock;
    sigfillset(&signals_to_unblock);
    sigprocmask(SIG_UNBLOCK, &signals_to_unblock, NULL);

    close(ptm);
    setsid();

    int pts = open(devname, O_RDWR);
    if (pts < 0) _exit(-1);

    // 将 stdin/stdout/stderr 重定向到 PTY slave
    dup2(pts, 0);
    dup2(pts, 1);
    dup2(pts, 2);

    // 关闭所有多余 FD
    DIR* self_dir = opendir("/proc/self/fd");
    if (self_dir != NULL) {
        int self_dir_fd = dirfd(self_dir);
        struct dirent* entry;
        while ((entry = readdir(self_dir)) != NULL) {
            int fd = atoi(entry->d_name);
            if (fd > 2 && fd != self_dir_fd) {
                close(fd);
            }
        }
        closedir(self_dir);
    }

    // 清理并设置环境变量
    clearenv();
    if (envp) {
        for (char** p = envp; *p; ++p) {
            putenv(*p);
        }
    }

    if (cwd && *cwd) {
        if (chdir(cwd) != 0) {
            fprintf(stderr, "chdir(\"%s\"): %s\n", cwd, strerror(errno));
            fflush(stderr);
        }
    }

    execvp(cmd, argv);

    // exec 失败
    fprintf(stderr, "exec(\"%s\"): %s\n", cmd, strerror(errno));
    fflush(stderr);
    _exit(1);
}

JNIEXPORT jint JNICALL
Java_io_github_huidoudour_Installer_util_TermuxBridge_nativeCreateSubprocess(
    JNIEnv* env,
    jclass TERMUX_UNUSED(clazz),
    jstring cmd,
    jstring cwd,
    jobjectArray args,
    jobjectArray envVars,
    jintArray processIdArray,
    jint rows,
    jint cols)
{
    // 转换 cmd
    const char* cmd_utf8 = (*env)->GetStringUTFChars(env, cmd, NULL);
    if (!cmd_utf8) return throw_runtime_exception(env, "GetStringUTFChars(cmd) failed");

    // 转换 cwd
    const char* cwd_utf8 = (*env)->GetStringUTFChars(env, cwd, NULL);
    if (!cwd_utf8) {
        (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf8);
        return throw_runtime_exception(env, "GetStringUTFChars(cwd) failed");
    }

    // 转换 args
    jsize args_count = args ? (*env)->GetArrayLength(env, args) : 0;
    char** argv = NULL;
    if (args_count > 0) {
        argv = (char**)malloc((size_t)(args_count + 1) * sizeof(char*));
        if (!argv) {
            (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf8);
            (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf8);
            return throw_runtime_exception(env, "malloc(argv) failed");
        }
        for (jsize i = 0; i < args_count; i++) {
            jstring arg_java = (jstring)(*env)->GetObjectArrayElement(env, args, i);
            const char* arg_utf8 = (*env)->GetStringUTFChars(env, arg_java, NULL);
            if (!arg_utf8) {
                // 清理已分配的内存
                for (jsize j = 0; j < i; j++) free(argv[j]);
                free(argv);
                (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf8);
                (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf8);
                return throw_runtime_exception(env, "GetStringUTFChars(argv) failed");
            }
            argv[i] = strdup(arg_utf8);
            (*env)->ReleaseStringUTFChars(env, arg_java, arg_utf8);
        }
        argv[args_count] = NULL;
    }

    // 转换 envVars
    jsize env_count = envVars ? (*env)->GetArrayLength(env, envVars) : 0;
    char** envp = NULL;
    if (env_count > 0) {
        envp = (char**)malloc((size_t)(env_count + 1) * sizeof(char*));
        if (!envp) {
            if (argv) {
                for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
                free(argv);
            }
            (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf8);
            (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf8);
            return throw_runtime_exception(env, "malloc(envp) failed");
        }
        for (jsize i = 0; i < env_count; i++) {
            jstring env_java = (jstring)(*env)->GetObjectArrayElement(env, envVars, i);
            const char* env_utf8 = (*env)->GetStringUTFChars(env, env_java, NULL);
            if (!env_utf8) {
                for (jsize j = 0; j < i; j++) free(envp[j]);
                free(envp);
                if (argv) {
                    for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
                    free(argv);
                }
                (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf8);
                (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf8);
                return throw_runtime_exception(env, "GetStringUTFChars(env) failed");
            }
            envp[i] = strdup(env_utf8);
            (*env)->ReleaseStringUTFChars(env, env_java, env_utf8);
        }
        envp[env_count] = NULL;
    }

    int procId = 0;
    int ptm = create_subprocess_internal(env, cmd_utf8, cwd_utf8, argv, envp, &procId, rows, cols);

    (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf8);
    (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf8);

    if (argv) {
        for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
        free(argv);
    }
    if (envp) {
        for (char** tmp = envp; *tmp; ++tmp) free(*tmp);
        free(envp);
    }

    // 写入 processId
    jint* pProcId = (*env)->GetPrimitiveArrayCritical(env, processIdArray, NULL);
    if (pProcId) {
        *pProcId = procId;
        (*env)->ReleasePrimitiveArrayCritical(env, processIdArray, pProcId, 0);
    }

    return ptm;
}

// ========== setPtyWindowSize ==========

JNIEXPORT void JNICALL
Java_io_github_huidoudour_Installer_util_TermuxBridge_nativeSetPtyWindowSize(
    JNIEnv* TERMUX_UNUSED(env),
    jclass TERMUX_UNUSED(clazz),
    jint fd,
    jint rows,
    jint cols)
{
    struct winsize sz;
    memset(&sz, 0, sizeof(sz));
    sz.ws_row = (unsigned short)rows;
    sz.ws_col = (unsigned short)cols;
    sz.ws_xpixel = (unsigned short)(cols * 8);
    sz.ws_ypixel = (unsigned short)(rows * 16);
    ioctl((int)fd, TIOCSWINSZ, &sz);
}

// ========== setPtyUTF8Mode ==========

JNIEXPORT void JNICALL
Java_io_github_huidoudour_Installer_util_TermuxBridge_nativeSetPtyUTF8Mode(
    JNIEnv* TERMUX_UNUSED(env),
    jclass TERMUX_UNUSED(clazz),
    jint fd)
{
    struct termios tios;
    tcgetattr((int)fd, &tios);
    if ((tios.c_iflag & IUTF8) == 0) {
        tios.c_iflag |= IUTF8;
        tcsetattr((int)fd, TCSANOW, &tios);
    }
}

// ========== waitFor ==========

JNIEXPORT jint JNICALL
Java_io_github_huidoudour_Installer_util_TermuxBridge_nativeWaitFor(
    JNIEnv* TERMUX_UNUSED(env),
    jclass TERMUX_UNUSED(clazz),
    jint pid)
{
    int status;
    waitpid((pid_t)pid, &status, 0);
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return -WTERMSIG(status);
    }
    return 0;
}

// ========== close ==========

JNIEXPORT void JNICALL
Java_io_github_huidoudour_Installer_util_TermuxBridge_nativeClose(
    JNIEnv* TERMUX_UNUSED(env),
    jclass TERMUX_UNUSED(clazz),
    jint fd)
{
    close((int)fd);
}

// ========== readFromPty ==========
// 从 PTY FD 读取数据，支持超时 (timeoutMs 毫秒)
// 返回: 实际读取的字节数，-1 表示错误

JNIEXPORT jint JNICALL
Java_io_github_huidoudour_Installer_util_TermuxBridge_nativeReadFromPty(
    JNIEnv* env,
    jclass TERMUX_UNUSED(clazz),
    jint fd,
    jbyteArray buffer,
    jint offset,
    jint length,
    jint timeoutMs)
{
    // 使用 select 实现超时
    if (timeoutMs > 0) {
        fd_set read_fds;
        FD_ZERO(&read_fds);
        FD_SET((int)fd, &read_fds);

        struct timeval tv;
        tv.tv_sec = timeoutMs / 1000;
        tv.tv_usec = (timeoutMs % 1000) * 1000;

        int ret = select((int)fd + 1, &read_fds, NULL, NULL, &tv);
        if (ret < 0) {
            return -2; // select error
        }
        if (ret == 0) {
            return 0; // timeout, no data
        }
    }

    // 读取数据
    jbyte* native_buffer = (*env)->GetPrimitiveArrayCritical(env, buffer, NULL);
    if (!native_buffer) return -1;

    ssize_t bytes_read = read((int)fd, native_buffer + offset, (size_t)length);
    (*env)->ReleasePrimitiveArrayCritical(env, buffer, native_buffer, JNI_ABORT);

    if (bytes_read < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0;
        }
        return -1;
    }

    // 需要将数据复制回 Java
    (*env)->SetByteArrayRegion(env, buffer, offset, (jsize)bytes_read, native_buffer + offset);

    return (jint)bytes_read;
}

// ========== writeToPty ==========

JNIEXPORT jint JNICALL
Java_io_github_huidoudour_Installer_util_TermuxBridge_nativeWriteToPty(
    JNIEnv* env,
    jclass TERMUX_UNUSED(clazz),
    jint fd,
    jbyteArray buffer,
    jint offset,
    jint length)
{
    jbyte* native_buffer = (*env)->GetPrimitiveArrayCritical(env, buffer, NULL);
    if (!native_buffer) return -1;

    ssize_t bytes_written = write((int)fd, native_buffer + offset, (size_t)length);
    (*env)->ReleasePrimitiveArrayCritical(env, buffer, native_buffer, JNI_ABORT);

    if (bytes_written < 0) {
        return -1;
    }
    return (jint)bytes_written;
}

// ========== isAlive ==========
// 检查进程是否存活

JNIEXPORT jboolean JNICALL
Java_io_github_huidoudour_Installer_util_TermuxBridge_nativeIsAlive(
    JNIEnv* TERMUX_UNUSED(env),
    jclass TERMUX_UNUSED(clazz),
    jint pid)
{
    // kill with signal 0 仅检查进程是否存在
    return (kill((pid_t)pid, 0) == 0) ? JNI_TRUE : JNI_FALSE;
}

// ========== setNonBlocking ==========

JNIEXPORT void JNICALL
Java_io_github_huidoudour_Installer_util_TermuxBridge_nativeSetNonBlocking(
    JNIEnv* TERMUX_UNUSED(env),
    jclass TERMUX_UNUSED(clazz),
    jint fd,
    jboolean nonBlocking)
{
    int flags = fcntl((int)fd, F_GETFL, 0);
    if (nonBlocking) {
        fcntl((int)fd, F_SETFL, flags | O_NONBLOCK);
    } else {
        fcntl((int)fd, F_SETFL, flags & ~O_NONBLOCK);
    }
}

// ========== getExitCode ==========
// 获取进程退出码 (非阻塞, 检查 WNOHANG)

JNIEXPORT jint JNICALL
Java_io_github_huidoudour_Installer_util_TermuxBridge_nativeGetExitCode(
    JNIEnv* TERMUX_UNUSED(env),
    jclass TERMUX_UNUSED(clazz),
    jint pid)
{
    int status;
    pid_t result = waitpid((pid_t)pid, &status, WNOHANG);
    if (result == 0) {
        return -2; // 进程仍在运行
    }
    if (result < 0) {
        return -1; // 错误
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return -WTERMSIG(status);
    }
    return 0;
}
