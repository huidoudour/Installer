package io.github.huidoudour.Installer.debug.ui.installer;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.huidoudour.Installer.debug.databinding.FragmentInstallerBinding;
import io.github.huidoudour.Installer.debug.utils.LogManager;
import io.github.huidoudour.Installer.debug.utils.ApkAnalyzer;
import rikka.shizuku.Shizuku;

public class InstallerFragment extends Fragment {

    private FragmentInstallerBinding binding;
    private LogManager logManager;
    
    private static final int REQUEST_CODE_SHIZUKU_PERMISSION = 123;
    
    // 添加标志位，避免重复输出初始化日志
    private static boolean isFirstInit = true;
    private String lastShizukuStatus = ""; // 记录上次状态，避免重复日志
    
    private TextView tvShizukuStatus;
    private TextView tvSelectedFile;
    private Button btnSelectFile;
    private Button btnRequestPermission;
    private Button btnInstall;
    private SwitchMaterial switchReplaceExisting;
    private SwitchMaterial switchGrantPermissions;
    private View statusIndicator;
    
    private Uri selectedFileUri;
    private String selectedFilePath;

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

    // 文件选择 Launcher
    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedFileUri = result.getData().getData();
                    if (selectedFileUri != null) {
                        String fileName = getFileNameFromUri(selectedFileUri);
                        selectedFilePath = getFilePathFromUri(selectedFileUri);
                        if (selectedFilePath != null) {
                            tvSelectedFile.setText(fileName);
                            log("已选择文件并复制到 cache: " + selectedFilePath);
                            
                            // === 使用原生库分析 APK ===
                            analyzeApk(selectedFilePath);
                        } else {
                            tvSelectedFile.setText(fileName != null ? fileName : selectedFileUri.getPath());
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
                        openFilePicker();
                    } else {
                        log("MANAGE_EXTERNAL_STORAGE 权限被拒绝.");
                        Toast.makeText(requireContext(), "需要文件访问权限以选择 APK。", Toast.LENGTH_LONG).show();
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
                    Toast.makeText(requireContext(), "需要读取存储权限以选择 APK。", Toast.LENGTH_LONG).show();
                }
            });

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentInstallerBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 初始化视图
        tvShizukuStatus = binding.tvShizukuStatus;
        tvSelectedFile = binding.tvSelectedFile;
        btnSelectFile = binding.btnSelectFile;
        btnRequestPermission = binding.btnRequestPermission;
        btnInstall = binding.btnInstall;
        switchReplaceExisting = binding.switchReplaceExisting;
        switchGrantPermissions = binding.switchGrantPermissions;
        statusIndicator = binding.statusIndicator;

        // 初始化日志管理器
        logManager = LogManager.getInstance();

        // 注册 Shizuku 权限结果监听
        try {
            Shizuku.addRequestPermissionResultListener(onRequestPermissionResultListener);
        } catch (Throwable e) {
            log("Shizuku 不可用喵: " + e.getMessage());
        }

        // 添加 binder received listener
        try {
            Shizuku.addBinderReceivedListener(() -> {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::updateShizukuStatusAndUi);
                }
            });
        } catch (Throwable t) {
            log("不能添加 Shizuku binder listener 喵: " + t.getMessage());
        }

        // 设置按钮点击事件
        btnSelectFile.setOnClickListener(v -> checkFilePermissionsAndOpenFilePicker());
        btnRequestPermission.setOnClickListener(v -> requestShizukuPermission());
        btnInstall.setOnClickListener(v -> installSelectedApk());

        // 初始 UI 状态
        updateShizukuStatusAndUi();
        
        // 只在第一次初始化时输出日志
        if (isFirstInit) {
            log("Installer 已启动，等待操作喵……");
            isFirstInit = false;
        }

        return root;
    }

    private void log(String message) {
        // 使用全局日志管理器
        logManager.addLog(message);
    }
    private String getFilePathFromUri(Uri uri) {
        if (uri == null) return null;

        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }

        ContentResolver contentResolver = requireContext().getContentResolver();
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

    private File copyUriToCache(Uri uri, String fileName) {
        try {
            ParcelFileDescriptor pfd = requireContext().getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) return null;
            FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
            File outputFile = new File(requireContext().getCacheDir(), fileName);
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

    private String getFileNameFromUri(Uri uri) {
        if (uri == null) return null;
        String result = null;
        Cursor cursor = null;
        try {
            String[] projection = {OpenableColumns.DISPLAY_NAME};
            cursor = requireContext().getContentResolver().query(uri, projection, null, null, null);
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

    private void checkFilePermissionsAndOpenFilePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                log("请求 MANAGE_EXTERNAL_STORAGE 权限喵.");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", requireContext().getApplicationContext().getPackageName())));
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
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                log("请求 READ_EXTERNAL_STORAGE 权限喵.");
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
            filePickerLauncher.launch(Intent.createChooser(intent, "选择 APK 文件喵"));
            log("打开文件选择器喵~...");
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(requireContext(), "请安装文件管理器以选择 APK喵。", Toast.LENGTH_SHORT).show();
            log("文件选择器未找到喵.");
        }
    }

    private void requestShizukuPermission() {
        log("尝试请求 Shizuku 权限喵~...");
        try {
            if (!Shizuku.pingBinder()) {
                log("Shizuku没启动喵~。");
                Toast.makeText(requireContext(), "Shizuku 未运行或未安装喵", Toast.LENGTH_LONG).show();
                updateShizukuStatusAndUi();
                return;
            }
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                log("Shizuku 版本过低或不兼容喵。");
                Toast.makeText(requireContext(), "请升级Shizuku喵", Toast.LENGTH_LONG).show();
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

    private void installSelectedApk() {
        if (selectedFilePath == null || selectedFilePath.isEmpty()) {
            log("未选择 APK 或路径无效喵.");
            Toast.makeText(requireContext(), "请先选择 APK 文件喵.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                log("Shizuku 未连接或未授权，无法通过 Shizuku 安装喵.");
                Toast.makeText(requireContext(), "Shizuku 未连接或未授权，无法安装喵。", Toast.LENGTH_LONG).show();
                updateShizukuStatusAndUi();
                return;
            }
        } catch (Throwable t) {
            log("检查 Shizuku 状态失败喵: " + t.getMessage());
            Toast.makeText(requireContext(), "Shizuku 不可用喵", Toast.LENGTH_LONG).show();
            updateShizukuStatusAndUi();
            return;
        }

        btnInstall.setEnabled(false);
        log("=== 开始安装流程 ===");
        log("APK路径: " + selectedFilePath);

        new Thread(() -> {
            FileInputStream fis = null;
            try {
                File apkFile = new File(selectedFilePath);
                log("APK文件大小: " + apkFile.length() + " bytes");
                
                // 使用 Shizuku 执行 shell 命令的正确方法
                // 方法1: 使用 pm install-create/install-write/install-commit 流程
                
                // 步骤1: 创建安装会话
                StringBuilder createCmd = new StringBuilder("pm install-create");
                if (switchReplaceExisting.isChecked()) {
                    createCmd.append(" -r");
                }
                if (switchGrantPermissions.isChecked()) {
                    createCmd.append(" -g");
                }
                // 添加安装执行者参数 (-i 指定installer包名)
                createCmd.append(" -i io.github.huidoudour.zjs");
                
                log("创建安装会话: " + createCmd);
                String createOutput = executeShizukuCommand(createCmd.toString());
                log("会话创建输出: " + createOutput);
                
                // 解析会话ID (格式: "Success: created install session [123]")
                if (createOutput == null || !createOutput.contains("Success")) {
                    throw new Exception("创建安装会话失败: " + createOutput);
                }
                
                String sessionId = createOutput.substring(
                    createOutput.indexOf("[") + 1,
                    createOutput.indexOf("]")
                );
                log("会话ID: " + sessionId);
                
                // 步骤2: 写入APK数据到会话
                String writeCmd = "pm install-write -S " + apkFile.length() + " " + sessionId + " base.apk -";
                log("写入APK数据: " + writeCmd);
                
                String writeOutput = executeShizukuCommandWithInput(writeCmd, apkFile);
                log("写入结果: " + writeOutput);
                
                if (writeOutput == null || !writeOutput.contains("Success")) {
                    throw new Exception("写入APK失败: " + writeOutput);
                }
                
                // 步骤3: 提交安装
                String commitCmd = "pm install-commit " + sessionId;
                log("提交安装: " + commitCmd);
                String commitOutput = executeShizukuCommand(commitCmd);
                
                final String finalOut = commitOutput != null ? commitOutput.trim() : "";
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (finalOut.toLowerCase().contains("success")) {
                            log("✓ 安装成功喵: " + apkFile.getName());
                            log("输出: " + finalOut);
                            Toast.makeText(requireContext(), "安装成功喵~", Toast.LENGTH_LONG).show();
                            tvSelectedFile.setText("未选择文件");
                            selectedFileUri = null;
                            selectedFilePath = null;
                        } else {
                            log("✗ 安装失败喵");
                            log("输出: " + finalOut);
                            Toast.makeText(requireContext(), "安装失败，查看日志喵。", Toast.LENGTH_LONG).show();
                        }
                        log("=== 安装流程结束 ===");
                        btnInstall.setEnabled(true);
                        updateInstallButtonState();
                    });
                }

            } catch (Exception e) {
                final String em = e.getMessage();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        log("✗ 安装流程异常喵: " + em);
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "安装异常: " + em, Toast.LENGTH_LONG).show();
                        log("=== 安装流程异常结束 ===");
                        btnInstall.setEnabled(true);
                        updateInstallButtonState();
                    });
                }
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    // 使用 Shizuku 执行 shell 命令
    private String executeShizukuCommand(String command) throws Exception {
        try {
            // 使用反射调用 Shizuku 的隐藏 API
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

    // 使用 Shizuku 执行 shell 命令并传入文件数据
    private String executeShizukuCommandWithInput(String command, File inputFile) throws Exception {
        try {
            // 使用反射调用 Shizuku 的隐藏 API
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
            
            // 将文件数据写入进程的标准输入
            FileInputStream fis = new FileInputStream(inputFile);
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
            throw new Exception("执行命令失败: " + e.getMessage(), e);
        }
    }

    private void updateShizukuStatusAndUi() {
        String currentStatus = "";
        try {
            if (!Shizuku.pingBinder()) {
                currentStatus = "未连接";
                tvShizukuStatus.setText("未运行/未安装");
                tvShizukuStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                btnRequestPermission.setEnabled(false);
                if (!currentStatus.equals(lastShizukuStatus)) {
                    log("Shizuku 未连接喵.");
                }
            } else {
                try {
                    if (Shizuku.isPreV11() || Shizuku.getVersion() < 10) {
                        currentStatus = "版本过低";
                        tvShizukuStatus.setText("版本过低喵");
                        tvShizukuStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                        statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                        btnRequestPermission.setEnabled(false);
                        if (!currentStatus.equals(lastShizukuStatus)) {
                            log("Shizuku 版本过低喵.");
                        }
                    } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        currentStatus = "已授权";
                        tvShizukuStatus.setText("已授予喵");
                        tvShizukuStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                        statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                        btnRequestPermission.setEnabled(false);
                        // 只在状态变化或第一次初始化时输出日志
                        if (!currentStatus.equals(lastShizukuStatus)) {
                            log("Shizuku 已连接并授权喵.");
                        }
                    } else {
                        currentStatus = "未授权";
                        tvShizukuStatus.setText("未授予喵");
                        tvShizukuStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                        statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                        btnRequestPermission.setEnabled(true);
                        if (!currentStatus.equals(lastShizukuStatus)) {
                            log("Shizuku 已连接但未授权喵.");
                        }
                    }
                } catch (Throwable t) {
                    currentStatus = "状态未知";
                    tvShizukuStatus.setText("状态未知喵");
                    tvShizukuStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                    if (!currentStatus.equals(lastShizukuStatus)) {
                        log("检查 Shizuku 版本/权限失败喵: " + t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            currentStatus = "不可用";
            tvShizukuStatus.setText("不可用喵");
            tvShizukuStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
            statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
            if (!currentStatus.equals(lastShizukuStatus)) {
                log("updateShizukuStatusAndUi 捕获异常喵: " + t.getMessage());
            }
        }
        lastShizukuStatus = currentStatus; // 更新状态
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

        btnInstall.setEnabled(shizukuReady && fileSelected);
    }

    /**
     * 使用原生库分析 APK 文件
     * 这里使用了多个包含 .so 文件的库：
     * - java.security (MessageDigest - 原生加密库)
     * - conscrypt (高性能加密)
     * - apksig (签名验证)
     */
    private void analyzeApk(String apkPath) {
        log("");
        log("=== 开始分析 APK （使用原生库）===");
        
        new Thread(() -> {
            try {
                // 1. 文件基本信息
                String fileSize = ApkAnalyzer.getFileSize(apkPath);
                log("📁 文件大小: " + fileSize);
                
                // 2. APK 包名和版本
                String packageName = ApkAnalyzer.getPackageName(requireContext(), apkPath);
                if (packageName != null) {
                    log("📦 包名: " + packageName);
                }
                
                String versionInfo = ApkAnalyzer.getVersionInfo(requireContext(), apkPath);
                if (versionInfo != null) {
                    log("🔢 版本: " + versionInfo);
                }
                
                // 3. 文件哈希值（使用 MessageDigest 原生库）
                log("");
                log("🔐 正在计算哈希值（使用原生加密库）...");
                
                String md5 = ApkAnalyzer.calculateMD5(apkPath);
                if (md5 != null) {
                    log("   MD5: " + md5);
                }
                
                String sha256 = ApkAnalyzer.calculateSHA256(apkPath);
                if (sha256 != null) {
                    log("   SHA-256: " + sha256);
                }
                
                // 4. 签名信息（使用 CertificateFactory 原生库）
                log("");
                log("✒️ 签名信息：");
                java.util.List<String> sigInfo = ApkAnalyzer.getSignatureInfo(requireContext(), apkPath);
                for (String info : sigInfo) {
                    log("   " + info);
                }
                
                log("");
                log("✅ APK 分析完成！");
                log("=== 分析结束 ===");
                log("");
                
            } catch (Exception e) {
                log("❌ APK 分析失败: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateShizukuStatusAndUi();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            Shizuku.removeRequestPermissionResultListener(onRequestPermissionResultListener);
        } catch (Throwable ignored) {}
        binding = null;
    }
}