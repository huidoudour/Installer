package io.github.huidoudour.Installer.utils;

/**
 * Native Helper - C++共享库(.so)封装类
 * 
 * 功能演示：
 * 1. SHA-256哈希计算（使用原生C++实现，性能优于Java）
 * 2. 系统信息获取
 * 3. 性能测试
 * 
 * 使用场景：
 * - APK签名验证
 * - 文件完整性校验
 * - 高性能加密计算
 */
public class NativeHelper {
    
    // 加载原生库
    static {
        try {
            System.loadLibrary("installer-native");
            nativeLibraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            nativeLibraryLoaded = false;
            e.printStackTrace();
        }
    }
    
    private static boolean nativeLibraryLoaded = false;
    
    /**
     * 检查原生库是否加载成功
     */
    public static boolean isNativeLibraryAvailable() {
        return nativeLibraryLoaded;
    }
    
    /**
     * 计算字符串的SHA-256哈希值（原生C++实现）
     * 
     * @param input 输入字符串
     * @return 十六进制格式的哈希值，如果原生库未加载则返回null
     */
    public native String calculateSHA256(String input);
    
    /**
     * 获取原生库版本信息
     */
    public native String getNativeVersion();
    
    /**
     * 获取CPU架构信息
     */
    public native String getCPUArchitecture();
    
    /**
     * 性能测试：批量哈希计算
     * 
     * @param count 计算次数
     * @return 执行时间(毫秒)
     */
    public native long performanceTest(int count);
    
    /**
     * Java实现的SHA-256（用于对比性能）
     */
    public static String calculateSHA256Java(String input) {
        try {
            java.security.MessageDigest digest = 
                java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 获取库信息摘要
     */
    public String getLibraryInfo() {
        if (!nativeLibraryLoaded) {
            return "Native library not loaded";
        }
        
        StringBuilder info = new StringBuilder();
        info.append("Native Library Information:\n");
        info.append("Version: ").append(getNativeVersion()).append("\n");
        info.append("CPU: ").append(getCPUArchitecture()).append("\n");
        info.append("Status: Available\n");
        
        return info.toString();
    }
    
    /**
     * 运行性能对比测试
     */
    public String runPerformanceComparison() {
        if (!nativeLibraryLoaded) {
            return "Native library not available";
        }
        
        StringBuilder result = new StringBuilder();
        int testCount = 1000;
        
        result.append("Performance Test (").append(testCount).append(" iterations):\n\n");
        
        // Native C++测试
        long nativeTime = performanceTest(testCount);
        result.append("Native C++: ").append(nativeTime).append(" ms\n");
        
        // Java测试
        long javaStart = System.currentTimeMillis();
        for (int i = 0; i < testCount; i++) {
            calculateSHA256Java("Performance test data " + i);
        }
        long javaTime = System.currentTimeMillis() - javaStart;
        result.append("Java: ").append(javaTime).append(" ms\n\n");
        
        // 计算加速比
        double speedup = (double) javaTime / nativeTime;
        result.append("Speedup: ").append(String.format("%.2f", speedup)).append("x\n");
        
        return result.toString();
    }
}
