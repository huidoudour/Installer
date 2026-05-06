// crash_handler.cpp - 原生崩溃捕获（信号处理器版本 v2）
#include <jni.h>
#include <string>
#include <android/log.h>
#include <csignal>
#include <csetjmp>
#include <ctime>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <unwind.h>
#include <sys/syscall.h>
#include <errno.h>

#define LOG_TAG "NativeCrash"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ============================================================
// 全局 JNI 上下文（nativeInit 时缓存，信号处理器直接使用）
// ============================================================
static JavaVM*  g_jvm    = nullptr;
static jclass   g_clazz  = nullptr;   // 缓存的 jclass（全局引用）
static jmethodID g_on_crash_mid = nullptr;

// ============================================================
// 栈追踪：用 _Unwind_Backtrace（不依赖 backtrace/backtrace_symbols）
// ============================================================
struct UnwindCtx {
    char*  buf;
    size_t  rem;
    int     count;
    int     max;
};

static _Unwind_Reason_Code unwind_cb(_Unwind_Context* ctx, void* v) {
    UnwindCtx* u = static_cast<UnwindCtx*>(v);
    if (u->count >= u->max || u->rem < 32) return _URC_END_OF_STACK;
    uintptr_t pc = _Unwind_GetIP(ctx);
    if (pc == 0) return _URC_END_OF_STACK;
    int n = snprintf(u->buf, u->rem, "#%02d  pc %p\n", u->count++, (void*)pc);
    if (n > 0) { u->buf += n; u->rem -= static_cast<size_t>(n); }
    return _URC_NO_REASON;
}

static void get_stack_trace(char* buf, size_t sz, int max_frames) {
    if (!buf || sz == 0) return;
    buf[0] = '\0';
    UnwindCtx u;
    u.buf   = buf;
    u.rem   = sz;
    u.count = 0;
    u.max   = max_frames > 0 ? max_frames : 32;
    _Unwind_Backtrace(unwind_cb, &u);
}

// ============================================================
// 信号处理器
// ============================================================
static void signal_handler(int sig, siginfo_t* /*info*/, void* /*uctx*/) {
    // 拼装报告文本
    char ts[64] = {0};
    time_t now = time(nullptr);
    struct tm tm_val;
    localtime_r(&now, &tm_val);
    strftime(ts, sizeof(ts), "%Y-%m-%d %H:%M:%S", &tm_val);

    const char* sn = "?";
    switch (sig) {
        case SIGABRT: sn = "SIGABRT"; break;
        case SIGSEGV: sn = "SIGSEGV"; break;
        case SIGILL:  sn = "SIGILL";  break;
        case SIGFPE:  sn = "SIGFPE";  break;
        case SIGBUS:  sn = "SIGBUS";  break;
        case SIGTRAP: sn = "SIGTRAP"; break;
    }

    char stack[4096] = {0};
    get_stack_trace(stack, sizeof(stack), 32);

    char report[8192];
    snprintf(report, sizeof(report),
        "========================================\n"
        "Native Crash Report\n"
        "========================================\n"
        "Time:      %s\n"
        "Signal:    %s\n"
        "PID/TID:   %d / %d\n"
        "========================================\n"
        "Stack Trace:\n"
        "%s"
        "========================================\n",
        ts, sn,
        getpid(), (int)syscall(SYS_gettid),
        stack
    );

    // 回调 Java 层（通过缓存的 jclass + static method）
    JNIEnv* env = nullptr;
    bool did_attach = false;
    if (g_jvm) {
        jint status = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (status == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                did_attach = true;
            } else {
                env = nullptr;
            }
        }
        if (env && g_clazz && g_on_crash_mid) {
            jstring jreport = env->NewStringUTF(report);
            if (jreport) {
                env->CallStaticVoidMethod(g_clazz, g_on_crash_mid, jreport);
                env->DeleteLocalRef(jreport);
            }
        }
        if (did_attach) g_jvm->DetachCurrentThread();
    }

    // 恢复默认处理器，重新抛信号让系统生成 tombstone
    struct sigaction sa_def;
    memset(&sa_def, 0, sizeof(sa_def));
    sa_def.sa_handler = SIG_DFL;
    sigaction(sig, &sa_def, nullptr);
    raise(sig);
}

// ============================================================
// JNI 接口
// ============================================================

