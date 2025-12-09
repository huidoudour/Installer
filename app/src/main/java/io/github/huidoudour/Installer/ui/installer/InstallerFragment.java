package io.github.huidoudour.Installer.ui.installer;

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.databinding.FragmentInstallerBinding;
import io.github.huidoudour.Installer.utils.LogManager;

import io.github.huidoudour.Installer.utils.XapkInstaller;
import io.github.huidoudour.Installer.utils.ShizukuInstallHelper;
import io.github.huidoudour.Installer.utils.DhizukuInstallHelper;
import io.github.huidoudour.Installer.utils.PrivilegeHelper;
import io.github.huidoudour.Installer.utils.PrivilegeHelper.PrivilegeMode;
import io.github.huidoudour.Installer.utils.PrivilegeHelper.PrivilegeStatus;
import rikka.shizuku.Shizuku;

public class InstallerFragment extends Fragment {

    private FragmentInstallerBinding binding;
    private LogManager logManager;

    private static final int REQUEST_CODE_SHIZUKU_PERMISSION = 123;

    // 添加标志位，避免重复输出初始化日志
    private static boolean isFirstInit = true;
    private String lastPrivilegeStatus = ""; // 记录上次状态，避免重复日志

    private TextView tvPrivilegeTitle;  // 授权器标题
    private TextView tvPrivilegeStatus; // 授权器状态
    private TextView tvSelectedFile;
    private TextView tvFileType;  // 新增：文件类型显示
    private Button btnSelectFile;
    private Button btnRequestPermission;
    private Button btnSwitchPrivilege;  // 切换授权器按钮
    private Button btnInstall;
    private SwitchMaterial switchReplaceExisting;
    private SwitchMaterial switchGrantPermissions;
    private View statusIndicator;

    private Uri selectedFileUri;
    private String selectedFilePath;
    private boolean isXapkFile = false;  // 新增：标记是否为 XAPK 文件

