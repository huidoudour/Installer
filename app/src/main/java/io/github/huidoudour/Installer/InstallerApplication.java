package io.github.huidoudour.Installer;

import android.app.Application;
import android.util.Log;
import android.content.Context;

import io.github.huidoudour.Installer.util.LanguageManager;
import io.github.huidoudour.Installer.util.NativeCrashHandler;
import io.github.huidoudour.Installer.util.NotificationHelper;
import org.acra.ACRA;
import org.acra.config.ACRAConfigurationException;
import org.acra.data.CrashReportData;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderException;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义Application类
 * 用于全局应用语言设置和崩溃报告
 */
public class InstallerApplication extends Application {
    private static final String TAG = "InstallerApplication";
    
    // 崩溃日志目录
    private static final String CRASH_DIR = "crash_logs";

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(base);
        
        // 初始化 ACRA（使用默认配置）
        ACRA.init(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 在应用启动时保存系统原始语言（只保存一次）
        LanguageManager.saveSystemDefaultLanguage(this);
        
        // 初始化原生崩溃捕获（SIGABRT/SIGSEGV 等信号）
        try {
            NativeCrashHandler.init(this);
        } catch (Throwable t) {
            Log.w(TAG, "NativeCrashHandler init failed (non-critical): " + t.getMessage());
        }
        
        // 设置全局未捕获异常处理器
        setupGlobalExceptionHandler();
        
        // 检查 ACRA 状态
        checkAndLogACRAStatus();

        // 应用用户选择的语言设置
        try {
            LanguageManager.applyUserLanguagePreference(this);
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.apply_language_preference_failed, e.getMessage()));
        }
        
        // 初始化通知渠道
        try {
            NotificationHelper.createNotificationChannels(this);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create notification channels: " + e.getMessage());
        }
    }
    
    /**
     * 设置全局未捕获异常处理器
     * 必须在 ACRA 之后设置，这样我们包装 ACRA 的 handler，
     * 确保崩溃时先写本地文件，再交给 ACRA 处理。
     */
    private void setupGlobalExceptionHandler() {
        // 获取当前的 handler（此时是 ACRA 安装的）
        final UncaughtExceptionHandler acraHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                // 1. 先保存到本地文件（保证一定能写到磁盘）
                try {
                    saveCrashLog(ex);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save crash log: " + e.getMessage());
                }

                // 2. 再交给 ACRA（或系统）处理
                if (acraHandler != null) {
                    acraHandler.uncaughtException(thread, ex);
                } else {
                    // 兜底：直接让系统杀进程
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            }
        });
    }
    
    /**
     * 保存崩溃日志到应用私有目录
     */
    private void saveCrashLog(Throwable ex) {
        File crashDir = new File(getFilesDir(), CRASH_DIR);
        if (!crashDir.exists()) {
            crashDir.mkdirs();
        }
        
        String timestamp = String.valueOf(System.currentTimeMillis());
        File crashFile = new File(crashDir, "crash_" + timestamp + ".txt");
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(crashFile))) {
            // 写入基本信息
            writer.println("========================================");
            writer.println("Crash Report - " + timestamp);
            writer.println("========================================");
            writer.println();
            
            // 应用信息
            writer.println("App: " + getString(R.string.app_name));
            writer.println("Version: " + getAppVersion());
            writer.println("Android: " + android.os.Build.VERSION.RELEASE);
            writer.println("SDK: " + android.os.Build.VERSION.SDK_INT);
            writer.println("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            writer.println();
            
            // 崩溃信息
            writer.println("========================================");
            writer.println("Exception Details:");
            writer.println("========================================");
            
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            writer.println(sw.toString());
            
            // 写入设备状态
            writer.println();
            writer.println("========================================");
            writer.println("Device Info:");
            writer.println("========================================");
            writer.println("Brand: " + android.os.Build.BRAND);
            writer.println("Device: " + android.os.Build.DEVICE);
            writer.println("Product: " + android.os.Build.PRODUCT);
            writer.println("Board: " + android.os.Build.BOARD);
            writer.println("Hardware: " + android.os.Build.HARDWARE);
            writer.println("ABI: " + android.os.Build.SUPPORTED_ABIS[0]);
            
            writer.flush();
            Log.i(TAG, "Crash log saved to: " + crashFile.getAbsolutePath());
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to write crash log: " + e.getMessage());
        }
    }
    
    /**
     * 获取应用版本信息
     */
    private String getAppVersion() {
        try {
            android.content.pm.PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName + " (" + packageInfo.versionCode + ")";
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "Unknown";
        }
    }
    
    /**
     * 获取崩溃日志目录
     */
    public static File getCrashLogDirectory(Context context) {
        return new File(context.getFilesDir(), CRASH_DIR);
    }
    
    /**
     * 获取所有崩溃日志文件（按时间倒序）
     */
    public static File[] getCrashLogFiles(Context context) {
        File crashDir = getCrashLogDirectory(context);
        if (crashDir.exists()) {
            File[] files = crashDir.listFiles((dir, name) -> name.startsWith("crash_") && name.endsWith(".txt"));
            if (files != null && files.length > 0) {
                java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                return files;
            }
        }
        return new File[0];
    }
    
    /**
     * 删除所有崩溃日志
     */
    public static void clearCrashLogs(Context context) {
        File[] files = getCrashLogFiles(context);
        for (File file : files) {
            if (file.delete()) {
                Log.d(TAG, "Deleted crash log: " + file.getName());
            }
        }
    }
    
    /**
     * 检查并记录 ACRA 状态
     */
    private void checkAndLogACRAStatus() {
        try {
            boolean isDebug = (0 != (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE));
            
            if (!isDebug) {
                Log.i(TAG, "ACRA initialized - crash reporting enabled");
            } else {
                Log.i(TAG, "ACRA initialized - crash reporting active (debug mode)");
            }
            
            // 在 debug 模式下输出崩溃日志目录
            if (isDebug) {
                File crashDir = getCrashLogDirectory(this);
                Log.i(TAG, "Crash log directory: " + crashDir.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to check ACRA status: " + e.getMessage());
        }
    }
}