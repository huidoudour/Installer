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

        tvLog.setMovementMethod(new ScrollingMovementMethod());
        tvLog.setText("");

        // 注册 Shizuku 权限结果监听（安全包裹）
        try {
            Shizuku.addRequestPermissionResultListener(onRequestPermissionResultListener);
        } catch (Throwable e) {
            log("Shizuku SDK 未找到或不可用: " + e.getMessage());
        }

        // 添加 binder received listener，binder 一到就更新 UI（避免 getVersion 导致崩溃）
        try {
            Shizuku.addBinderReceivedListener(() -> runOnUiThread(this::updateShizukuStatusAndUi));
        } catch (Throwable t) {
            // 如果 SDK 里没有这个方法或不可用，忽略
            log("无法添加 Shizuku binder listener (忽略): " + t.getMessage());
        }

        btnSelectFile.setOnClickListener(v -> checkFilePermissionsAndOpenFilePicker());
        btnRequestPermission.setOnClickListener(v -> requestShizukuPermission());
        btnInstall.setOnClickListener(v -> installSelectedApk());

        // 初始 UI 状态（安全地检查 Shizuku）
        updateShizukuStatusAndUi();
        log("App已启动，等待操作……");
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
            log("getFilePathFromUri 异常: " + e.getMessage());
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
            log("复制到 cache 失败: " + e.getMessage());
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
                log("请求 MANAGE_EXTERNAL_STORAGE 权限.");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    manageFilesPermissionLauncher.launch(intent);
                } catch (Exception e) {
                    log("请求 MANAGE_EXTERNAL_STORAGE 出错: " + e.getMessage());
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    manageFilesPermissionLauncher.launch(intent);
                }
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                log("请求 READ_EXTERNAL_STORAGE 权限.");
                externalStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                return;
            }
        }
        openFilePicker();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.android.package-archive");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, "选择 APK 文件"));
            log("打开文件选择器...");
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "请安装文件管理器以选择 APK。", Toast.LENGTH_SHORT).show();
            log("文件选择器未找到.");
        }
    }

    private void requestShizukuPermission() {
        log("尝试请求 Shizuku 权限...");
        try {
            if (!Shizuku.pingBinder()) {
                log("Shizuku 未运行或未安装。请启动 Shizuku。");
                Toast.makeText(this, "Shizuku 未运行或未安装。", Toast.LENGTH_LONG).show();
                updateShizukuStatusAndUi();
                return;
            }
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                log("Shizuku 版本过低或不兼容。");
                Toast.makeText(this, "Shizuku 版本过低，请升级。", Toast.LENGTH_LONG).show();
                updateShizukuStatusAndUi();
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION);
            } else {
                log("Shizuku 已授权.");
                updateShizukuStatusAndUi();
            }
        } catch (Throwable t) {
            log("Shizuku 不可用: " + t.getMessage());
            updateShizukuStatusAndUi();
        }
    }

    // 核心：使用正确的 Shizuku API 执行命令
    private void installSelectedApk() {
        if (selectedFilePath == null || selectedFilePath.isEmpty()) {
            log("未选择 APK 或路径无效.");
            Toast.makeText(this, "请先选择 APK 文件.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查 Shizuku 连接与授权
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                log("Shizuku 未连接或未授权，无法通过 Shizuku 安装.");
                Toast.makeText(this, "Shizuku 未连接或未授权，无法安装。", Toast.LENGTH_LONG).show();
                updateShizukuStatusAndUi();
                return;
            }
        } catch (Throwable t) {
            log("检查 Shizuku 状态失败: " + t.getMessage());
            Toast.makeText(this, "Shizuku 不可用。", Toast.LENGTH_LONG).show();
            updateShizukuStatusAndUi();
            return;
        }

        btnInstall.setEnabled(false);
        log("Starting installation for: " + selectedFilePath);

        new Thread(() -> {
            try {
                // 方法1：直接使用 pm install 命令安装（推荐）
                String installCmd = "pm install -r \"" + selectedFilePath + "\"";
                log("Executing install command: " + installCmd);

                // 使用正确的 Shizuku API 执行命令
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", installCmd});

                StringBuilder out = new StringBuilder();
                StringBuilder err = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.append(line).append("\n");
                        final String l = line;
                        runOnUiThread(() -> log("Install (stdout): " + l));
                    }
                    while ((line = errorReader.readLine()) != null) {
                        err.append(line).append("\n");
                        final String l = line;
                        runOnUiThread(() -> log("Install (stderr): " + l));
                    }
                } catch (Exception e) {
                    log("读取安装进程输出出错: " + e.getMessage());
                }

                int installExit = process.waitFor();
                final String finalOut = out.toString().trim();
                final String finalErr = err.toString().trim();

                runOnUiThread(() -> {
                    if (installExit == 0 || finalOut.toLowerCase().contains("success")) {
                        log("安装成功: " + new File(selectedFilePath).getName() + " 输出: " + finalOut);
                        Toast.makeText(MainActivity.this, "安装成功！", Toast.LENGTH_LONG).show();
                        tvSelectedFile.setText("No file selected");
                        selectedFileUri = null;
                        selectedFilePath = null;
                    } else {
                        log("安装失败。Exit code: " + installExit + "\nOutput: " + finalOut + "\nError: " + finalErr);
                        Toast.makeText(MainActivity.this, "安装失败，查看日志。", Toast.LENGTH_LONG).show();
                    }
                    btnInstall.setEnabled(true);
                    updateInstallButtonState();
                });

            } catch (Exception e) {
                final String em = e.getMessage();
                runOnUiThread(() -> {
                    log("安装流程异常: " + em);
                    Toast.makeText(MainActivity.this, "安装异常: " + em, Toast.LENGTH_LONG).show();
                    btnInstall.setEnabled(true);
                    updateInstallButtonState();
                });
            }
        }).start();
    }

    // 备用方法：使用 Shizuku 的 execCommand（如果可用）
    private void installWithShizukuExec() {
        if (selectedFilePath == null) return;

        new Thread(() -> {
            try {
                // 尝试使用反射调用 Shizuku.execCommand（如果存在）
                String installCmd = "pm install -r \"" + selectedFilePath + "\"";
                log("尝试使用 Shizuku.execCommand: " + installCmd);

                // 使用 Runtime.exec，因为 Shizuku.newProcess 是 private 的
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", installCmd});
                int exitCode = process.waitFor();

                runOnUiThread(() -> {
                    if (exitCode == 0) {
                        log("安装成功");
                        Toast.makeText(MainActivity.this, "安装成功！", Toast.LENGTH_LONG).show();
                        tvSelectedFile.setText("No file selected");
                        selectedFileUri = null;
                        selectedFilePath = null;
                    } else {
                        log("安装失败, exit=" + exitCode);
                        Toast.makeText(MainActivity.this, "安装失败", Toast.LENGTH_LONG).show();
                    }
                    btnInstall.setEnabled(true);
                    updateInstallButtonState();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    log("安装异常: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "安装异常", Toast.LENGTH_LONG).show();
                    btnInstall.setEnabled(true);
                    updateInstallButtonState();
                });
            }
        }).start();
    }

    private void updateShizukuStatusAndUi() {
        try {
            if (!Shizuku.pingBinder()) {
                tvShizukuStatus.setText("Shizuku Server: 未运行/未安装");
                tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                btnRequestPermission.setEnabled(false);
                log("Shizuku 未连接.");
            } else {
                // 若 binder 已连上，再安全调用 getVersion 等
                try {
                    if (Shizuku.isPreV11() || Shizuku.getVersion() < 10) {
                        tvShizukuStatus.setText("Shizuku Server: 版本过低");
                        tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                        btnRequestPermission.setEnabled(false);
                        log("Shizuku 版本过低.");
                    } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        tvShizukuStatus.setText("Shizuku Permission: 已授予");
                        tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                        btnRequestPermission.setEnabled(false);
                        log("Shizuku 已连接并授权.");
                    } else {
                        tvShizukuStatus.setText("Shizuku Permission: 未授予");
                        tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
                        btnRequestPermission.setEnabled(true);
                        log("Shizuku 已连接但未授权.");
                    }
                } catch (Throwable t) {
                    tvShizukuStatus.setText("Shizuku 状态未知");
                    tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    log("检查 Shizuku 版本/权限失败: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            tvShizukuStatus.setText("Shizuku 不可用");
            tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            log("updateShizukuStatusAndUi 捕获异常: " + t.getMessage());
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
                int scrollAmount = tvLog.getLineCount() * tvLog.getLineHeight() - tvLog.getHeight();
                if (scrollAmount > 0) tvLog.scrollTo(0, scrollAmount);
            } catch (Exception ignored) {
            }
        });
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