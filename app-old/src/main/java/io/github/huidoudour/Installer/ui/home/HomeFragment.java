package io.github.huidoudour.Installer.ui.home;

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

import io.github.huidoudour.Installer.databinding.FragmentHomeBinding;
import io.github.huidoudour.Installer.utils.LogManager;
import rikka.shizuku.Shizuku;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private LogManager logManager;
    
    private static final int REQUEST_CODE_SHIZUKU_PERMISSION = 123;
    
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
        binding = FragmentHomeBinding.inflate(inflater, container, false);
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
        log("Installer 已启动，等待操作喵……");

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
            Toast.makeText(requireContext(), "请安装文件管理器以选择安装包文件喵。", Toast.LENGTH_SHORT).show();
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
        log("Starting installation for: " + selectedFilePath);

        new Thread(() -> {
            String tmpFilePath = null;
            try {
                // 步骤 1: 复制文件到 /data/local/tmp/ (解决 SELinux 权限问题)
                File sourceFile = new File(selectedFilePath);
                String tmpFileName = "installer_" + System.currentTimeMillis() + "_" + sourceFile.getName();
                tmpFilePath = "/data/local/tmp/" + tmpFileName;
                
                log("正在复制文件到系统临时目录: " + tmpFilePath);
                
                // 使用 cat 命令复制文件（比 cp 更可靠）
                String copyCmd = "cat \"" + selectedFilePath + "\" > \"" + tmpFilePath + "\"";
                Process copyProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", copyCmd});
                int copyExit = copyProcess.waitFor();
                
                if (copyExit != 0) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            log("复制文件到临时目录失败喵 Exit code: " + copyExit);
                            Toast.makeText(requireContext(), "复制文件失败喵", Toast.LENGTH_LONG).show();
                            btnInstall.setEnabled(true);
                            updateInstallButtonState();
                        });
                    }
                    return;
                }
                
                log("文件复制成功，开始安装...");
                
                // 步骤 2: 修改文件权限，确保 system_server 可读
                String chmodCmd = "chmod 644 \"" + tmpFilePath + "\"";
                Process chmodProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", chmodCmd});
                chmodProcess.waitFor();
                
                // 步骤 3: 构建安装命令
                StringBuilder installCmd = new StringBuilder("pm install");
                if (switchReplaceExisting.isChecked()) {
                    installCmd.append(" -r");
                }
                if (switchGrantPermissions.isChecked()) {
                    installCmd.append(" -g");
                }
                installCmd.append(" \"").append(tmpFilePath).append("\"");
                
                log("Executing install command: " + installCmd.toString());

                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", installCmd.toString()});

                StringBuilder out = new StringBuilder();
                StringBuilder err = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.append(line).append("\n");
                        final String l = line;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> log("Install (stdout): " + l));
                        }
                    }
                    while ((line = errorReader.readLine()) != null) {
                        err.append(line).append("\n");
                        final String l = line;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> log("Install (stderr): " + l));
                        }
                    }
                } catch (Exception e) {
                    log("读取安装进程输出出错喵: " + e.getMessage());
                }

                int installExit = process.waitFor();
                final String finalOut = out.toString().trim();
                final String finalErr = err.toString().trim();
                final String finalTmpPath = tmpFilePath;

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (installExit == 0 || finalOut.toLowerCase().contains("success")) {
                            log("安装成功喵: " + new File(selectedFilePath).getName() + " 输出: " + finalOut);
                            Toast.makeText(requireContext(), "安装成功喵~", Toast.LENGTH_LONG).show();
                            tvSelectedFile.setText("未选择文件");
                            selectedFileUri = null;
                            selectedFilePath = null;
                        } else {
                            log("安装失败喵。Exit code: " + installExit + "\nOutput: " + finalOut + "\nError: " + finalErr);
                            Toast.makeText(requireContext(), "安装失败，查看日志喵。", Toast.LENGTH_LONG).show();
                        }
                        btnInstall.setEnabled(true);
                        updateInstallButtonState();
                        
                        // 步骤 4: 清理临时文件
                        new Thread(() -> {
                            try {
                                String rmCmd = "rm -f \"" + finalTmpPath + "\"";
                                Runtime.getRuntime().exec(new String[]{"sh", "-c", rmCmd}).waitFor();
                                log("已清理临时文件: " + finalTmpPath);
                            } catch (Exception e) {
                                log("清理临时文件失败: " + e.getMessage());
                            }
                        }).start();
                    });
                }

            } catch (Exception e) {
                final String em = e.getMessage();
                final String finalTmpPath = tmpFilePath;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        log("安装流程异常喵: " + em);
                        Toast.makeText(requireContext(), "安装异常: " + em, Toast.LENGTH_LONG).show();
                        btnInstall.setEnabled(true);
                        updateInstallButtonState();
                        
                        // 清理临时文件
                        if (finalTmpPath != null) {
                            new Thread(() -> {
                                try {
                                    String rmCmd = "rm -f \"" + finalTmpPath + "\"";
                                    Runtime.getRuntime().exec(new String[]{"sh", "-c", rmCmd}).waitFor();
                                } catch (Exception ignored) {}
                            }).start();
                        }
                    });
                }
            }
        }).start();
    }

    private void updateShizukuStatusAndUi() {
        try {
            if (!Shizuku.pingBinder()) {
                tvShizukuStatus.setText("未运行/未安装");
                tvShizukuStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                btnRequestPermission.setEnabled(false);
                log("Shizuku 未连接喵.");
            } else {
                try {
                    if (Shizuku.isPreV11() || Shizuku.getVersion() < 10) {
                        tvShizukuStatus.setText("版本过低喵");
                        tvShizukuStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                        statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                        btnRequestPermission.setEnabled(false);
                        log("Shizuku 版本过低喵.");
                    } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        tvShizukuStatus.setText("已授予喵");
                        tvShizukuStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                        statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                        btnRequestPermission.setEnabled(false);
                        log("Shizuku 已连接并授权喵.");
                    } else {
                        tvShizukuStatus.setText("未授予喵");
                        tvShizukuStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                        statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                        btnRequestPermission.setEnabled(true);
                        log("Shizuku 已连接但未授权喵.");
                    }
                } catch (Throwable t) {
                    tvShizukuStatus.setText("状态未知喵");
                    tvShizukuStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                    log("检查 Shizuku 版本/权限失败喵: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            tvShizukuStatus.setText("不可用喵");
            tvShizukuStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
            statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
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

        btnInstall.setEnabled(shizukuReady && fileSelected);
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