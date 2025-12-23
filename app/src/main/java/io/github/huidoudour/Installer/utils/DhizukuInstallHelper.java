package io.github.huidoudour.Installer.utils;

import android.content.Context;
import android.util.Log;

import io.github.huidoudour.Installer.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Dhizuku APK 安装辅助类
 * 使用 Dhizuku API 执行命令和安装 APK
 * Dhizuku 是设备所有者模式，有权调用系统位执行 pm 命令
 */
public class DhizukuInstallHelper {
    private static final String TAG = "DhizukuInstallHelper";

    /**
     * 执行 Dhizuku 命令
     * 使用 Dhizuku.newProcess() 执行 shell 命令
     */
    public static String executeCommand(String command) throws Exception {
        try {
            Log.d(TAG, "executeCommand: 正在执行命令 = " + command);
            
            // 使用反射调用 Dhizuku API
            Class<?> dhizukuClass = Class.forName("com.rosan.dhizuku.api.Dhizuku");
            Log.d(TAG, "executeCommand: Dhizuku 类已加载");
            
            // 查找 newProcess 方法
            java.lang.reflect.Method newProcessMethod = null;
            java.lang.reflect.Method[] allMethods = dhizukuClass.getDeclaredMethods();
            Log.d(TAG, "executeCommand: 共有 " + allMethods.length + " 个方法");
            
            for (java.lang.reflect.Method method : allMethods) {
                if (method.getName().equals("newProcess")) {
                    Class<?>[] paramTypes = method.getParameterTypes();
                    Log.d(TAG, "executeCommand: 找到 newProcess, 参数个数 = " + paramTypes.length + ", 参数类型 = " + java.util.Arrays.toString(paramTypes));
                    
                    // Dhizuku newProcess 应该有 String[] 一个参数（命令数组）
                    // 或为 String[] cmd, String[] env, String dir 三个参数
                    if (paramTypes.length >= 1 && paramTypes[0].equals(String[].class)) {
                        newProcessMethod = method;
                        Log.d(TAG, "executeCommand: 选中 newProcess 方法");
                        break;
                    }
                }
            }
            
            if (newProcessMethod == null) {
                throw new Exception("无法找到符合条件的 Dhizuku newProcess 方法");
            }
            
            newProcessMethod.setAccessible(true);
            
            // 使用 sh -c 执行系统命令
            Object processObj = null;
            try {
                // 尝试 3 个参数的调用
                Log.d(TAG, "executeCommand: 尝试三参数调用 newProcess");
                processObj = newProcessMethod.invoke(
                    null,
                    new Object[]{
                        new String[]{"sh", "-c", command},
                        null,
                        null
                    }
                );
                Log.d(TAG, "executeCommand: 成功调用 newProcess (三参数), 返回值 = " + (processObj == null ? "null" : processObj.getClass().getName()));
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "executeCommand: 三参数失败，尝试单参数: " + e.getMessage());
                // 尝试单参数：仅命令数组
                try {
                    Log.d(TAG, "executeCommand: 尝试单参数调用 newProcess");
                    processObj = newProcessMethod.invoke(
                        null,
                        new Object[]{new String[]{"sh", "-c", command}}
                    );
                    Log.d(TAG, "executeCommand: 成功调用 newProcess (单参数), 返回值 = " + (processObj == null ? "null" : processObj.getClass().getName()));
                } catch (Exception e2) {
                    Log.e(TAG, "executeCommand: 单参数也失败", e2);
                    throw new Exception("无法找到正确的 newProcess 调用方式", e2);
                }
            } catch (java.lang.reflect.InvocationTargetException e) {
                Log.e(TAG, "executeCommand: InvocationTargetException", e);
                Throwable cause = e.getCause();
                Log.e(TAG, "executeCommand: 原因异常", cause);
                throw new Exception("Dhizuku 执行异常: " + (cause != null ? cause.getMessage() : e.getMessage()), e);
            }
            
            if (processObj == null) {
                throw new Exception("创建 Dhizuku 进程失败（返回 null）");
            }
            
            // 不是 Process 类，Dhizuku 返回的是 java.lang.Process
            Process process = null;
            try {
                process = (Process) processObj;
            } catch (ClassCastException e) {
                Log.e(TAG, "executeCommand: 类型转换失败，对象类型 = " + processObj.getClass().getName(), e);
                throw new Exception("无法转换为 Process 对象", e);
            }
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            reader.close();
            
            // 读取错误流
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errors = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                errors.append(line).append("\n");
            }
            errorReader.close();
            
            int exitCode = process.waitFor();
            Log.d(TAG, "executeCommand: 执行完成，退出码 = " + exitCode);
            
            String result = output.toString().trim();
            if (result.isEmpty() && errors.length() > 0) {
                result = errors.toString().trim();
            }
            
