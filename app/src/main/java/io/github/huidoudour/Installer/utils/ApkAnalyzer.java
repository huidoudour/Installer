package io.github.huidoudour.Installer.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * APK 分析工具类
 * 用于提取 APK 的签名、哈希值等信息
 * 
 * 使用到的原生库：
 * - java.security (包含原生加密库)
 * - conscrypt (如果添加，性能更好)
 */
public class ApkAnalyzer {

    /**
     * 计算文件的 SHA-256 哈希值
     * 
     * @param filePath APK 文件路径
     * @return SHA-256 哈希字符串
     */
    public static String calculateSHA256(String filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            FileInputStream fis = new FileInputStream(filePath);
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            fis.close();
            
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 计算文件的 MD5 哈希值
     */
    public static String calculateMD5(String filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(filePath);
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            fis.close();
            
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取 APK 的包名
     */
    public static String getPackageName(Context context, String apkPath) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkPath, 0);
            if (info != null) {
                return info.packageName;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取 APK 的版本信息
     */
    public static String getVersionInfo(Context context, String apkPath) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkPath, 0);
            if (info != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    return info.versionName + " (" + info.getLongVersionCode() + ")";
                } else {
                    return info.versionName + " (" + info.versionCode + ")";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取 APK 的签名信息
     */
    @SuppressWarnings("deprecation")
    public static List<String> getSignatureInfo(Context context, String apkPath) {
        List<String> sigInfo = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES);
            
            if (info != null && info.signatures != null) {
                for (Signature signature : info.signatures) {
                    byte[] signatureBytes = signature.toByteArray();
                    
                    // 计算签名的 MD5
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    md.update(signatureBytes);
                    String md5 = bytesToHex(md.digest());
                    
                    // 计算签名的 SHA1
                    MessageDigest sha1 = MessageDigest.getInstance("SHA1");
                    sha1.update(signatureBytes);
                    String sha1Hash = bytesToHex(sha1.digest());
                    
                    // 计算签名的 SHA256
                    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                    sha256.update(signatureBytes);
                    String sha256Hash = bytesToHex(sha256.digest());
                    
                    // 获取证书信息
                    try {
                        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                        X509Certificate cert = (X509Certificate) certFactory.generateCertificate(
                            new java.io.ByteArrayInputStream(signatureBytes)
                        );
                        
                        sigInfo.add("证书主体: " + cert.getSubjectDN().toString());
                        sigInfo.add("证书颁发者: " + cert.getIssuerDN().toString());
                        sigInfo.add("有效期: " + cert.getNotBefore() + " 至 " + cert.getNotAfter());
                    } catch (Exception e) {
                        // 证书解析失败，只显示哈希
                    }
                    
                    sigInfo.add("签名 MD5: " + md5);
                    sigInfo.add("签名 SHA1: " + sha1Hash);
                    sigInfo.add("签名 SHA256: " + sha256Hash);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            sigInfo.add("签名信息获取失败: " + e.getMessage());
        }
        return sigInfo;
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toUpperCase();
    }

    /**
     * 获取文件大小（格式化）
     */
    public static String getFileSize(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return "Unknown";
        
        long size = file.length();
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 检查 APK 是否有效
     */
    public static boolean isValidApk(Context context, String apkPath) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkPath, 0);
            return info != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取 APK 的应用图标
     */
    public static android.graphics.drawable.Drawable getAppIcon(Context context, String apkPath) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkPath, 0);
            if (info != null) {
                // 获取应用图标
                info.applicationInfo.sourceDir = apkPath;
                info.applicationInfo.publicSourceDir = apkPath;
                return info.applicationInfo.loadIcon(pm);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}