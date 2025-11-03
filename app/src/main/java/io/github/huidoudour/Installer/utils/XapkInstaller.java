package io.github.huidoudour.Installer.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * XAPK/APKS 安装工具类
 * 
 * 使用 Android 内置的 ZIP 解压功能处理安装包
 * 
 * 支持格式：
 * - XAPK (APKPure 格式)
 * - APKS (App Bundle 导出格式)
 * - APKM (APKMirror 格式)
 */
public class XapkInstaller {

    private static final String TAG = "XapkInstaller";
    
    /**
     * 解压 XAPK/APKS 文件到缓存目录
     * 
     * @param context 上下文
     * @param xapkPath XAPK/APKS 文件路径
     * @return 解压出的 APK 文件列表
     */
    public static List<File> extractXapk(Context context, String xapkPath) throws Exception {
        List<File> apkFiles = new ArrayList<>();
        
        // 创建临时解压目录
        File extractDir = new File(context.getCacheDir(), "xapk_temp_" + System.currentTimeMillis());
        if (!extractDir.exists()) {
            extractDir.mkdirs();
        }
        
        Log.d(TAG, "开始解压 XAPK: " + xapkPath);
        Log.d(TAG, "解压目标目录: " + extractDir.getAbsolutePath());
        
        FileInputStream fis = null;
        ZipInputStream zis = null;
        
        try {
            Log.d(TAG, "正在打开 ZIP 文件...");
            fis = new FileInputStream(xapkPath);
            zis = new ZipInputStream(fis);
            Log.d(TAG, "ZIP 文件打开成功");
            
            int totalEntries = 0;
            int extractedApks = 0;
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                totalEntries++;
                String entryName = entry.getName();
                
                Log.d(TAG, "处理条目 #" + totalEntries + ": " + entryName + " (大小: " + entry.getSize() + " bytes)");
                
                // 只解压 APK 文件，忽略其他文件（如 icon.png, manifest.json）
                if (entryName.toLowerCase().endsWith(".apk")) {
                    File destFile = new File(extractDir, new File(entryName).getName());
                    
                    // 确保父目录存在
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    
                    Log.d(TAG, "正在解压 APK: " + destFile.getName());
                    
                    // 解压文件
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(destFile);
                        
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalRead = 0;
                        
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                        }
                        
                        fos.close();
                        
                        apkFiles.add(destFile);
                        extractedApks++;
                        
                        Log.d(TAG, "✓ 已解压 APK: " + destFile.getName() + 
                                " (" + totalRead + " bytes)");
                    } catch (Exception e) {
                        Log.e(TAG, "解压 APK 失败: " + destFile.getName(), e);
                        // 确保流被关闭
                        if (fos != null) {
                            try { fos.close(); } catch (Exception ignored) {}
                        }
                        throw e;
                    }
                }
                
                // 关闭当前条目
                zis.closeEntry();
            }
            
            Log.d(TAG, "解压完成！共处理 " + totalEntries + " 个条目，" +
                    "提取 " + extractedApks + " 个 APK 文件");
            
        } catch (Exception e) {
            Log.e(TAG, "解压 XAPK 过程中发生错误", e);
            throw e;
        } finally {
            // 关闭流
            if (zis != null) {
                try {
                    zis.close();
                    Log.d(TAG, "ZIP 输入流已关闭");
                } catch (Exception e) {
                    Log.e(TAG, "关闭 ZIP 输入流失败", e);
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                    Log.d(TAG, "文件输入流已关闭");
                } catch (Exception e) {
                    Log.e(TAG, "关闭文件输入流失败", e);
                }
            }
        }
        
        if (apkFiles.isEmpty()) {
            throw new Exception("XAPK/APKS 文件中未找到任何 APK 文件");
        }
        
        return apkFiles;
    }
    
    /**
     * 判断文件是否为 XAPK/APKS 格式
     */
    public static boolean isXapkFile(String filePath) {
        if (filePath == null) return false;
        String lowerPath = filePath.toLowerCase();
        return lowerPath.endsWith(".xapk") || 
               lowerPath.endsWith(".apks") || 
               lowerPath.endsWith(".apkm");
    }
    
    /**
     * 获取文件类型描述
     */
    public static String getFileTypeDescription(String filePath) {
        if (filePath == null) return "未知";
        
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".apk")) {
            return "APK (标准安装包)";
        } else if (lowerPath.endsWith(".xapk")) {
            return "XAPK (APKPure 格式)";
        } else if (lowerPath.endsWith(".apks")) {
            return "APKS (App Bundle)";
        } else if (lowerPath.endsWith(".apkm")) {
            return "APKM (APKMirror 格式)";
        }
        return "未知格式";
    }
    
    /**
     * 清理解压的临时文件
     */
    public static void cleanupTempFiles(List<File> files) {
        if (files == null || files.isEmpty()) return;
        
        for (File file : files) {
            if (file.exists()) {
                file.delete();
            }
        }
        
        // 删除父目录
        if (!files.isEmpty()) {
            File parentDir = files.get(0).getParentFile();
            if (parentDir != null && parentDir.exists()) {
                parentDir.delete();
            }
        }
    }
    
    /**
     * 获取 XAPK 中的 APK 数量（不解压，只读取）
     */
    public static int getApkCount(String xapkPath) {
        int count = 0;
        FileInputStream fis = null;
        ZipInputStream zis = null;
        
        try {
            fis = new FileInputStream(xapkPath);
            zis = new ZipInputStream(fis);
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().endsWith(".apk")) {
                    count++;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            Log.e(TAG, "读取 XAPK 失败", e);
        } finally {
            if (zis != null) {
                try { zis.close(); } catch (Exception ignored) {}
            }
            if (fis != null) {
                try { fis.close(); } catch (Exception ignored) {}
            }
        }
        return count;
    }
}