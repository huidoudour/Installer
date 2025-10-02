package io.github.huidoudour.Installer; // 确保这是您的正确包名

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
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_SHIZUKU_PERMISSION = 123;
    private static final int REQUEST_CODE_MANAGE_FILES_PERMISSION = 456; // 用于 Android 11+ 文件权限

    private TextView tvShizukuStatus;
    private TextView tvSelectedFile;
    private Button btnSelectFile;
    private Button btnRequestPermission;
    private Button btnInstall;
    private TextView tvLog;

    private Uri selectedFileUri;
    private String selectedFilePath; // 用于 Shizuku pm install 命令

    // Shizuku 权限请求监听器
    private final Shizuku.OnRequestPermissionResultListener onRequestPermissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        log("已授予 Shizuku 权限.");
                        updateShizukuStatusAndUi();
                    } else {
                        log("Shizuku 权限被拒绝.");
                        updateShizukuStatusAndUi();
                    }
                }
            };

    // Activity Result Launcher for file selection
    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedFileUri = result.getData().getData();
                    if (selectedFileUri != null) {
                        // 使用新的方法获取文件路径
                        selectedFilePath = getFilePathFromUri(selectedFileUri);
                        String fileName = getFileNameFromUri(selectedFileUri);

                        if (selectedFilePath != null) {
                            tvSelectedFile.setText("已选择: " + fileName);
                            log("已选择文件: " + selectedFilePath);
                        } else {
                            // 如果无法获取路径，只显示文件名
                            tvSelectedFile.setText("Selected: " + (fileName != null ? fileName : selectedFileUri.getPath()));
                            log("已选择文件 (URI): " + selectedFileUri.toString() + ". 获取路径失败.");
                        }
                        updateInstallButtonState();
                    }
                } else {
                    log("文件选择失败或被取消.");
                }
            });

    // Activity Result Launcher for MANAGE_EXTERNAL_STORAGE permission
    private final ActivityResultLauncher<Intent> manageFilesPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        log("MANAGE_EXTERNAL_STORAGE 权限拒绝.");
                        openFilePicker(); // 权限获取后再次尝试打开文件选择器
                    } else {
                        log("MANAGE_EXTERNAL_STORAGE 权限拒绝.");
                        Toast.makeText(this, "File access permission is required to select APKs.", Toast.LENGTH_LONG).show();
                    }
                }
            });

    // Launcher for READ_EXTERNAL_STORAGE permission (Android 10 and below)
    private final ActivityResultLauncher<String> externalStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    log("READ_EXTERNAL_STORAGE 权限拒绝.");
                    openFilePicker();
                } else {
                    log("READ_EXTERNAL_STORAGE 权限拒绝.");
                    Toast.makeText(this, "Read storage permission is required to select APKs.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 使用您现有的布局

        // 初始化视图
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnRequestPermission = findViewById(R.id.btnRequestPermission);
        btnInstall = findViewById(R.id.btnInstall);
        tvLog = findViewById(R.id.tvLog);

        // 使日志 TextView 可滚动
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        tvLog.setText(""); // 清空初始日志

        // 添加 Shizuku 监听器
        Shizuku.addRequestPermissionResultListener(onRequestPermissionResultListener);

        // 设置按钮点击事件
        btnSelectFile.setOnClickListener(v -> checkFilePermissionsAndOpenFilePicker());
        btnRequestPermission.setOnClickListener(v -> requestShizukuPermission());
        btnInstall.setOnClickListener(v -> installSelectedApk());

        // 初始化 Shizuku 状态和 UI
        updateShizukuStatusAndUi();
        log("App已启动，等待操作……");
    }

    // 获取文件路径的方法
    private String getFilePathFromUri(Uri uri) {
        if (uri == null) return null;

        // 如果是以 "file://" 开头的 URI，直接获取路径
        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }

        // 对于 content URI，尝试获取真实路径
        String result = null;
        ContentResolver contentResolver = getContentResolver();

        Cursor cursor = null;
        try {
            String[] projection = {OpenableColumns.DISPLAY_NAME};
            cursor = contentResolver.query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    String fileName = cursor.getString(nameIndex);
                    // 将文件复制到缓存目录
                    File cacheFile = copyUriToCache(uri, fileName);
                    if (cacheFile != null) {
                        result = cacheFile.getAbsolutePath();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    // 将 URI 指向的文件复制到缓存目录
    private File copyUriToCache(Uri uri, String fileName) {
        try {
            File cacheDir = getCacheDir();
            File outputFile = new File(cacheDir, fileName);

            InputStream inputStream = getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            outputStream.close();

            return outputFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 获取文件名的方法
    private String getFileNameFromUri(Uri uri) {
        if (uri == null) return null;

        String result = null;
        ContentResolver contentResolver = getContentResolver();

        Cursor cursor = null;
        try {
            String[] projection = {OpenableColumns.DISPLAY_NAME};
            cursor = contentResolver.query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (result == null) {
            result = uri.getLastPathSegment();
        }

        return result;
    }

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
        } else { // Android 10 及以下
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
        intent.setType("application/vnd.android.package-archive"); // Filter for APK files
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, "Select APK file"));
            log("打开文件选择器...");
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
            log("文件选择器未找到.");
        }
    }

    private void requestShizukuPermission() {
        log("尝试请求Shizuku权限……");
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            log("Shizuku version is too old or Shizuku app not found.");
            Toast.makeText(this, "Shizuku app not found or version too old. Please install/update Shizuku.", Toast.LENGTH_LONG).show();
            updateShizukuStatusAndUi();
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION);
        } else {
            log("以授予 Shizuku 权限.");
            Toast.makeText(this, "Shizuku permission is already granted.", Toast.LENGTH_SHORT).show();
            updateShizukuStatusAndUi();
        }
    }

    private void installSelectedApk() {
        if (selectedFilePath == null || selectedFilePath.isEmpty()) {
            log("未选择APK文件或路径无效.");
            Toast.makeText(this, "Please select an APK file first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            log("Shizuku 权限未授予. 未安装Shizuku.");
            Toast.makeText(this, "Shizuku permission is required to install.", Toast.LENGTH_SHORT).show();
            return;
        }

        log("Starting installation for: " + selectedFilePath);
        btnInstall.setEnabled(false);

        new Thread(() -> {
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            int exitCode = -1;

            try {
                // 先把文件复制到 /data/local/tmp
                String tmpPath = "/data/local/tmp/tmp_app.apk";
                String copyCmd = "cp \"" + selectedFilePath + "\" \"" + tmpPath + "\" && chmod 666 \"" + tmpPath + "\"";
                log("Executing copy command: " + copyCmd);
                Process copyProcess = new ProcessBuilder("sh", "-c", copyCmd).start();
                int copyExit = copyProcess.waitFor();
                if (copyExit != 0) {
                    runOnUiThread(() -> {
                        log("复制APK到 /data/local/tmp 失败, exit=" + copyExit);
                        Toast.makeText(MainActivity.this, "Copy failed, cannot install.", Toast.LENGTH_LONG).show();
                        updateInstallButtonState();
                    });
                    return;
                }

                // 再执行安装
                String[] cmd = {"sh", "-c", "cmd package install -r -d \"" + tmpPath + "\""};
                log("Executing install command: " + String.join(" ", cmd));
                ProcessBuilder processBuilder = new ProcessBuilder(cmd);
                Process process = processBuilder.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        final String currentLine = line;
                        runOnUiThread(() -> log("Install (stdout): " + currentLine));
                    }
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                        final String currentLine = line;
                        runOnUiThread(() -> log("Install (stderr): " + currentLine));
                    }
                }
                exitCode = process.waitFor();

            } catch (Exception e) {
                final String errorMsg = e.getMessage();
                runOnUiThread(() -> {
                    log("Exception during installation: " + errorMsg);
                    Toast.makeText(MainActivity.this, "Installation Error: " + errorMsg, Toast.LENGTH_LONG).show();
                    updateInstallButtonState();
                });
                return;
            }

            final String finalOutput = output.toString().trim();
            final String finalErrorOutput = errorOutput.toString().trim();
            final int finalExitCode = exitCode;

            runOnUiThread(() -> {
                if (finalExitCode == 0 || finalOutput.toLowerCase().contains("success")) {
                    log("Installation successful for " + new File(selectedFilePath).getName() + ". Output: " + finalOutput);
                    Toast.makeText(MainActivity.this, "Installation successful!", Toast.LENGTH_LONG).show();
                    tvSelectedFile.setText("No file selected");
                    selectedFileUri = null;
                    selectedFilePath = null;
                } else {
                    log("Installation failed. Exit code: " + finalExitCode + "\nOutput: " + finalOutput + "\nError: " + finalErrorOutput);
                    Toast.makeText(MainActivity.this, "Installation failed. Check logs.", Toast.LENGTH_LONG).show();
                }
                updateInstallButtonState();
            });
        }).start();
    }


    private void updateShizukuStatusAndUi() {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 10) { // Shizuku 建议至少版本 10
            tvShizukuStatus.setText("Shizuku Server: Shizuku未运行喵");
            tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            btnRequestPermission.setEnabled(true); // Allow attempting to request
            log("Shizuku status: 未运行或版本过低.");
        } else {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                tvShizukuStatus.setText("Shizuku Permission: 授予喵");
                tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                btnRequestPermission.setEnabled(false); // No need to request if already granted
                log("Shizuku status: 已连接并授权.");
            } else {
                tvShizukuStatus.setText("Shizuku Permission: 未授予喵");
                tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
                btnRequestPermission.setEnabled(true);
                log("Shizuku status: 已连接未授权.");
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    log("Shizuku: 请求权限时显示理由.");
                    // You could show a dialog here explaining why you need the permission
                }
            }
        }
        updateInstallButtonState();
    }

    private void updateInstallButtonState() {
        boolean shizukuReady = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED &&
                !(Shizuku.isPreV11() || Shizuku.getVersion() < 10);
        boolean fileSelected = selectedFilePath != null && !selectedFilePath.isEmpty();

        if (shizukuReady && fileSelected) {
            btnInstall.setEnabled(true);
            btnInstall.setBackgroundColor(ContextCompat.getColor(this, R.color.install_button_enabled_color)); // 您需要在 colors.xml 定义这个颜色
            btnInstall.setTextColor(ContextCompat.getColor(this, R.color.install_button_enabled_text_color)); // 例如 #FFFFFF
        } else {
            btnInstall.setEnabled(false);
            btnInstall.setBackgroundColor(ContextCompat.getColor(this, R.color.install_button_disabled_color)); // 例如 #9E9E9E (来自您的 XML)
            btnInstall.setTextColor(ContextCompat.getColor(this, R.color.install_button_disabled_text_color)); // 例如 #FFFFFF
        }
    }

    // Helper method to log messages to both Logcat and the on-screen TextView
    @SuppressLint("SetTextI18n")
    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logMessage = timestamp + ": " + message;
        System.out.println("ShizukuInstallerApp: " + logMessage); // Logcat
        runOnUiThread(() -> {
            tvLog.append(logMessage + "\n");

            // 简化的自动滚动 - 更安全的方式
            // 使用post确保在布局完成后执行滚动
            tvLog.post(() -> {
                try {
                    int scrollAmount = tvLog.getLineCount() * tvLog.getLineHeight() - tvLog.getHeight();
                    if (scrollAmount > 0) {
                        tvLog.scrollTo(0, scrollAmount);
                    }
                } catch (Exception e) {
                    // 忽略滚动错误，至少日志已经添加
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Shizuku 状态可能在应用外部发生变化（例如，用户在 Shizuku Manager 中撤销权限）
        // 所以在 onResume 中更新是个好主意
        updateShizukuStatusAndUi();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(onRequestPermissionResultListener);
        super.onDestroy();
    }

    // 如果您需要处理 Android 6.0+ 的运行时权限结果（非 Shizuku，非 MANAGE_EXTERNAL_STORAGE）
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Shizuku.onRequestPermissionsResult(requestCode, permissions, grantResults); // Shizuku 自己的权限结果由 listener 处理
        if (requestCode == REQUEST_CODE_MANAGE_FILES_PERMISSION) { // 虽然我们用了 Launcher，但以防万一
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    log("MANAGE_EXTERNAL_STORAGE permission granted via onRequestPermissionsResult.");
                    openFilePicker();
                } else {
                    log("MANAGE_EXTERNAL_STORAGE permission denied via onRequestPermissionsResult.");
                }
            }
        }
    }
}