// 推荐的初始化方式：Java 层传入自己的 jclass，避免 FindClass 在 Android 8+ 找不到 app classloader 的类
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_huidoudour_installer_util_NativeCrashHandler_nativeInitWithClass(
        JNIEnv* env, jobject thiz, jclass clazz) {
    (void)thiz; // JNI callback, thiz not needed
    LOGI("nativeInitWithClass: registering signal handlers");

    // 缓存 JavaVM
    if (env->GetJavaVM(&g_jvm) != JNI_OK) {
        LOGE("Failed to get JavaVM");
        return JNI_FALSE;
    }

    // 缓存 jclass（Java 层已保证是 app classloader 的 class，直接建全局引用）
    g_clazz = (jclass)env->NewGlobalRef(clazz);
    if (!g_clazz) { LOGE("Failed to create global ref for class"); return JNI_FALSE; }

    // 缓存 static 方法 ID
    g_on_crash_mid = env->GetStaticMethodID(g_clazz, "onNativeCrash", "(Ljava/lang/String;)V");
    if (!g_on_crash_mid) { LOGE("Failed to find onNativeCrash static method"); return JNI_FALSE; }

    // 注册信号
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = signal_handler;
    sa.sa_flags     = SA_SIGINFO;
    sigemptyset(&sa.sa_mask);

    // 使用独立信号栈（防止栈溢出时信号处理器也无法运行）
    static uint8_t sig_stack[SIGSTKSZ];
    stack_t ss;
    ss.ss_sp    = sig_stack;
    ss.ss_size  = SIGSTKSZ;
    ss.ss_flags = 0;
    sigaltstack(&ss, nullptr);

    sigaction(SIGABRT, &sa, nullptr);
    sigaction(SIGSEGV, &sa, nullptr);
    sigaction(SIGILL,  &sa, nullptr);
    sigaction(SIGFPE,  &sa, nullptr);
    sigaction(SIGBUS,  &sa, nullptr);

    LOGI("Signal handlers registered: SIGABRT SIGSEGV SIGILL SIGFPE SIGBUS");
    return JNI_TRUE;
}

// 旧版初始化（向后兼容，不推荐）：在 Android 8+ 上可能因 FindClass 找不到类而失败
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_huidoudour_installer_util_NativeCrashHandler_nativeInit(
        JNIEnv* env, jobject thiz) {
    (void)thiz; // JNI callback, thiz not needed
    // 通过 thiz 获取 class（等价于 thiz.getClass()），绕过 FindClass
    jclass clazz = env->GetObjectClass(thiz);
    if (!clazz) { LOGE("Failed to get class from thiz"); return JNI_FALSE; }
    return Java_io_github_huidoudour_installer_util_NativeCrashHandler_nativeInitWithClass(
            env, thiz, clazz);
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_huidoudour_installer_util_NativeCrashHandler_nativeGetStackTrace(
        JNIEnv* env, jclass /*clazz*/) {
    char buf[4096] = {0};
    get_stack_trace(buf, sizeof(buf), 32);
    return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_huidoudour_installer_util_NativeCrashHandler_nativeGetCrashReport(
        JNIEnv* env, jclass /*clazz*/,
        jstring jsignal, jstring jstack) {
    const char* sig   = jsignal ? env->GetStringUTFChars(jsignal, nullptr) : nullptr;
    const char* stack = jstack  ? env->GetStringUTFChars(jstack,  nullptr) : nullptr;

    char ts[64] = {0};
    time_t now = time(nullptr);
    struct tm tm_val;
    localtime_r(&now, &tm_val);
    strftime(ts, sizeof(ts), "%Y-%m-%d %H:%M:%S", &tm_val);

    char report[8192];
    snprintf(report, sizeof(report),
        "========================================\n"
        "Native Crash Report\n"
        "========================================\n"
        "Time:      %s\n"
        "Signal:    %s\n"
        "PID:       %d\n"
        "========================================\n"
        "Stack Trace (Java):\n"
        "%s"
        "========================================\n",
        ts,
        sig ? sig : "?",
        getpid(),
        stack ? stack : "?"
    );

    if (sig)   env->ReleaseStringUTFChars(jsignal, sig);
    if (stack) env->ReleaseStringUTFChars(jstack,  stack);
    return env->NewStringUTF(report);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_huidoudour_installer_util_NativeCrashHandler_isSupported(
        JNIEnv* /*env*/, jclass /*clazz*/) {
    return JNI_TRUE;
}
