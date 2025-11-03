package io.github.huidoudour.Installer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_SHIZUKU_PERMISSION = 123;
    private static final int REQUEST_CODE_MANAGE_FILES_PERMISSION = 456; // Android11+ 全盘访问

    private TextView tvShizukuStatus;
    private TextView tvSelectedFile;
    private Button btnSelectFile;
    private Button btnRequestPermission;
    private Button btnInstall;
    private TextView tvLog;
    private Button btnClearLog;
    private Button btnExportLog;
    private ScrollView scrollViewLog;

    private Uri selectedFileUri;
    private String selectedFilePath; // 指向 app cache 中复制的文件路径

    // Shizuku 权限请求监听器
    private final Shizuku.OnRequestPermissionResultListener onRequestPermissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        log("已授予 Shizuku 权限.");
                    } else {
                        log("Shizuku 权限被拒绝.");
                    }
                    updateShizukuStatusAndUi();
                }
            };

    // 文件选择 Launcher（保留原有风格）
    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedFileUri = result.getData().getData();
                    if (selectedFileUri != null) {
                        // 将用户选择的文件复制到 app cache 并返回本地路径
                        String fileName = getFileNameFromUri(selectedFileUri);
                        selectedFilePath = getFilePathFromUri(selectedFileUri); // 这个内部会将 content uri 复制到 cache
                        if (selectedFilePath != null) {
                            tvSelectedFile.setText("已选择: " + fileName);
                            log("已选择文件并复制到 cache: " + selectedFilePath);
                        } else {
                            tvSelectedFile.setText("已选择: " + (fileName != null ? fileName : selectedFileUri.getPath()));
                            log("已选择文件 (URI)，但复制到 cache 失败，URI: " + selectedFileUri.toString());
                        }
                        updateInstallButtonState();
                    }
                } else {
                    log("文件选择失败或被取消.");
                }
            });

    // MANAGE_EXTERNAL_STORAGE launcher（Android 11+）
    private final ActivityResultLauncher<Intent> manageFilesPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        log("MANAGE_EXTERNAL_STORAGE 权限已授予.");
                        openFilePicker(); // 权限获取后再次打开选择器
                    } else {
                        log("MANAGE_EXTERNAL_STORAGE 权限被拒绝.");
                        Toast.makeText(this, "需要文件访问权限以选择 APK。", Toast.LENGTH_LONG).show();
                    }
                }
            });

    // READ_EXTERNAL_STORAGE launcher (Android 10 及以下)
    private final ActivityResultLauncher<String> externalStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    log("READ_EXTERNAL_STORAGE 权限已授予.");
                    openFilePicker();
                } else {
                    log("READ_EXTERNAL_STORAGE 权限被拒绝.");
                    Toast.makeText(this, "需要读取存储权限以选择 APK。", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 你现有的布局

        // init views
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnRequestPermission = findViewById(R.id.btnRequestPermission);
        btnInstall = findViewById(R.id.btnInstall);
        tvLog = findViewById(R.id.tvLog);
        btnClearLog = findViewById(R.id.btnClearLog);
        btnExportLog = findViewById(R.id.btnExportLog);
        scrollViewLog = findViewById(R.id.scrollViewLog);

        tvLog.setMovementMethod(new ScrollingMovementMethod());
        tvLog.setText("");

        // 注册 Shizuku 权限结果监听（安全包裹）
        try {
            Shizuku.addRequestPermissionResultListener(onRequestPermissionResultListener);
        } catch (Throwable e) {
            log("Shizuku 不可用喵: " + e.getMessage());
        }

        // 添加 binder received listener，binder 一到就更新 UI（避免 getVersion 导致崩溃）
        try {
            Shizuku.addBinderReceivedListener(() -> runOnUiThread(this::updateShizukuStatusAndUi));
        } catch (Throwable t) {
            // 如果 SDK 里没有这个方法或不可用，忽略
            log("不能添加 Shizuku binder listener 喵: " + t.getMessage());
        }

        btnSelectFile.setOnClickListener(v -> checkFilePermissionsAndOpenFilePicker());
        btnRequestPermission.setOnClickListener(v -> requestShizukuPermission());
        btnInstall.setOnClickListener(v -> installSelectedApk());
        btnClearLog.setOnClickListener(v -> clearLogs());
        btnExportLog.setOnClickListener(v -> exportLogs());

        // 初始 UI 状态（安全地检查 Shizuku）
        updateShizukuStatusAndUi();
        log("App已启动，等待操作喵……");
    }

    // --- 文件 URI -> 复制到 cache，返回本地路径 ---
    private String getFilePathFromUri(Uri uri) {
        if (uri == null) return null;

        // 如果是 file://
        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }

        // 读取 DISPLAY_NAME 作为文件名并复制到 getCacheDir()
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = null;
        try {
            String[] projection = {OpenableColumns.DISPLAY_NAME};
            cursor = contentResolver.query(uri, projection, null, null, null);
            String fileName = null;
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
            }
            if (fileName == null) fileName = "selected.apk";

            File cacheFile = copyUriToCache(uri, fileName);
            if (cacheFile != null) {
                return cacheFile.getAbsolutePath();
            }
        } catch (Exception e) {
            log("getFilePathFromUri 有问题喵: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    // 将 Content Uri 的内容复制到 app cache 目录
    private File copyUriToCache(Uri uri, String fileName) {
        try {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) return null;
            FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
            File outputFile = new File(getCacheDir(), fileName);
            FileOutputStream out = new FileOutputStream(outputFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            in.close();
            out.close();
            pfd.close();
            return outputFile;
        } catch (Exception e) {
            log("复制到 cache 失败喵: " + e.getMessage());
            return null;
        }
    }

    // 获取显示文件名
    private String getFileNameFromUri(Uri uri) {
        if (uri == null) return null;
        String result = null;
        Cursor cursor = null;
        try {
            String[] projection = {OpenableColumns.DISPLAY_NAME};
            cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) result = cursor.getString(nameIndex);
            }
        } catch (Exception e) {
            // ignore
        } finally {
            if (cursor != null) cursor.close();
        }
        if (result == null) result = uri.getLastPathSegment();
        return result;
    }

    // 检查文件读权限并打开文件选择器
    private void checkFilePermissionsAndOpenFilePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            if (!Environment.isExternalStorageManager()) {
                log("请求 MANAGE_EXTERNAL_STORAGE 权限喵.");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    manageFilesPermissionLauncher.launch(intent);
                } catch (Exception e) {
                    log("请求 MANAGE_EXTERNAL_STORAGE 权限出错喵: " + e.getMessage());
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    manageFilesPermissionLauncher.launch(intent);
                }
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                log("请求 READ_EXTERNAL_STORAGE 权限喵.");
                externalStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                return;
            }
        }
        openFilePicker();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        // 进一步优化文件选择器配置以支持华为/鸿蒙设备上的XAPK/APKS文件
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "application/vnd.android.package-archive",  // APK
            "application/zip",  // XAPK, APKS (ZIP格式)
            "application/octet-stream"  // 二进制流（用于兼容华为等设备将APKS识别为bin文件的情况）
        });
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // 添加额外的标志以提高兼容性
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, "选择安装包文件 (APK/XAPK/APKS)"));
            log("打开文件选择器喵~...");
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "请安装文件管理器以选择安装包文件喵。", Toast.LENGTH_SHORT).show();
            log("文件选择器未找到喵.");
        }
    }

    private void requestShizukuPermission() {
        log("尝试请求 Shizuku 权限喵~...");
        try {
            if (!Shizuku.pingBinder()) {
                log("Shizuku没启动喵~。");
                Toast.makeText(this, "Shizuku 未运行或未安装喵", Toast.LENGTH_LONG).show();
                updateShizukuStatusAndUi();
                return;
            }
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                log("Shizuku 版本过低或不兼容喵。");
                Toast.makeText(this, "请升级Shizuku喵", Toast.LENGTH_LONG).show();
                updateShizukuStatusAndUi();
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION);
            } else {
                log("Shizuku 已授权喵.");
                updateShizukuStatusAndUi();
            }
        } catch (Throwable t) {
            log("Shizuku 不可用喵: " + t.getMessage());
            updateShizukuStatusAndUi();
        }
    }

    // 核心：使用正确的 Shizuku API 执行命令
    private void installSelectedApk() {
        if (selectedFilePath == null || selectedFilePath.isEmpty()) {
            log("未选择 APK 或路径无效喵.");
            Toast.makeText(this, "请先选择 APK 文件喵.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查 Shizuku 连接与授权
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                log("Shizuku 未连接或未授权，无法通过 Shizuku 安装喵.");
                Toast.makeText(this, "Shizuku 未连接或未授权，无法安装喵。", Toast.LENGTH_LONG).show();
                updateShizukuStatusAndUi();
                return;
            }
        } catch (Throwable t) {
            log("检查 Shizuku 状态失败喵: " + t.getMessage());
            Toast.makeText(this, "Shizuku 不可用喵", Toast.LENGTH_LONG).show();
            updateShizukuStatusAndUi();
            return;
        }

        btnInstall.setEnabled(false);
        log("=== 开始安装流程 ===");
        log("APK路径: " + selectedFilePath);

        new Thread(() -> {
            File tempFile = new File(selectedFilePath);
            try {
                log("APK文件大小: " + tempFile.length() + " bytes");
                
                // 使用 pm install-create/install-write/install-commit 流程
                
                // 步骤1: 创建安装会话
                String createCmd = "pm install-create -r";
                log("创建安装会话: " + createCmd);
                String createOutput = executeShizukuCommand(createCmd);
                log("会话创建输出: " + createOutput);
                
                // 解析会诚ID (格式: "Success: created install session [123]")
                if (createOutput == null || !createOutput.contains("Success")) {
                    throw new Exception("创建安装会话失败: " + createOutput);
                }
                
                String sessionId = createOutput.substring(
                    createOutput.indexOf("[") + 1,
                    createOutput.indexOf("]")
                );
                log("会诚ID: " + sessionId);
                
                // 步骤2: 写入APK数据到会话
                String writeCmd = "pm install-write -S " + tempFile.length() + " " + sessionId + " base.apk -";
                log("写入APK数据: " + writeCmd);
                
                String writeOutput = executeShizukuCommandWithInput(writeCmd, tempFile);
                log("写入结果: " + writeOutput);
                
                if (writeOutput == null || !writeOutput.contains("Success")) {
                    throw new Exception("写入APK失败: " + writeOutput);
                }
                
                // 步骤3: 提交安装
                String commitCmd = "pm install-commit " + sessionId;
                log("提交安装: " + commitCmd);
                String commitOutput = executeShizukuCommand(commitCmd);
                
                final String finalOut = commitOutput != null ? commitOutput.trim() : "";

                runOnUiThread(() -> {
                    if (finalOut.toLowerCase().contains("success")) {
                        log("✓ 安装成功: " + tempFile.getName());
                        log("输出: " + finalOut);
                        Toast.makeText(MainActivity.this, "安装成功喵~", Toast.LENGTH_LONG).show();
                        tvSelectedFile.setText("No file selected");
                        selectedFileUri = null;
                        selectedFilePath = null;
                        
                        // 安装成功后清理临时文件
                        if (tempFile.exists()) {
                            if (tempFile.delete()) {
                                log("临时文件已清理");
                            }
                        }
                    } else {
                        log("✗ 安装失败");
                        log("输出: " + finalOut);
                        Toast.makeText(MainActivity.this, "安装失败，请查看日志喵。", Toast.LENGTH_LONG).show();
                    }
                    log("=== 安装流程结束 ===");
                    btnInstall.setEnabled(true);
                    updateInstallButtonState();
                });

            } catch (Exception e) {
                final String em = e.getMessage();
                runOnUiThread(() -> {
                    log("✗ 安装流程异常: " + em);
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "安装异常: " + em, Toast.LENGTH_LONG).show();
                    log("=== 安装流程异常结束 ===");
                    btnInstall.setEnabled(true);
                    updateInstallButtonState();
                });
            }
        }).start();
    }

    // 使用 Shizuku 执行 shell 命令并传入文件数据
    private String executeShizukuCommandWithInput(String command, File inputFile) throws Exception {
        Process process = null;
        FileInputStream fis = null;
        try {
            // 使用反射调用 Shizuku.newProcess
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            java.lang.reflect.Method newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess", 
                String[].class, 
                String[].class, 
                String.class
            );
            newProcessMethod.setAccessible(true);
            
            process = (Process) newProcessMethod.invoke(
                null,
                new String[]{"sh", "-c", command},
                null,
                null
            );
            
            if (process == null) {
                throw new Exception("无法创建进程");
            }
            
            // 将文件数据写入进程的标准输入
            fis = new FileInputStream(inputFile);
            java.io.OutputStream os = process.getOutputStream();
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            os.flush();
            os.close();
            fis.close();
            fis = null;
            
            log("已写入 " + totalBytes + " bytes");
            
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
            log("执行命令异常: " + e.getMessage());
            throw new Exception("执行命令失败: " + e.getMessage(), e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception ignored) {}
            }
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {}
            }
        }
    }

    // 使用反射调用 Shizuku 的隐藏 API 执行 shell 命令
    private String executeShizukuCommand(String command) throws Exception {
        Process process = null;
        try {
            // 使用反射调用 Shizuku.newProcess
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            java.lang.reflect.Method newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess", 
                String[].class, 
                String[].class, 
                String.class
            );
            newProcessMethod.setAccessible(true);
            
            process = (Process) newProcessMethod.invoke(
                null,
                new String[]{"sh", "-c", command},
                null,
                null
            );
            
            if (process == null) {
                throw new Exception("无法创建进程，Shizuku可能未正确授权");
            }
            
            // 同时读取标准输出和错误输出
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            
            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                output.append(line).append("\n");
                log("命令输出: " + line);
            }
            while ((line = stderrReader.readLine()) != null) {
                error.append(line).append("\n");
                log("命令错误: " + line);
            }
            
            stdoutReader.close();
            stderrReader.close();
            
            int exitCode = process.waitFor();
            log("进程退出码: " + exitCode);
            
            // 如果有输出就返回输出，否则返回错误信息
            String result = output.toString().trim();
            if (result.isEmpty() && error.length() > 0) {
                result = error.toString().trim();
            }
            
            if (result.isEmpty()) {
                throw new Exception("命令执行无输出，退出码: " + exitCode);
            }
            
            return result;
        } catch (Exception e) {
            log("执行命令异常详情: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                log("异常原因: " + e.getCause().getMessage());
            }
            throw new Exception("执行命令失败: " + e.getMessage(), e);
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {}
            }
        }
    }

    private void updateShizukuStatusAndUi() {
        try {
            if (!Shizuku.pingBinder()) {
                tvShizukuStatus.setText("Shizuku Server: 未运行/未安装");
                tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                btnRequestPermission.setEnabled(false);
                log("Shizuku 未连接喵.");
            } else {
                // 若 binder 已连上，再安全调用 getVersion 等
                try {
                    if (Shizuku.isPreV11() || Shizuku.getVersion() < 10) {
                        tvShizukuStatus.setText("Shizuku Server: 版本过低喵");
                        tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                        btnRequestPermission.setEnabled(false);
                        log("Shizuku 版本过低喵.");
                    } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        tvShizukuStatus.setText("Shizuku Permission: 已授予喵");
                        tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                        btnRequestPermission.setEnabled(false);
                        log("Shizuku 已连接并授权喵.");
                    } else {
                        tvShizukuStatus.setText("Shizuku Permission: 未授予喵");
                        tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
                        btnRequestPermission.setEnabled(true);
                        log("Shizuku 已连接但未授权喵.");
                    }
                } catch (Throwable t) {
                    tvShizukuStatus.setText("Shizuku 状态未知喵");
                    tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    log("检查 Shizuku 版本/权限失败喵: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            tvShizukuStatus.setText("Shizuku 不可用喵");
            tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            log("updateShizukuStatusAndUi 捕获异常喵: " + t.getMessage());
        }
        updateInstallButtonState();
    }

    private void updateInstallButtonState() {
        boolean fileSelected = selectedFilePath != null && !selectedFilePath.isEmpty();
        boolean shizukuReady = false;
        try {
            shizukuReady = Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED &&
                    !(Shizuku.isPreV11() || Shizuku.getVersion() < 10);
        } catch (Throwable t) {
            shizukuReady = false;
        }

        if (shizukuReady && fileSelected) {
            btnInstall.setEnabled(true);
            // 保持你原来的样式调用，如需可设置颜色
        } else {
            btnInstall.setEnabled(false);
        }
    }

    @SuppressLint("SetTextI18n")
    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logMessage = timestamp + ": " + message;
        System.out.println("ShizukuInstallerApp: " + logMessage);
        runOnUiThread(() -> {
            try {
                tvLog.append(logMessage + "\n");
                // 使用 ScrollView 滚动到底部
                scrollViewLog.post(() -> scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN));
            } catch (Exception ignored) {
            }
        });
    }

    // 清空日志
    private void clearLogs() {
        runOnUiThread(() -> {
            tvLog.setText("");
            Toast.makeText(this, "日志已清空喵", Toast.LENGTH_SHORT).show();
        });
    }

    // 导出日志
    private void exportLogs() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "installer_log_" + timestamp + ".txt";
            File logFile = new File(getExternalCacheDir(), fileName);

            FileWriter writer = new FileWriter(logFile);
            writer.write("=== Installer Log Export ===\n");
            writer.write("Export Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n");
            writer.write("\n=== Log Content ===\n");
            writer.write(tvLog.getText().toString());
            writer.close();

            // 分享文件
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Installer Log");
            
            // 使用 content:// URI 而不是 file:// URI
            Uri fileUri = Uri.parse("content://" + getPackageName() + ".fileprovider/external_cache/" + fileName);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "导出日志"));
            
            Toast.makeText(this, "日志已导出: " + fileName, Toast.LENGTH_LONG).show();
            log("日志已导出到: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            log("导出日志失败: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateShizukuStatusAndUi();
    }

    @Override
    protected void onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(onRequestPermissionResultListener);
        } catch (Throwable ignored) {}
        super.onDestroy();
    }

    // 兼容旧 onRequestPermissionsResult（保留）
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}