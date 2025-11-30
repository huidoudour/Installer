package io.github.huidoudour.Installer.utils;

import android.content.Context;
import android.util.Log;

import io.github.huidoudour.Installer.R;

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
        
        Log.d(TAG, context.getString(R.string.start_extract_xapk_log, xapkPath));
        Log.d(TAG, context.getString(R.string.extract_target_dir_log, extractDir.getAbsolutePath()));
        
        FileInputStream fis = null;
        ZipInputStream zis = null;
        
        try {
            Log.d(TAG, context.getString(R.string.opening_zip_file_log));
            fis = new FileInputStream(xapkPath);
            zis = new ZipInputStream(fis);
            Log.d(TAG, context.getString(R.string.zip_file_opened_log));
            
            int totalEntries = 0;
            int extractedApks = 0;
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                totalEntries++;
                String entryName = entry.getName();
                
                Log.d(TAG, context.getString(R.string.processing_entry, totalEntries, entryName, entry.getSize()));
                
                // 只解压 APK 文件，忽略其他文件（如 icon.png, manifest.json）
                if (entryName.toLowerCase().endsWith(".apk")) {
                    File destFile = new File(extractDir, new File(entryName).getName());
                    
                    // 确保父目录存在
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    
                    Log.d(TAG, context.getString(R.string.extracting_apk_log, destFile.getName()));
                    
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
                        
                        Log.d(TAG, context.getString(R.string.apk_extracted, destFile.getName(), totalRead));
                    } catch (Exception e) {
                        Log.e(TAG, context.getString(R.string.extract_apk_failed, destFile.getName()), e);
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
            
            Log.d(TAG, context.getString(R.string.extract_complete_summary, totalEntries, extractedApks));
            
        } catch (Exception e) {
            Log.e(TAG, context.getString(R.string.extract_xapk_error), e);
            throw e;
        } finally {
            // 关闭流
            if (zis != null) {
                try {
                    zis.close();
                    Log.d(TAG, context.getString(R.string.close_zip_stream));
                } catch (Exception e) {
                    Log.e(TAG, context.getString(R.string.close_zip_stream_failed), e);
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                    Log.d(TAG, context.getString(R.string.close_file_stream));
                } catch (Exception e) {
                    Log.e(TAG, context.getString(R.string.close_file_stream_failed), e);
                }
            }
        }
        
        if (apkFiles.isEmpty()) {
            throw new Exception(context.getString(R.string.no_apk_found));
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
        if (filePath == null) return "Unknown";
        
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".apk")) {
            return "APK (Standard Installation Package)";
        } else if (lowerPath.endsWith(".xapk")) {
            return "XAPK (APKPure Format)";
        } else if (lowerPath.endsWith(".apks")) {
            return "APKS (App Bundle)";
        } else if (lowerPath.endsWith(".apkm")) {
            return "APKM (APKMirror Format)";
        }
        return "Unknown Format";
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
    public static int getApkCount(Context context, String xapkPath) {
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
            Log.e(TAG, context.getString(R.string.read_xapk_failed), e);
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
    
    /**
     * XAPK 安装回调接口
     */
    public interface InstallCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * 安装 XAPK 文件
     * @param context 上下文
     * @param xapkPath XAPK 文件路径
     * @param callback 安装回调
     */
    public static void installXapk(Context context, String xapkPath, InstallCallback callback) {
        new Thread(() -> {
            List<File> extractedApks = null;
            try {
                // 解压 XAPK
                extractedApks = extractXapk(context, xapkPath);
                Log.d(TAG, context.getString(R.string.extract_complete, extractedApks.size()));
                
                // 安装所有 APK
                for (File apkFile : extractedApks) {
                    // 使用 ShizukuInstallHelper 安装单个 APK
                    ShizukuInstallHelper.installSingleApk(context, apkFile, true, true, new ShizukuInstallHelper.InstallCallback() {
                        @Override
                        public void onProgress(String message) {
                            Log.d(TAG, context.getString(R.string.install_progress, message));
                        }
                        
                        @Override
                        public void onSuccess(String message) {
                            Log.d(TAG, context.getString(R.string.apk_install_success_log, message));
                        }
                        
                        @Override
                        public void onError(String error) {
                            Log.e(TAG, context.getString(R.string.apk_install_failed_log, error));
                            callback.onError(context.getString(R.string.install_failed_error, error));
                            return;
                        }
                    });
                }
                
                // 所有 APK 安装完成
                callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, context.getString(R.string.xapk_install_exception_log), e);
                callback.onError(context.getString(R.string.install_exception_error, e.getMessage()));
            } finally {
                // 清理临时文件
                if (extractedApks != null) {
                    cleanupTempFiles(extractedApks);
                }
            }
        }).start();
    }
}