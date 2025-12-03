package io.github.huidoudour.Installer.utils;

import android.util.Log;

/**
 * JNI 原生库辅助类
 * 
 * 功能:
 * - 加载 C++ 共享库
 * - 提供原生方法接口
 * - 性能测试和系统信息查询
 * 
 * 支持的架构:
 * - arm64-v8a
 * - x86_64
 * 
 * 16KB 页面对齐支持: ✅
 */
public class NativeHelper {
    
    private static final String TAG = "NativeHelper";
    private static final String LIBRARY_NAME = "installer-native";
    
    // 原生库加载状态
    private static boolean sNativeLibraryLoaded = false;
    private static String sLoadError = null;
    
    // 静态加载原生库
    static {
        try {
            System.loadLibrary(LIBRARY_NAME);
            sNativeLibraryLoaded = true;
            Log.i(TAG, "✅ Native library loaded successfully: " + LIBRARY_NAME);
        } catch (UnsatisfiedLinkError e) {
            sNativeLibraryLoaded = false;
            sLoadError = e.getMessage();
            Log.e(TAG, "❌ Failed to load native library: " + LIBRARY_NAME, e);
        }
    }
    
    /**
     * 检查原生库是否可用
     */
    public static boolean isNativeLibraryAvailable() {
        return sNativeLibraryLoaded;
    }
    
    /**
     * 获取加载错误信息
     */
    public static String getLoadError() {
        return sLoadError;
    }
    
    // ========== 原生方法声明 ==========
    
    /**
     * 获取原生库版本
     */
    public native String getNativeVersion();
    
    /**
     * 获取 CPU 架构
     */
    public native String getCPUArchitecture();
    
    /**
     * 计算简单哈希 (演示用)
     * 注意: 仅用于性能对比,实际请使用 OpenSSL 或 conscrypt
     */
    public native String calculateSimpleHash(String input);
    
    /**
     * 性能测试
     * @param iterations 迭代次数
     * @return 耗时(微秒)
     */
    public native long performanceTest(int iterations);
    
    /**
     * 验证原生库是否正确加载
     */
    public native boolean isNativeLibraryLoaded();
    
    /**
     * 获取构建信息
     */
    public native String getBuildInfo();
    
    // ========== 辅助方法 ==========
    
    /**
     * 获取原生库详细信息
     */
    public String getLibraryInfo() {
        if (!sNativeLibraryLoaded) {
            return "❌ Native library not loaded\nError: " + sLoadError;
        }
        
        StringBuilder info = new StringBuilder();
        info.append("📦 Native Library Information\n\n");
        info.append("Library Name: ").append(LIBRARY_NAME).append("\n");
        info.append("Version: ").append(getNativeVersion()).append("\n");
        info.append("CPU Architecture: ").append(getCPUArchitecture()).append("\n");
        info.append("Status: ").append(isNativeLibraryLoaded() ? "✅ Loaded" : "❌ Error").append("\n\n");
        info.append("Build Information:\n").append(getBuildInfo());
        
        return info.toString();
    }
    
    /**
     * 运行性能对比测试 (Java vs C++)
     */
    public String runPerformanceComparison() {
        if (!sNativeLibraryLoaded) {
            return "❌ Native library not available";
        }
        
        int iterations = 10_000_000; // 一千万次迭代
        
        // Java 实现
        long javaStart = System.nanoTime();
        long javaResult = 0;
        for (int i = 0; i < iterations; i++) {
            javaResult += i * i;
        }
        long javaTime = (System.nanoTime() - javaStart) / 1000; // 转换为微秒
        
        // C++ 实现
        long nativeTime = performanceTest(iterations);
        
        // 计算加速比
        double speedup = (double) javaTime / nativeTime;
        
        StringBuilder result = new StringBuilder();
        result.append("🚀 Performance Comparison\n\n");
        result.append("Iterations: ").append(String.format("%,d", iterations)).append("\n\n");
        result.append("Java Time: ").append(String.format("%,d", javaTime)).append(" μs\n");
        result.append("C++ Time: ").append(String.format("%,d", nativeTime)).append(" μs\n\n");
        result.append("Speedup: ").append(String.format("%.2fx", speedup)).append("\n");
        
        if (speedup > 1.0) {
            result.append("✅ Native is faster!");
        } else if (speedup < 1.0) {
            result.append("⚠️ Java is faster (unusual)");
        } else {
            result.append("➖ Same performance");
        }
        
        return result.toString();
    }
    
    /**
     * 测试哈希计算性能
     */
    public String testHashPerformance() {
        if (!sNativeLibraryLoaded) {
            return "❌ Native library not available";
        }
        
        String testData = "Hello, Android Native Library! This is a test string for hash calculation.";
        int iterations = 100_000;
        
        // Java 哈希 (简单实现)
        long javaStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            int hash = testData.hashCode();
        }
        long javaTime = (System.nanoTime() - javaStart) / 1000;
        
        // Native 哈希
        long nativeStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String hash = calculateSimpleHash(testData);
        }
        long nativeTime = (System.nanoTime() - nativeStart) / 1000;
        
        double speedup = (double) javaTime / nativeTime;
        
        StringBuilder result = new StringBuilder();
        result.append("🔐 Hash Performance Test\n\n");
        result.append("Iterations: ").append(String.format("%,d", iterations)).append("\n");
        result.append("Test Data: \"").append(testData).append("\"\n\n");
        result.append("Java Time: ").append(String.format("%,d", javaTime)).append(" μs\n");
        result.append("Native Time: ").append(String.format("%,d", nativeTime)).append(" μs\n\n");
        result.append("Speedup: ").append(String.format("%.2fx", speedup));
        
        return result.toString();
    }
}
