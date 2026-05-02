package io.github.huidoudour.Installer;

import android.content.Context;

import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Dhizuku APK 安装辅助类
 * 封装 Dhizuku 命令执行和 APK 安装逻辑
 * 使用 pm install 命令，支持 -i 参数指定安装请求者
 */
public class DhizukuInstallHelper {

    public interface InstallCallback {
        void onProgress(String message);
        void onSuccess(String message);
        void onError(String error);
    }

    /**
     * 执行 Dhizuku 命令
     */
    public static String executeCommand(Context context, String command) throws Exception {
        try {
            android.util.Log.i("DhizukuInstallHelper", "Executing command: " + command);
            
            // 初始化 Dhizuku（每次执行前都需要）
            boolean initResult = com.rosan.dhizuku.api.Dhizuku.init(context.getApplicationContext());
            android.util.Log.i("DhizukuInstallHelper", "Dhizuku.init() result: " + initResult);
            
            if (!initResult) {
                throw new Exception("Dhizuku initialization failed. Please check if Dhizuku is properly activated as DeviceOwner.");
            }
            
            // 检查 Dhizuku 权限状态
            boolean permissionGranted = com.rosan.dhizuku.api.Dhizuku.isPermissionGranted();
            android.util.Log.i("DhizukuInstallHelper", "Dhizuku.isPermissionGranted(): " + permissionGranted);
            
            if (!permissionGranted) {
                throw new Exception("Dhizuku permission not granted. Please grant permission in Dhizuku app first.");
            }
            
            // 使用 Dhizuku API 执行命令
            Process process = com.rosan.dhizuku.api.Dhizuku.newProcess(
                new String[]{"sh", "-c", command},
                null,
                null
            );
            
            if (process == null) {
                throw new Exception("Failed to create Dhizuku process. Dhizuku may not be properly activated.");
            }
            
            android.util.Log.i("DhizukuInstallHelper", "Process created successfully, PID: " + process.hashCode());
            
            // 同时读取标准输出和错误输出
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                android.util.Log.d("DhizukuInstallHelper", "STDOUT: " + line);
            }
            reader.close();
            
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
                android.util.Log.e("DhizukuInstallHelper", "STDERR: " + line);
            }
            errorReader.close();
            
            int exitCode = process.waitFor();
            android.util.Log.i("DhizukuInstallHelper", "Command exit code: " + exitCode);
            
            // 如果有错误输出，优先返回错误信息
            if (errorOutput.length() > 0) {
                throw new Exception("命令执行失败 (exit code: " + exitCode + "): " + errorOutput.toString().trim());
            }
            
            return output.toString().trim();
        } catch (Exception e) {
            android.util.Log.e("DhizukuInstallHelper", "Command execution failed: " + e.getMessage());
            throw new Exception("执行命令失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行 Dhizuku 命令并传入文件数据
     */
    public static String executeCommandWithInput(Context context, String command, File inputFile) throws Exception {
        try {
            android.util.Log.i("DhizukuInstallHelper", "Executing command with input: " + command);
            
            // 初始化 Dhizuku（每次执行前都需要）
            if (!com.rosan.dhizuku.api.Dhizuku.init(context.getApplicationContext())) {
                throw new Exception("Dhizuku initialization failed");
            }
            
            // 检查 Dhizuku 权限状态
            if (!com.rosan.dhizuku.api.Dhizuku.isPermissionGranted()) {
                throw new Exception("Dhizuku permission not granted. Please grant permission in Dhizuku app first.");
            }
            
            // 使用 Dhizuku API 执行命令
            Process process = com.rosan.dhizuku.api.Dhizuku.newProcess(
                new String[]{"sh", "-c", command},
                null,
                null
            );
            
            if (process == null) {
                throw new Exception("Failed to create Dhizuku process. Dhizuku may not be properly activated.");
            }
            
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
            
            // 读取输出和错误
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                android.util.Log.d("DhizukuInstallHelper", "STDOUT: " + line);
            }
            reader.close();
            
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
                android.util.Log.e("DhizukuInstallHelper", "STDERR: " + line);
            }
            errorReader.close();
            
            int exitCode = process.waitFor();
            android.util.Log.i("DhizukuInstallHelper", "Command exit code: " + exitCode);
            
            // 如果有错误输出，优先返回错误信息
            if (errorOutput.length() > 0) {
                throw new Exception("命令执行失败 (exit code: " + exitCode + "): " + errorOutput.toString().trim());
            }
            
            return output.toString().trim();
        } catch (Exception e) {
            android.util.Log.e("DhizukuInstallHelper", "Command execution failed: " + e.getMessage());
            throw new Exception("执行命令失败: " + e.getMessage(), e);
        }
    }

    /**
     * 安装单个 APK
     */
    public static void installSingleApk(Context context, File apkFile, boolean replaceExisting, boolean grantPermissions, InstallCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress(context.getString(R.string.start_apk_install));
                
                // 创建安装会话 - Dhizuku 不使用 -i 参数，因为它本身就是 DeviceOwner
                StringBuilder createCmd = new StringBuilder("pm install-create --user 0");
                if (replaceExisting) createCmd.append(" -r");
                if (grantPermissions) createCmd.append(" -g");
                // 注意：Dhizuku 模式下不添加 -i 参数，避免权限冲突

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
                String writeOutput = executeCommandWithInput(context, writeCmd, apkFile);
                
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
                
                // 创建安装会话 - Dhizuku 不使用 -i 参数
                StringBuilder createCmd = new StringBuilder("pm install-create --user 0");
                if (replaceExisting) createCmd.append(" -r");
                if (grantPermissions) createCmd.append(" -g");
                // 注意：Dhizuku 模式下不添加 -i 参数，避免权限冲突
                
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
                    
                    String writeOutput = executeCommandWithInput(context, writeCmd, apkFile);
                    
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
    
    /**
     * 获取用户设置的安装请求者包名
     * @param context 上下文
     * @return 安装请求者包名，如果未启用自定义包名则返回 com.android.shell
     */
    private static String getInstallerPackage(Context context) {
        android.content.SharedPreferences sharedPreferences = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        
        // 检查是否启用了自定义包名
        boolean enableCustomPackageName = sharedPreferences.getBoolean("enable_custom_package_name", true);
        
        if (!enableCustomPackageName) {
            // 关闭时使用默认的 com.android.shell
            return "com.android.shell";
        }
        
        // 开启时使用用户选择的包名
        String installerPackage = sharedPreferences.getString("installer_package", "");
        
        // 如果用户没有设置，默认使用 com.android.shell
        if (installerPackage.isEmpty()) {
            return "com.android.shell";
        }
        
        return installerPackage;
    }
}
