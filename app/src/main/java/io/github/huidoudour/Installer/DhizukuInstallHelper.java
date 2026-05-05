package io.github.huidoudour.Installer;

import android.content.Context;

import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuRemoteProcess;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dhizuku APK 安装辅助类
 * 封装 Dhizuku 命令执行和 APK 安装逻辑
 * 使用 pm install 命令，支持 -i 参数指定安装请求者
 *
 * 关键说明（Dhizuku 与 Shizuku 的重要区别）：
 * - Dhizuku.newProcess() 返回 DhizukuRemoteProcess（远程进程），非本地 Process
 * - Dhizuku 以 DeviceOwner 应用 UID 运行，不是 shell 也不是 system
 * - pm install-write 使用 stdin 管道（"-"参数 / reverse mode）仅限 shell 或 system UID
 *   非 shell/system UID 会抛出：SecurityException: Reverse mode only supported from shell or system
 * - 因此 Dhizuku 必须使用文件路径方式写入 APK 数据，不能通过 stdin 管道
 * - pm install-create 的 -i 参数只能指定 Dhizuku 自身包名，其他包名会抛出
 *   SecurityException: Package xxx does not belong to <uid>
 *
 * 安装流程：
 * 1. pm install-create 创建会话
 * 2. 将 APK 文件 cp 到 /data/local/tmp/（DeviceOwner 有权限写此目录）
 * 3. pm install-write 使用文件路径写入数据（非 stdin 管道）
 * 4. pm install-commit 提交安装
 * 5. 清理 /data/local/tmp/ 中的临时文件
 */
public class DhizukuInstallHelper {

    private static final String TAG = "DhizukuInstallHelper";

    /** Dhizuku 安装时使用的临时目录 */
    private static final String TEMP_DIR = "/data/local/tmp";

    /** 临时文件前缀，用于清理时识别 */
    private static final String TEMP_PREFIX = "dhizuku_install_";

    public interface InstallCallback {
        void onProgress(String message);
        void onSuccess(String message);
        void onError(String error);
    }

    /**
     * 确保 Dhizuku 已初始化并拥有权限
     * @throws Exception 如果初始化失败或权限未授予
     */
    private static void ensureInitialized(Context context) throws Exception {
        boolean initResult = Dhizuku.init(context.getApplicationContext());
        android.util.Log.i(TAG, "Dhizuku.init() result: " + initResult);

        if (!initResult) {
            throw new Exception("Dhizuku initialization failed. Please check if Dhizuku is properly activated as DeviceOwner.");
        }

        boolean permissionGranted = Dhizuku.isPermissionGranted();
        android.util.Log.i(TAG, "Dhizuku.isPermissionGranted(): " + permissionGranted);

        if (!permissionGranted) {
            throw new Exception("Dhizuku permission not granted. Please grant permission in Dhizuku app first.");
        }
    }

    /**
     * 并行读取 Process 的 stdout 和 stderr，避免管道阻塞死锁
     * 这对 DhizukuRemoteProcess 尤为重要，因为它是远程进程
     */
    private static class ProcessOutput {
        final String stdout;
        final String stderr;
        final int exitCode;