            if (!result.isEmpty()) {
                Log.d(TAG, "executeCommand: 结果 = " + result);
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "executeCommand: 程序异常", e);
            throw new Exception("执行 Dhizuku 命令失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 执行带输入的 Dhizuku 命令
     * 用于 pm install-write 等需要流输入的命令
     */
    public static String executeCommandWithInput(String command, File inputFile) throws Exception {
        try {
            Log.d(TAG, "executeCommandWithInput: 执行命令 = " + command + ", 文件 = " + inputFile.getName());
            
            Class<?> dhizukuClass = Class.forName("com.rosan.dhizuku.api.Dhizuku");
            java.lang.reflect.Method newProcessMethod = null;
            
            // 查找 newProcess 方法
            java.lang.reflect.Method[] allMethods = dhizukuClass.getDeclaredMethods();
            for (java.lang.reflect.Method method : allMethods) {
                if (method.getName().equals("newProcess")) {
                    Class<?>[] paramTypes = method.getParameterTypes();
                    if (paramTypes.length >= 1 && paramTypes[0].equals(String[].class)) {
                        newProcessMethod = method;
                        break;
                    }
                }
            }
            
            if (newProcessMethod == null) {
                throw new Exception("无法找到符合条件的 Dhizuku newProcess 方法");
            }
            
            newProcessMethod.setAccessible(true);
            
            Object processObj = null;
            try {
                processObj = newProcessMethod.invoke(
                    null,
                    new Object[]{new String[]{"sh", "-c", command}, null, null}
                );
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "executeCommandWithInput: 三参数失败，尝试单参数");
                try {
                    processObj = newProcessMethod.invoke(
                        null,
                        new Object[]{new String[]{"sh", "-c", command}}
                    );
                } catch (Exception e2) {
                    throw new Exception("无法调用 newProcess", e2);
                }
            }
            
            if (processObj == null) {
                throw new Exception("创建 Dhizuku 进程失败（返回 null）");
            }
            
            Process process = (Process) processObj;
            
            // 创建线程读取输出流，防止缓冲区满
            final StringBuilder output = new StringBuilder();
            final StringBuilder errors = new StringBuilder();
            
            Thread outputReader = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    reader.close();
                } catch (Exception e) {
                    Log.e(TAG, "executeCommandWithInput: 读取输出异常", e);
                }
            });
            
            Thread errorReader = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errors.append(line).append("\n");
                    }
                    reader.close();
                } catch (Exception e) {
                    Log.e(TAG, "executeCommandWithInput: 读取错误流异常", e);
                }
            });
            
            // 启动读取线程
            outputReader.start();
            errorReader.start();
            
            // 写入文件数据到进程的标准输入
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
            Log.d(TAG, "executeCommandWithInput: 文件数据写入完成");
            
            // 等待读取线程完成
            outputReader.join();
            errorReader.join();
            
            int exitCode = process.waitFor();
            Log.d(TAG, "executeCommandWithInput: 执行完成，退出码 = " + exitCode);
            
            String result = output.toString().trim();
            if (result.isEmpty() && errors.length() > 0) {
                result = errors.toString().trim();
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "executeCommandWithInput: 异常", e);
            throw new Exception("执行命令失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 执行 Dhizuku 命令（带上下文）
     */
    public static String executeCommand(Context context, String command) throws Exception {
        return executeCommand(command);
    }
    
    /**
     * 安全的 Dhizuku 权限检查
     */
    public static boolean isDhizukuAvailable(Context context) {
        try {
            return PrivilegeHelper.getStatus(context, PrivilegeHelper.PrivilegeMode.DHIZUKU) != PrivilegeHelper.PrivilegeStatus.NOT_INSTALLED;
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * 安装单个 APK
     * 使用 pm 命令但不添加 -i 参数（设备所有者模式）
     */
    public static void installSingleApk(Context context, File apkFile, boolean replaceExisting, boolean grantPermissions, ShizukuInstallHelper.InstallCallback callback) {
        // 检查权限
        if (!isDhizukuAvailable(context)) {
            if (callback != null) {
                callback.onError(context.getString(R.string.dhizuku_not_available));
            }
            return;
        }
        
        new Thread(() -> {
            try {
                callback.onProgress(context.getString(R.string.start_apk_install));
                
                // 创建安装会话（指定 --user 0 和 Dhizuku 包名）
                StringBuilder createCmd = new StringBuilder("pm install-create --user 0");
                if (replaceExisting) createCmd.append(" -r");
                if (grantPermissions) createCmd.append(" -g");
                
                // 使用 Dhizuku 的包名作为安装器（com.rosan.dhizuku）
                createCmd.append(" -i com.rosan.dhizuku");
                
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
                
                // 写入 APK - 使用文件路径而不是 stdin 重定向
                String writeCmd = "pm install-write -S " + apkFile.length() + " " + sessionId + " base.apk " + apkFile.getAbsolutePath();
                callback.onProgress(context.getString(R.string.write_apk_data));
                String writeOutput = executeCommand(context, writeCmd);
                
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
     * 使用 pm 命令但不添加 -i 参数（设备所有者模式）
     */
    public static void installXapk(Context context, String xapkPath, boolean replaceExisting, boolean grantPermissions, ShizukuInstallHelper.InstallCallback callback) {
        // 检查权限
        if (!isDhizukuAvailable(context)) {
            if (callback != null) {
                callback.onError(context.getString(R.string.dhizuku_not_available));
            }
            return;
        }
        
        new Thread(() -> {
            List<File> extractedApks = null;
            try {
                callback.onProgress(context.getString(R.string.extract_xapk));
                
                // 解压 XAPK
                extractedApks = XapkInstaller.extractXapk(context, xapkPath);
                callback.onProgress(context.getString(R.string.extract_complete, extractedApks.size()));
                
                // 创建安装会话（指定 --user 0 和 Dhizuku 包名）
                StringBuilder createCmd = new StringBuilder("pm install-create --user 0");
                if (replaceExisting) createCmd.append(" -r");
                if (grantPermissions) createCmd.append(" -g");
                
                // 使用 Dhizuku 的包名作为安装器（com.rosan.dhizuku）
                createCmd.append(" -i com.rosan.dhizuku");
                
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
                    
                    // 使用文件路径而不是 stdin 重定向
                    String writeCmd = "pm install-write -S " + apkFile.length() + " " + 
                                    sessionId + " " + apkFile.getName() + " " + apkFile.getAbsolutePath();
                    
                    String writeOutput = executeCommand(context, writeCmd);
                    
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
    public static void installApk(Context context, String apkPath, boolean replaceExisting, boolean grantPermissions, ShizukuInstallHelper.InstallCallback callback) {
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
