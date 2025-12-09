package io.github.huidoudour.Installer.utils;

import android.content.Context;

import io.github.huidoudour.Installer.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Shizuku APK 安装辅助类
 * 封装 Shizuku 命令执行和 APK 安装逻辑
 * 使用 pm install 命令，支持 -i 参数指定安装请求者
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
     * 执行 Shizuku 命令（带上下文）
     */
    public static String executeCommand(Context context, String command) throws Exception {
        return executeCommand(command);
    }

    /**
     * 安装单个 APK
     */
    public static void installSingleApk(Context context, File apkFile, boolean replaceExisting, boolean grantPermissions, InstallCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress(context.getString(R.string.start_apk_install));
                
                // 创建安装会话 - 添加安装请求者参数
                StringBuilder createCmd = new StringBuilder("pm install-create");
                if (replaceExisting) createCmd.append(" -r");
                if (grantPermissions) createCmd.append(" -g");
                
                // 添加安装请求者参数：io.github.huidoudour.zjs
                createCmd.append(" -i io.github.huidoudour.zjs");
                
                callback.onProgress(context.getString(R.string.create_session, createCmd.toString()));
                String createOutput = executeCommand(context, createCmd.toString());
                
                if (!createOutput.contains("Success")) {
                    throw new Exception(context.getString(R.string.install_failed_error, createOutput));
                }
                
                String sessionId = createOutput.substring(
                    createOutput.indexOf("[") + 1,
                    createOutput.indexOf("]")
                );
                callback.onProgress(context.getString(R.string.session_id, sessionId));
                
                // 写入 APK
                String writeCmd = "pm install-write -S " + apkFile.length() + " " + sessionId + " base.apk -";
                callback.onProgress(context.getString(R.string.write_apk_data));
                String writeOutput = executeCommandWithInput(writeCmd, apkFile);
                
                if (!writeOutput.contains("Success")) {
                    throw new Exception(context.getString(R.string.install_failed_error, writeOutput));
                }
                
                // 提交安装
                callback.onProgress(context.getString(R.string.submit_install));
                String commitOutput = executeCommand(context, "pm install-commit " + sessionId);
                
                if (commitOutput.toLowerCase().contains("success")) {
                    callback.onSuccess(context.getString(R.string.install_success_simple));
                } else {
                    callback.onError(context.getString(R.string.install_failed_error, commitOutput));
                }
                
            } catch (Exception e) {
                callback.onError(context.getString(R.string.install_exception, e.getMessage()));
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
                callback.onProgress(context.getString(R.string.extract_xapk));
                
                // 解压 XAPK
                extractedApks = XapkInstaller.extractXapk(context, xapkPath);
                callback.onProgress(context.getString(R.string.extract_complete, extractedApks.size()));
                
                // 创建安装会话
                StringBuilder createCmd = new StringBuilder("pm install-create");
                if (replaceExisting) createCmd.append(" -r");
                if (grantPermissions) createCmd.append(" -g");
                
                // 添加安装请求者参数：io.github.huidoudour.zjs
                createCmd.append(" -i io.github.huidoudour.zjs");
                
                callback.onProgress(context.getString(R.string.create_session, ""));
                String createOutput = executeCommand(context, createCmd.toString());
                
                if (!createOutput.contains("Success")) {
                    throw new Exception(context.getString(R.string.install_failed_error, createOutput));
                }
                
                String sessionId = createOutput.substring(
                    createOutput.indexOf("[") + 1,
                    createOutput.indexOf("]")
                );
                callback.onProgress(context.getString(R.string.session_id, sessionId));
                
                // 写入所有 APK
                int current = 0;
                for (File apkFile : extractedApks) {
                    current++;
                    callback.onProgress(context.getString(R.string.apk_progress, current, extractedApks.size(), apkFile.getName()));
                    
                    String writeCmd = "pm install-write -S " + apkFile.length() + " " + 
                                    sessionId + " " + apkFile.getName() + " -";
                    
                    String writeOutput = executeCommandWithInput(writeCmd, apkFile);
                    
                    if (!writeOutput.contains("Success")) {
                        throw new Exception(context.getString(R.string.install_failed_error, 
                            context.getString(R.string.apk_name_failed, apkFile.getName(), writeOutput)));
                    }
                }
                
                // 提交安装
                callback.onProgress(context.getString(R.string.submitting_install));
                String commitOutput = executeCommand(context, "pm install-commit " + sessionId);
                
                if (commitOutput.toLowerCase().contains("success")) {
                    callback.onSuccess(context.getString(R.string.xapk_install_success_msg, extractedApks.size()));
                } else {
                    callback.onError(context.getString(R.string.install_failed_error, commitOutput));
                }
                
            } catch (Exception e) {
                callback.onError(context.getString(R.string.xapk_install_exception, e.getMessage()));
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
     * @param replaceExisting 是否替换现有应用
     * @param grantPermissions 是否自动授予权限
     * @param callback 安装回调
     */
    public static void installApk(Context context, String apkPath, boolean replaceExisting, boolean grantPermissions, InstallCallback callback) {
        try {
            File apkFile = new File(apkPath);
            if (!apkFile.exists()) {
                callback.onError(context.getString(R.string.apk_not_exist));
                return;
            }
            
            installSingleApk(context, apkFile, replaceExisting, grantPermissions, callback);
        } catch (Exception e) {
            callback.onError(context.getString(R.string.install_exception, e.getMessage()));
        }
    }
}