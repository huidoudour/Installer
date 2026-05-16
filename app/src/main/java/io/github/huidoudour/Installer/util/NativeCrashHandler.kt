package io.github.huidoudour.Installer.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 原生崩溃捕获处理器
 * 通过原生信号处理器捕获 SIGABRT/SIGSEGV 等信号，
 * 收集栈追踪和内存信息，写入 app 私有目录 /crash/
 */
public class NativeCrashHandler {

    private static final String TAG = "NativeCrashHandler";
    private static final String CRASH_DIR_NAME = "crash";
    private static final String LIBRARY_NAME = "crash-handler";

    private static Context s_appContext;
    private static boolean s_initialized = false;

    // ==========================================================
    // 静态初始化：加载原生库
    // ==========================================================
    static {
        System.loadLibrary(LIBRARY_NAME);
    }

    // ==========================================================
    // JNI 方法声明
    // ==========================================================

    /** 初始化原生信号处理器，返回是否成功 */
    public static native boolean nativeInit();

    /**
     * 带 class 引用的初始化（推荐）。
     * Java 层传入自己的 class，绕过 native FindClass 在 Android 8+ 的 classloader 限制。
     */
    private static native boolean nativeInitWithClass(Class<?> clazz);

    /** 获取当前线程的栈追踪字符串（供测试用）*/
    public static native String nativeGetStackTrace();

    /** 组装一份崩溃报告 JSON/文本（Java 层调用）*/
    public static native String nativeGetCrashReport(String signalName, String javaTrace);

    /** 当前设备是否支持原生崩溃捕获 */
    public static native boolean isSupported();

    // ==========================================================
    // 初始化
    // ==========================================================

    /**
     * 在 Application.onCreate() 中调用
     */
    public static void init(Context context) {
        if (s_initialized) {
            Log.w(TAG, "Already initialized, skipping.");
            return;
        }
        s_appContext = context.getApplicationContext();
        try {
            // 传入自己的 Class，避免 native FindClass 在 Android 8+ 找不到 app classloader 的类
            boolean ok = nativeInitWithClass(NativeCrashHandler.class);
            s_initialized = ok;
            Log.i(TAG, "Native crash handler init: " + (ok ? "OK" : "FAILED"));
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to init native crash handler", e);
        }
    }

    public static boolean isInitialized() {
        return s_initialized;
    }

    // ==========================================================
    // Java 层崩溃回调（由原生信号处理器调用）
    // 声明为 static，供 JNI 直接调用
    // ==========================================================

    /**
     * 由原生信号处理器回调，运行在捕获到信号的线程上。
     * 拼装完整崩溃报告并写入文件。
     * @param reportText 原生层拼好的报告文本
     */
    @SuppressWarnings("unused")
    public static void onNativeCrash(String reportText) {
        Log.e(TAG, "!!! Native crash detected !!!");
        if (reportText != null) {
            Log.e(TAG, reportText);
        }

        // 补充 Java 层栈（捕获信号时 Java 栈通常没意义，但可以记录）
        StringBuilder fullReport = new StringBuilder();
        fullReport.append(reportText != null ? reportText : "No report from native\n");
        fullReport.append("\n--- Java Stack at signal time ---\n");
        fullReport.append(getJavaStackTrace());

        // 写入文件
        File crashFile = getCrashFile();
        boolean saved = writeCrashFile(crashFile, fullReport.toString());
        Log.i(TAG, "Crash file saved: " + saved + " -> " + crashFile.getAbsolutePath());
    }

    // ==========================================================
    // 文件写入
    // ==========================================================

    private static File getCrashDir() {
        if (s_appContext == null) return null;
        File dir = new File(s_appContext.getFilesDir(), CRASH_DIR_NAME);
        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            Log.d(TAG, "Create crash dir: " + ok + " -> " + dir.getAbsolutePath());
        }
        return dir;
    }

    private static File getCrashFile() {
        File dir = getCrashDir();
        if (dir == null) return null;
        String ts = new SimpleDateFormat("yyyyMMdd_HH-mm-ss", Locale.US).format(new Date());
        return new File(dir, "crash_native_" + ts + ".txt");
    }

    private static boolean writeCrashFile(File file, String content) {
        if (file == null) return false;
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.print(content);
            pw.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write crash file", e);
            return false;
        }
    }

    private static String getJavaStackTrace() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        new Throwable("Java stack at crash time").printStackTrace(pw);
        return sw.toString();
    }

    // ==========================================================
    // 崩溃文件管理（供 UI 调用）
    // ==========================================================

    public static File[] getCrashFiles() {
        File dir = getCrashDir();
        if (dir == null || !dir.exists()) return new File[0];
        File[] files = dir.listFiles((d, name) -> name.startsWith("crash_") && name.endsWith(".txt"));
        if (files == null) return new File[0];
        // 按时间倒序
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    public static void clearCrashFiles() {
        File[] files = getCrashFiles();
        for (File f : files) {
            boolean ok = f.delete();
            Log.d(TAG, "Deleted: " + f.getName() + " -> " + ok);
        }
    }
}