    // Shizuku 权限请求监听器
    private final Shizuku.OnRequestPermissionResultListener onRequestPermissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        log(getString(R.string.shizuku_permission_granted));
                    } else {
                        log(getString(R.string.shizuku_permission_denied));
                    }
                    updatePrivilegeStatusAndUi();
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

                            // === 检测文件类型并显示 ===
                            isXapkFile = XapkInstaller.isXapkFile(selectedFilePath);
                            String fileType = XapkInstaller.getFileTypeDescription(selectedFilePath);
                            tvFileType.setText(fileType);
                            tvFileType.setVisibility(View.VISIBLE);

                            // 如果是 XAPK，显示包含的 APK 数量
                            if (isXapkFile) {
                                int apkCount = XapkInstaller.getApkCount(requireContext(), selectedFilePath);
                            log(getString(R.string.detected_file_type_apk_count, fileType, apkCount));
                            }

                            log(getString(R.string.file_selected_and_cached, selectedFilePath));


                        } else {
                            tvSelectedFile.setText(fileName != null ? fileName : selectedFileUri.getPath());
                            log(getString(R.string.file_selected_uri_fail, selectedFileUri.toString()));
                        }
                        updateInstallButtonState();
                    }
                } else {
                    log(getString(R.string.file_selection_failed));
                }
            });

    // MANAGE_EXTERNAL_STORAGE launcher（Android 11+）
    private final ActivityResultLauncher<Intent> manageFilesPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        log(getString(R.string.manage_storage_permission_granted));
                        openFilePicker();
                    } else {
                        log(getString(R.string.manage_storage_permission_denied));
                        Toast.makeText(requireContext(), getString(R.string.need_file_access_permission_select_apk), Toast.LENGTH_LONG).show();
                    }
                }
            });

    // READ_EXTERNAL_STORAGE launcher (Android 10 及以下)
    private final ActivityResultLauncher<String> externalStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    log(getString(R.string.read_storage_permission_granted));
                    openFilePicker();
                } else {
                    log(getString(R.string.read_storage_permission_denied));
                    Toast.makeText(requireContext(), "需要读取存储权限以选择 APK。", Toast.LENGTH_LONG).show();
                }
            });

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentInstallerBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 初始化视图
        tvPrivilegeTitle = binding.tvPrivilegeTitle;
        tvPrivilegeStatus = binding.tvPrivilegeStatus;
        tvSelectedFile = binding.tvSelectedFile;
        tvFileType = binding.tvFileType;  // 新增
        btnSelectFile = binding.btnSelectFile;
        btnRequestPermission = binding.btnRequestPermission;
        btnSwitchPrivilege = binding.btnSwitchPrivilege;
        btnInstall = binding.btnInstall;
        switchReplaceExisting = binding.switchReplaceExisting;
        switchGrantPermissions = binding.switchGrantPermissions;
        statusIndicator = binding.statusIndicator;

        // 初始化日志管理器
        logManager = LogManager.getInstance();
        logManager.setContext(requireContext());

        // 注册 Shizuku 权限结果监听
        try {
            Shizuku.addRequestPermissionResultListener(onRequestPermissionResultListener);
        } catch (Throwable e) {
            log(getString(R.string.shizuku_unavailable, e.getMessage()));
        }

        // 添加 binder received listener
        try {
            Shizuku.addBinderReceivedListener(() -> {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::updatePrivilegeStatusAndUi);
                }
            });
        } catch (Throwable t) {
            log(getString(R.string.shizuku_binder_listener_error, t.getMessage()));
        }

        // 设置按钮点击事件
        btnSelectFile.setOnClickListener(v -> checkFilePermissionsAndOpenFilePicker());
        btnRequestPermission.setOnClickListener(v -> requestPrivilegePermission());
        btnSwitchPrivilege.setOnClickListener(v -> switchPrivilegeMode());
        btnInstall.setOnClickListener(v -> installSelectedApk());

        // 初始 UI 状态
        updatePrivilegeStatusAndUi();

        // 只在第一次初始化时输出日志

        // 处理从其他应用传递过来的安装URI
        handleInstallUriFromIntent();

        // 处理从HomeActivity传递过来的安装参数
        handleInstallArguments();

        if (isFirstInit) {
            log(getString(R.string.installer_started));
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
            log(getString(R.string.get_file_path_uri_error, e.getMessage()));
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
            log(getString(R.string.copy_to_cache_error, e.getMessage()));
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
                log(getString(R.string.request_manage_storage));
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", requireContext().getApplicationContext().getPackageName())));
                    manageFilesPermissionLauncher.launch(intent);
                } catch (Exception e) {
                    log(getString(R.string.request_manage_storage_error, e.getMessage()));
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    manageFilesPermissionLauncher.launch(intent);
                }
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                log(getString(R.string.request_read_storage));
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
            log(getString(R.string.open_file_picker));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(requireContext(), getString(R.string.file_picker_not_found), Toast.LENGTH_SHORT).show();
            log(getString(R.string.file_picker_not_found));
        }
    }

    /**
     * 切换授权器模式
     */
    private void switchPrivilegeMode() {
        PrivilegeMode newMode = PrivilegeHelper.switchMode(requireContext());
        String modeName = PrivilegeHelper.getModeName(newMode);
        log("已切换到 " + modeName);
        updatePrivilegeStatusAndUi();
    }

    /**
     * 请求当前授权器权限
     */
    private void requestPrivilegePermission() {
        PrivilegeMode currentMode = PrivilegeHelper.getCurrentMode(requireContext());
        PrivilegeStatus status = PrivilegeHelper.getStatus(requireContext(), currentMode);
        String modeName = PrivilegeHelper.getModeName(currentMode);
        
        switch (status) {
            case NOT_INSTALLED:
                PrivilegeHelper.openGithubPage(requireContext(), currentMode);
                log(modeName + " 未安装，正在打开 GitHub 下载页面...");
                break;
            case NOT_RUNNING:
                PrivilegeHelper.openPrivilegeApp(requireContext(), currentMode);
                log("正在打开 " + modeName + " 应用...");
                break;
            case NOT_AUTHORIZED:
            case VERSION_TOO_LOW:
                if (currentMode == PrivilegeMode.SHIZUKU) {
                    log(getString(R.string.request_shizuku_permission));
                    try {
                        if (!Shizuku.pingBinder()) {
                            log(getString(R.string.shizuku_not_started));
                            Toast.makeText(requireContext(), getString(R.string.shizuku_not_running), Toast.LENGTH_LONG).show();
                            updatePrivilegeStatusAndUi();
                            return;
                        }
                        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                            log(getString(R.string.shizuku_version_low));
                            Toast.makeText(requireContext(), getString(R.string.shizuku_version_too_low), Toast.LENGTH_LONG).show();
                            updatePrivilegeStatusAndUi();
                            return;
                        }
                        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION);
                        } else {
                            log(getString(R.string.shizuku_authorized));
                            updatePrivilegeStatusAndUi();
                        }
                    } catch (Throwable t) {
                        log("Shizuku 不可用喵: " + t.getMessage());
                        updatePrivilegeStatusAndUi();
                    }
                } else {
                    PrivilegeHelper.requestDhizukuPermission();
                    log("正在请求 Dhizuku 授权...");
                }
                break;
            case AUTHORIZED:
                log(modeName + " 已授权");
                break;
        }
    }

    private void installSelectedApk() {
        if (selectedFilePath == null || selectedFilePath.isEmpty()) {
            log(getString(R.string.apk_not_selected));
            Toast.makeText(requireContext(), getString(R.string.please_select_install_file_miao), Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查当前授权器状态
        PrivilegeMode currentMode = PrivilegeHelper.getCurrentMode(requireContext());
        PrivilegeStatus status = PrivilegeHelper.getStatus(requireContext(), currentMode);
        String modeName = PrivilegeHelper.getModeName(currentMode);
        
        if (status != PrivilegeStatus.AUTHORIZED) {
            log(modeName + " 未连接或未授权，无法安装喵.");
            Toast.makeText(requireContext(), modeName + " 未连接或未授权，无法安装喵。", Toast.LENGTH_LONG).show();
            updatePrivilegeStatusAndUi();
            return;
        }

        btnInstall.setEnabled(false);
        log("");
        log(getString(R.string.start_apk_install));

        // === 根据文件类型和授权器选择安装方式 ===
        if (isXapkFile) {
            // XAPK/APKS 安装
            if (currentMode == PrivilegeMode.SHIZUKU) {
                ShizukuInstallHelper.installXapk(
                    requireContext(),
                    selectedFilePath,
                    switchReplaceExisting.isChecked(),
                    switchGrantPermissions.isChecked(),
                    createInstallCallback()
                );
            } else {
                DhizukuInstallHelper.installXapk(
                    requireContext(),
                    selectedFilePath,
                    switchReplaceExisting.isChecked(),
                    switchGrantPermissions.isChecked(),
                    createInstallCallback()
                );
            }
        } else {
            // 单个 APK 安装
            if (currentMode == PrivilegeMode.SHIZUKU) {
                ShizukuInstallHelper.installSingleApk(
                    requireContext(),
                    new File(selectedFilePath),
                    switchReplaceExisting.isChecked(),
                    switchGrantPermissions.isChecked(),
                    createInstallCallback()
                );
            } else {
                DhizukuInstallHelper.installSingleApk(
                    requireContext(),
                    new File(selectedFilePath),
                    switchReplaceExisting.isChecked(),
                    switchGrantPermissions.isChecked(),
                    createInstallCallback()
                );
            }
        }
    }
    
    /**
     * 创建安装回调接口
     */
    private ShizukuInstallHelper.InstallCallback createInstallCallback() {
        return new ShizukuInstallHelper.InstallCallback() {
            @Override
            public void onProgress(String message) {
                log(message);
            }

            @Override
            public void onSuccess(String message) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        log(message);
                        log(getString(R.string.install_process_end));
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        clearSelection();
                        btnInstall.setEnabled(true);
                        updateInstallButtonState();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        log("❌ " + error);
                        log(getString(R.string.install_process_end));
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                        btnInstall.setEnabled(true);
                        updateInstallButtonState();
                    });
                }
            }
        };
    }

    /**
     * 清除选择的文件
     */
    private void clearSelection() {
        tvSelectedFile.setText(R.string.no_file_selected);
        tvFileType.setVisibility(View.GONE);
        selectedFileUri = null;
        selectedFilePath = null;
        isXapkFile = false;
    }

    /**
     * 更新授权器状态和UI
     */
    private void updatePrivilegeStatusAndUi() {
        PrivilegeMode currentMode = PrivilegeHelper.getCurrentMode(requireContext());
        PrivilegeStatus status = PrivilegeHelper.getStatus(requireContext(), currentMode);
        String modeName = PrivilegeHelper.getModeName(currentMode);
        
        // 更新标题
        tvPrivilegeTitle.setText(modeName + " 状态");
        
        // 根据状态更新UI
        String currentStatus = "";
        switch (status) {
            case NOT_INSTALLED:
                currentStatus = "未安装";
                tvPrivilegeStatus.setText(modeName + " 未安装喵");
                tvPrivilegeStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                btnRequestPermission.setText("下载 " + modeName);
                btnRequestPermission.setEnabled(true);
                if (!currentStatus.equals(lastPrivilegeStatus)) {
                    log(modeName + " 未安装");
                }
                break;
                
            case NOT_RUNNING:
                currentStatus = getString(R.string.not_connected);
                tvPrivilegeStatus.setText(modeName + " 未运行喵");
                tvPrivilegeStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                btnRequestPermission.setText("打开 " + modeName);
                btnRequestPermission.setEnabled(true);
                if (!currentStatus.equals(lastPrivilegeStatus)) {
                    log(modeName + " 未连接");
                }
                break;
                
            case VERSION_TOO_LOW:
                currentStatus = getString(R.string.version_too_low);
                tvPrivilegeStatus.setText("版本过低喵");
                tvPrivilegeStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                btnRequestPermission.setText("更新 " + modeName);
                btnRequestPermission.setEnabled(true);
                if (!currentStatus.equals(lastPrivilegeStatus)) {
                    log(modeName + " 版本过低");
                }
                break;
                
            case NOT_AUTHORIZED:
                currentStatus = getString(R.string.not_authorized);
                tvPrivilegeStatus.setText("未授权喵");
                tvPrivilegeStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                btnRequestPermission.setText("请求授权");
                btnRequestPermission.setEnabled(true);
                if (!currentStatus.equals(lastPrivilegeStatus)) {
                    log(modeName + " 已连接但未授权");
                }
                break;
                
            case AUTHORIZED:
                currentStatus = getString(R.string.authorized);
                tvPrivilegeStatus.setText("权限已授予喵");
                tvPrivilegeStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                btnRequestPermission.setText("已授权");
                btnRequestPermission.setEnabled(false);
                if (!currentStatus.equals(lastPrivilegeStatus)) {
                    log(modeName + " 已连接并已授权");
                }
                break;
        }
        
        lastPrivilegeStatus = currentStatus;
        updateInstallButtonState();
    }

    private void updateInstallButtonState() {
        boolean fileSelected = selectedFilePath != null && !selectedFilePath.isEmpty();
        
        // 检查当前授权器是否就绪
        PrivilegeMode currentMode = PrivilegeHelper.getCurrentMode(requireContext());
        PrivilegeStatus status = PrivilegeHelper.getStatus(requireContext(), currentMode);
        boolean privilegeReady = (status == PrivilegeStatus.AUTHORIZED);

        btnInstall.setEnabled(privilegeReady && fileSelected);
    }



    @Override
    public void onResume() {
        super.onResume();
        updatePrivilegeStatusAndUi();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            Shizuku.removeRequestPermissionResultListener(onRequestPermissionResultListener);
        } catch (Throwable ignored) {}
        binding = null;
    }

    /**
     * 处理从其他应用传递过来的安装URI
     * 这个方法会在onCreateView中被调用，用于处理外部应用发起的安装请求
     */
    private void handleInstallUriFromIntent() {
        if (getActivity() == null) return;
        
        Intent intent = getActivity().getIntent();
        if (intent == null) return;
        
        String action = intent.getAction();
        Uri dataUri = intent.getData();
        
        // 检查是否是安装相关的意图
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_INSTALL_PACKAGE.equals(action)) {
            if (dataUri != null) {
                log("接收到外部安装请求: " + dataUri.toString());
                
                // 处理URI并设置文件选择
                selectedFileUri = dataUri;
                String fileName = getFileNameFromUri(dataUri);
                selectedFilePath = getFilePathFromUri(dataUri);
                
                if (selectedFilePath != null) {
                    tvSelectedFile.setText(fileName);
                    
                    // 检测文件类型并显示
                    isXapkFile = XapkInstaller.isXapkFile(selectedFilePath);
                    String fileType = XapkInstaller.getFileTypeDescription(selectedFilePath);
                    tvFileType.setText(fileType);
                    tvFileType.setVisibility(View.VISIBLE);
                    
                    // 如果是 XAPK，显示包含的 APK 数量
                    if (isXapkFile) {
                        int apkCount = XapkInstaller.getApkCount(requireContext(), selectedFilePath);
                        log("检测到 " + fileType + "，包含 " + apkCount + " 个 APK 文件");
                    }
                    
                    log("已处理外部安装请求，文件已复制到 cache: " + selectedFilePath);
                    

                    
                    updateInstallButtonState();
                } else {
                    log(getString(R.string.process_external_install_uri_failed, dataUri.toString()));
                    Toast.makeText(requireContext(), R.string.cannot_process_install_file, Toast.LENGTH_SHORT).show();
                }
                
                // 清除意图数据，避免重复处理
                getActivity().setIntent(null);
            }
        }
    }

    /**
     * 处理从HomeActivity传递过来的安装参数
     * 当用户从其他应用选择本应用作为安装器时，HomeActivity会导航到InstallerFragment并传递参数
     */
    private void handleInstallArguments() {
        Bundle arguments = getArguments();
        if (arguments == null) return;
        
        Uri installUri = arguments.getParcelable("install_uri");
        if (installUri != null) {
            log(getString(R.string.received_install_uri_from_home, installUri.toString()));
            
            // 处理URI并设置文件选择
            selectedFileUri = installUri;
            String fileName = getFileNameFromUri(installUri);
            selectedFilePath = getFilePathFromUri(installUri);
            
            if (selectedFilePath != null) {
                tvSelectedFile.setText(fileName);
                
                // 检测文件类型并显示
                isXapkFile = XapkInstaller.isXapkFile(selectedFilePath);
                String fileType = XapkInstaller.getFileTypeDescription(selectedFilePath);
                tvFileType.setText(fileType);
                tvFileType.setVisibility(View.VISIBLE);
                
                // 如果是 XAPK，显示包含的 APK 数量
                if (isXapkFile) {
                    int apkCount = XapkInstaller.getApkCount(requireContext(), selectedFilePath);
                    log(getString(R.string.detected_file_type_apk_count, fileType, apkCount));
                }
                
                log(getString(R.string.processed_home_install_request, selectedFilePath));
                

                
                updateInstallButtonState();
                
                // 清除参数，避免重复处理
                arguments.remove("install_uri");
            } else {
                log(getString(R.string.process_home_install_uri_failed, installUri.toString()));
                Toast.makeText(requireContext(), R.string.cannot_process_install_file, Toast.LENGTH_SHORT).show();
            }
        }
    }
}