        ProcessOutput(String stdout, String stderr, int exitCode) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
        }
    }

    /**
     * 并行读取进程输出
     */
    private static ProcessOutput readProcessOutput(Process process) throws Exception {
        final StringBuilder output = new StringBuilder();
        final StringBuilder errorOutput = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<Exception> readException = new AtomicReference<>(null);

        // 并行读取 stdout
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    android.util.Log.d(TAG, "STDOUT: " + line);
                }
            } catch (Exception e) {
                readException.compareAndSet(null, e);
                android.util.Log.e(TAG, "Error reading stdout: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        }, "Dhizuku-stdout").start();

        // 并行读取 stderr
        new Thread(() -> {
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                    android.util.Log.e(TAG, "STDERR: " + line);
                }
            } catch (Exception e) {
                readException.compareAndSet(null, e);
                android.util.Log.e(TAG, "Error reading stderr: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        }, "Dhizuku-stderr").start();

        // 等待进程结束
        int exitCode = process.waitFor();
        android.util.Log.i(TAG, "Command exit code: " + exitCode);

        // 等待读取线程完成（最多 10 秒）
        if (!latch.await(10, TimeUnit.SECONDS)) {
            android.util.Log.w(TAG, "Timeout waiting for output readers to finish");
        }

        if (readException.get() != null) {
            throw readException.get();
        }

        return new ProcessOutput(output.toString().trim(), errorOutput.toString().trim(), exitCode);
    }

    /**
     * 执行 Dhizuku 命令
     */
    public static String executeCommand(Context context, String command) throws Exception {
        try {
            android.util.Log.i(TAG, "Executing command: " + command);

            ensureInitialized(context);

            DhizukuRemoteProcess process = Dhizuku.newProcess(
                new String[]{"sh", "-c", command},
                null,
                null
            );

            ProcessOutput result = readProcessOutput(process);

            if (result.exitCode != 0 && !result.stderr.isEmpty()) {
                throw new Exception("命令执行失败 (exit code: " + result.exitCode + "): " + result.stderr);
            }

            return result.stdout;
        } catch (Exception e) {
            android.util.Log.e(TAG, "Command execution failed: " + e.getMessage());
            throw new Exception("执行命令失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 APK 文件复制到 /data/local/tmp/ 并返回远程路径
     * Dhizuku 作为 DeviceOwner 有权限写入 /data/local/tmp/
     *
     * @param context 上下文
     * @param localFile 本地 APK 文件
     * @return 远程临时文件路径
     * @throws Exception 如果复制失败
     */
    private static String copyApkToTemp(Context context, File localFile) throws Exception {
        // 确保临时目录存在且有写权限
        executeCommand(context, "mkdir -p " + TEMP_DIR);

        // 生成唯一的临时文件名
        String tempFileName = TEMP_PREFIX + System.currentTimeMillis() + "_" + localFile.getName();
        String tempPath = TEMP_DIR + "/" + tempFileName;

        // 使用 cat 命令将本地文件内容写入远程路径
        // 先用 base64 编码传输，避免特殊字符问题
        // 或者更简单：直接 cp，因为 DeviceOwner 可以访问 app 的私有目录
        String localPath = localFile.getAbsolutePath();

        // 尝试直接 cp（DeviceOwner 通常有权限读取 app 私有目录）
        String cpOutput = executeCommand(context, "cp '" + localPath + "' '" + tempPath + "' && chmod 644 '" + tempPath + "'");

        // 验证文件是否复制成功
        String lsOutput = executeCommand(context, "ls -la '" + tempPath + "'");
        if (lsOutput == null || lsOutput.isEmpty() || lsOutput.contains("No such file")) {
            throw new Exception("Failed to copy APK to temp directory: " + tempPath);
        }

        android.util.Log.i(TAG, "APK copied to temp: " + tempPath + " (" + lsOutput + ")");
        return tempPath;
    }

    /**
     * 清理远程临时文件
     */
    private static void cleanupTempFile(Context context, String tempPath) {
        try {
            executeCommand(context, "rm -f '" + tempPath + "'");
            android.util.Log.i(TAG, "Cleaned up temp file: " + tempPath);
        } catch (Exception e) {
            android.util.Log.w(TAG, "Failed to cleanup temp file: " + tempPath + ": " + e.getMessage());
        }
    }

    /**
     * 安装单个 APK
     */
    public static void installSingleApk(Context context, File apkFile, boolean replaceExisting, boolean grantPermissions, InstallCallback callback) {
        new Thread(() -> {
            String tempPath = null;
            try {
                callback.onProgress(context.getString(R.string.start_apk_install));

                // 创建安装会话
                StringBuilder createCmd = new StringBuilder("pm install-create --user 0");
                if (replaceExisting) createCmd.append(" -r");
                if (grantPermissions) createCmd.append(" -g");

                // Dhizuku 模式下 -i 只能使用 Dhizuku 自身包名
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

                // 将 APK 复制到 /data/local/tmp/（不能使用 stdin 管道，因为 Dhizuku 非 shell UID）
                callback.onProgress(context.getString(R.string.write_apk_data));
                tempPath = copyApkToTemp(context, apkFile);

                // 使用文件路径方式写入 APK 数据（不使用 "-" stdin 管道）
                String writeCmd = "pm install-write " + sessionId + " base.apk '" + tempPath + "'";
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
            } finally {
                // 清理临时文件
                if (tempPath != null) {
                    cleanupTempFile(context, tempPath);
                }
            }
        }).start();
    }

    /**
     * 安装 XAPK (多个 APK)
     */
    public static void installXapk(Context context, String xapkPath, boolean replaceExisting, boolean grantPermissions, InstallCallback callback) {
        new Thread(() -> {
            List<File> extractedApks = null;
            String[] tempPaths = null;
            try {
                callback.onProgress(context.getString(R.string.extract_xapk));

                // 解压 XAPK
                extractedApks = XapkInstaller.extractXapk(context, xapkPath);
                callback.onProgress(context.getString(R.string.extract_complete, extractedApks.size()));

                // 创建安装会话
                StringBuilder createCmd = new StringBuilder("pm install-create --user 0");
                if (replaceExisting) createCmd.append(" -r");
                if (grantPermissions) createCmd.append(" -g");

                // Dhizuku 模式下 -i 只能使用 Dhizuku 自身包名
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

                // 写入所有 APK（使用文件路径方式，不用 stdin 管道）
                tempPaths = new String[extractedApks.size()];
                int current = 0;
                for (File apkFile : extractedApks) {
                    current++;
                    callback.onProgress(context.getString(R.string.apk_progress, current, extractedApks.size(), apkFile.getName()));

                    // 复制到临时目录
                    tempPaths[current - 1] = copyApkToTemp(context, apkFile);

                    // 使用文件路径方式写入
                    String writeCmd = "pm install-write " + sessionId + " " + apkFile.getName() + " '" + tempPaths[current - 1] + "'";
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
                if (tempPaths != null) {
                    for (String tempPath : tempPaths) {
                        if (tempPath != null) {
                            cleanupTempFile(context, tempPath);
                        }
                    }
                }
                // 清理解压的 APK 文件
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
