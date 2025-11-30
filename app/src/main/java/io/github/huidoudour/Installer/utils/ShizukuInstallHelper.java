package io.github.huidoudour.Installer.utils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * XAPK 安装辅助类
 * 封装 Shizuku 命令执行和 XAPK 多 APK 安装逻辑
 */
public class ShizukuInstallHelper {

    public interface InstallCallback {
        void onProgress(String message);
        void onSuccess(String message);
        void onError(String error);
    }

    /**
     * 执行 Shizuku 命令
     */
    public static String executeCommand(String command) throws Exception {
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            java.lang.reflect.Method newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess", 
                String[].class, 
                String[].class, 
                String.class
            );
            newProcessMethod.setAccessible(true);
            
            Process process = (Process) newProcessMethod.invoke(
                null,
                new String[]{"sh", "-c", command},
                null,
                null
            );
            
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            reader.close();
            
            process.waitFor();
            return output.toString().trim();
        } catch (Exception e) {
            throw new Exception("执行命令失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行 Shizuku 命令并传入文件数据
     */
    public static String executeCommandWithInput(String command, File inputFile) throws Exception {
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            java.lang.reflect.Method newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess", 
                String[].class, 
                String[].class, 
                String.class
            );
            newProcessMethod.setAccessible(true);
            
            Process process = (Process) newProcessMethod.invoke(
                null,
                new String[]{"sh", "-c", command},
                null,
                null
            );
            
            // 写入文件数据
            FileInputStream fis = new FileInputStream(inputFile);
            java.io.OutputStream os = process.getOutputStream();
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
            os.close();
            fis.close();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            reader.close();
            
            process.waitFor();
            return output.toString().trim();
        } catch (Exception e) {
            throw new Exception("执行命令失败: " + e.getMessage(), e);
        }
    }

    /**
     * 安装单个 APK
     */
    public static void installSingleApk(File apkFile, boolean replaceExisting, boolean grantPermissions, InstallCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress("开始安装 APK...");
                
                // 创建安装会话 - 添加安装请求者参数
                StringBuilder createCmd = new StringBuilder("pm install-create");
                if (replaceExisting) createCmd.append(" -r");
                if (grantPermissions) createCmd.append(" -g");
                
                // 添加安装请求者参数：io.github.huidoudour.zjs
                createCmd.append(" -i io.github.huidoudour.zjs");
                
                callback.onProgress("创建安装会话: " + createCmd);
                String createOutput = executeCommand(createCmd.toString());
                
                if (!createOutput.contains("Success")) {
                    throw new Exception("创建安装会话失败: " + createOutput);
                }
                
                String sessionId = createOutput.substring(
                    createOutput.indexOf("[") + 1,
                    createOutput.indexOf("]")
                );
                callback.onProgress("会话ID: " + sessionId);
                
                // 写入 APK
                String writeCmd = "pm install-write -S " + apkFile.length() + " " + sessionId + " base.apk -";
                callback.onProgress("写入 APK 数据...");
                String writeOutput = executeCommandWithInput(writeCmd, apkFile);
                
                if (!writeOutput.contains("Success")) {
                    throw new Exception("写入 APK 失败: " + writeOutput);
                }
                
                // 提交安装
                callback.onProgress("提交安装...");
                String commitOutput = executeCommand("pm install-commit " + sessionId);
                
                if (commitOutput.toLowerCase().contains("success")) {
                    callback.onSuccess("✅ 安装成功！");
                } else {
                    callback.onError("安装失败: " + commitOutput);
                }
                
            } catch (Exception e) {
                callback.onError("安装异常: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 安装 XAPK (多个 APK)
     */
    public static void installXapk(Context context, String xapkPath, boolean replaceExisting, boolean grantPermissions, InstallCallback callback) {
        new Thread(() -> {
            List<File> extractedApks = null;
            try {
                callback.onProgress("📦 正在解压 XAPK (使用原生压缩库)...");
                
                // 解压 XAPK
                extractedApks = XapkInstaller.extractXapk(context, xapkPath);
                callback.onProgress("✅ 解压完成，共 " + extractedApks.size() + " 个 APK");
                
                // 创建安装会话
                StringBuilder createCmd = new StringBuilder("pm install-create");
                if (replaceExisting) createCmd.append(" -r");
                if (grantPermissions) createCmd.append(" -g");
                
                // 添加安装请求者参数：io.github.huidoudour.zjs
                createCmd.append(" -i io.github.huidoudour.zjs");
                
                callback.onProgress("创建安装会话...");
                String createOutput = executeCommand(createCmd.toString());
                
                if (!createOutput.contains("Success")) {
                    throw new Exception("创建安装会话失败: " + createOutput);
                }
                
                String sessionId = createOutput.substring(
                    createOutput.indexOf("[") + 1,
                    createOutput.indexOf("]")
                );
                callback.onProgress("会话ID: " + sessionId);
                
                // 写入所有 APK
                int current = 0;
                for (File apkFile : extractedApks) {
                    current++;
                    callback.onProgress("📦 [" + current + "/" + extractedApks.size() + "] " + apkFile.getName());
                    
                    String writeCmd = "pm install-write -S " + apkFile.length() + " " + 
                                    sessionId + " " + apkFile.getName() + " -";
                    
                    String writeOutput = executeCommandWithInput(writeCmd, apkFile);
                    
                    if (!writeOutput.contains("Success")) {
                        throw new Exception("写入 " + apkFile.getName() + " 失败: " + writeOutput);
                    }
                }
                
                // 提交安装
                callback.onProgress("🚀 提交安装...");
                String commitOutput = executeCommand("pm install-commit " + sessionId);
                
                if (commitOutput.toLowerCase().contains("success")) {
                    callback.onSuccess("✨ XAPK 安装成功！共安装 " + extractedApks.size() + " 个 APK");
                } else {
                    callback.onError("安装失败: " + commitOutput);
                }
                
            } catch (Exception e) {
                callback.onError("XAPK 安装异常: " + e.getMessage());
            } finally {
                // 清理临时文件
                if (extractedApks != null) {
                    XapkInstaller.cleanupTempFiles(extractedApks);
                }
            }
        }).start();
    }
    
    /**
     * 安装单个 APK 文件
     * @param context 上下文
     * @param apkPath APK 文件路径
     * @param callback 安装回调
     */
    public static void installApk(Context context, String apkPath, InstallCallback callback) {
        new Thread(() -> {
            try {
                File apkFile = new File(apkPath);
                if (!apkFile.exists()) {
                    callback.onError("APK 文件不存在");
                    return;
                }
                
                // 使用 installSingleApk 方法安装
                installSingleApk(apkFile, true, true, callback);
            } catch (Exception e) {
                callback.onError("安装异常: " + e.getMessage());
            }
        }).start();
    }
}