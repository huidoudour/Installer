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
import io.github.huidoudour.Installer.LogManager;

import io.github.huidoudour.Installer.XapkInstaller;
import io.github.huidoudour.Installer.ShizukuInstallHelper;

import io.github.huidoudour.Installer.PrivilegeHelper;
import io.github.huidoudour.Installer.PrivilegeHelper.PrivilegeMode;
import io.github.huidoudour.Installer.PrivilegeHelper.PrivilegeStatus;
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
                        // 显示"正在处理..."的临时状态
                        tvSelectedFile.setText(R.string.processing_file);
                        tvFileType.setVisibility(View.GONE);
                        
                        // 在新线程中处理所有文件操作
                        new Thread(() -> {
                            String fileName = getFileNameFromUri(selectedFileUri);
                            selectedFilePath = getFilePathFromUri(selectedFileUri);
                            
                            // 检测文件类型
                            final boolean localIsXapk;
                            final String localFileType;
                            final int localApkCount;
                            
                            if (selectedFilePath != null) {
                                localIsXapk = XapkInstaller.isXapkFile(selectedFilePath);
                                localFileType = XapkInstaller.getFileTypeDescription(selectedFilePath);
                                if (localIsXapk) {
                                    localApkCount = XapkInstaller.getApkCount(requireContext(), selectedFilePath);
                                } else {
                                    localApkCount = 0;
                                }
                            } else {
                                localIsXapk = false;
                                localFileType = "";
                                localApkCount = 0;
                            }
                            
                            // 在主线程中更新 UI
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (selectedFilePath != null) {
                                        tvSelectedFile.setText(fileName != null ? fileName : "Unknown");
                                        
                                        // === 检测文件类型并显示 ===
                                        InstallerFragment.this.isXapkFile = localIsXapk;
                                        tvFileType.setText(localFileType);
                                        tvFileType.setVisibility(View.VISIBLE);
                                        
                                        // 如果是 XAPK，显示包含的 APK 数量
                                        if (localIsXapk && localApkCount > 0) {
                                            log(getString(R.string.detected_file_type_apk_count, localFileType, localApkCount));
                                        }
                                        
                                        log(getString(R.string.file_selected_and_cached, selectedFilePath));
                                        updateInstallButtonState();
                                    } else {
                                        tvSelectedFile.setText(fileName != null ? fileName : selectedFileUri.getPath());
                                        tvFileType.setVisibility(View.GONE);
                                        log(getString(R.string.file_selected_uri_fail, selectedFileUri.toString()));
                                        updateInstallButtonState();
                                    }
                                });
                            }
                        }).start();
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
                    Toast.makeText(requireContext(), getString(R.string.need_file_access_permission_select_apk), Toast.LENGTH_LONG).show();
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
        // 优先尝试调用第三方文件管理器
        if (openThirdPartyFilePicker()) {
            log(getString(R.string.opening_third_party_file_picker));
            return;
        }
            
        // 如果第三方文件管理器不可用，使用系统默认文件选择器
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        // 进一步优化文件选择器配置以支持华为/鸿蒙设备上的XAPK/APKS文件
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "application/vnd.android.package-archive",  // APK
            "application/zip",  // XAPK, APKS (ZIP 格式)
            "application/octet-stream"  // 二进制流（用于兼容华为等设备将 APKS 识别为 bin 文件的情况）
        });
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // 添加额外的标志以提高兼容性
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.select_package_file)));
            log(getString(R.string.open_file_picker));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(requireContext(), getString(R.string.file_picker_not_found), Toast.LENGTH_SHORT).show();
            log(getString(R.string.file_picker_not_found));
        }
    }
        
    /**
     * 尝试调用第三方文件管理器（MT管理器、质感文件等）
     * @return 如果成功启动第三方文件管理器返回 true，否则返回 false
     */
    private boolean openThirdPartyFilePicker() {
        // 按优先级尝试不同的文件管理器
        String[] fileManagers = {
            "bin.mt.plus",      // MT管理器
            "me.zhanghai.android.files"  // 质感文件
        };
            
        for (String packageName : fileManagers) {
            try {
                // 检查文件管理器是否已安装
                PackageManager pm = requireContext().getPackageManager();
                pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
                    
                // 构建调用文件管理器的 Intent
                Intent intent = new Intent("android.intent.action.VIEW");
                intent.setClassName(packageName, packageName + ".activity.FileManagerActivity");
                intent.setType("file/*");
                intent.putExtra("select", "file");
                intent.putExtra("ext", new String[]{".apk", ".apks", ".xapk", ".apkm"});
                intent.putExtra("multi_select", false);
                    
                // 启动文件管理器选择器
                filePickerLauncher.launch(intent);
                log(getString(R.string.opening_file_manager, getPackageManagerName(packageName)));
                return true;
                    
            } catch (Exception e) {
                // 当前文件管理器未安装或启动失败，继续尝试下一个
                log(getString(R.string.file_manager_not_available, packageName, e.getMessage()));
            }
        }
            
        return false;
    }
        
    /**
     * 获取包名的友好名称
     */
    private String getPackageManagerName(String packageName) {
        switch (packageName) {
            case "bin.mt.plus":
                return "MT管理器";
            case "me.zhanghai.android.files":
                return "质感文件";
            default:
                return packageName;
        }
    }

    /**
     * 切换授权器模式
     */
    private void switchPrivilegeMode() {
        PrivilegeMode newMode = PrivilegeHelper.switchMode(requireContext());
        String modeName = PrivilegeHelper.getModeName(newMode);
        log(getString(R.string.switched_to_privilege, modeName));
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
                log(getString(R.string.privilege_not_installed_log, modeName));
                break;
            case NOT_RUNNING:
                PrivilegeHelper.openPrivilegeApp(requireContext(), currentMode);
                log(getString(R.string.opening_privilege_app, modeName));
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
                        log(getString(R.string.shizuku_unavailable_meow, t.getMessage()));
                        updatePrivilegeStatusAndUi();
                    }
                }
                break;
            case AUTHORIZED:
                log(getString(R.string.privilege_already_authorized, modeName));
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
            log(getString(R.string.privilege_not_connected_install_log, modeName));
            Toast.makeText(requireContext(), getString(R.string.privilege_not_connected_install_log, modeName), Toast.LENGTH_LONG).show();
            updatePrivilegeStatusAndUi();
            return;
        }

        btnInstall.setEnabled(false);
        log("");
        log(getString(R.string.start_apk_install));

        // === 根据文件类型和授权器选择安装方式 ===
        if (isXapkFile) {
            // XAPK/APKS 安装
            ShizukuInstallHelper.installXapk(
                requireContext(),
                selectedFilePath,
                switchReplaceExisting.isChecked(),
                switchGrantPermissions.isChecked(),
                createInstallCallback()
            );
        } else {
            // 单个 APK 安装
            ShizukuInstallHelper.installSingleApk(
                requireContext(),
                new File(selectedFilePath),
                switchReplaceExisting.isChecked(),
                switchGrantPermissions.isChecked(),
                createInstallCallback()
            );
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
                        log(getString(R.string.error_prefix, error));
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
        tvPrivilegeTitle.setText(getString(R.string.privilege_status_title, modeName));
        
        // 根据状态更新UI
        String currentStatus = "";
        switch (status) {
            case NOT_INSTALLED:
                currentStatus = "Not installed";
                tvPrivilegeStatus.setText(getString(R.string.privilege_not_installed_status, modeName));
                tvPrivilegeStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                btnRequestPermission.setText(getString(R.string.privilege_download_button, modeName));
                btnRequestPermission.setEnabled(true);
                if (!currentStatus.equals(lastPrivilegeStatus)) {
                    log(getString(R.string.privilege_not_installed_log, modeName));
                }
                break;
                
            case NOT_RUNNING:
                currentStatus = "Not running";
                tvPrivilegeStatus.setText(getString(R.string.privilege_not_running_status, modeName));
                tvPrivilegeStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                btnRequestPermission.setText(getString(R.string.privilege_open_button, modeName));
                btnRequestPermission.setEnabled(true);
                if (!currentStatus.equals(lastPrivilegeStatus)) {
                    log(getString(R.string.opening_privilege_app, modeName));
                }
                break;
                
            case VERSION_TOO_LOW:
                currentStatus = getString(R.string.version_too_low);
                tvPrivilegeStatus.setText(getString(R.string.privilege_version_low_status));
                tvPrivilegeStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                btnRequestPermission.setText(getString(R.string.privilege_update_button, modeName));
                btnRequestPermission.setEnabled(true);
                if (!currentStatus.equals(lastPrivilegeStatus)) {
                    log(getString(R.string.privilege_version_low_status) + ": " + modeName);
                }
                break;
                
            case NOT_AUTHORIZED:
                currentStatus = getString(R.string.not_authorized);
                tvPrivilegeStatus.setText(getString(R.string.privilege_not_authorized_status));
                tvPrivilegeStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                btnRequestPermission.setText(getString(R.string.privilege_request_auth_button));
                btnRequestPermission.setEnabled(true);
                if (!currentStatus.equals(lastPrivilegeStatus)) {
                    log(getString(R.string.privilege_not_authorized_status) + ": " + modeName);
                }
                break;
                
            case AUTHORIZED:
                currentStatus = getString(R.string.authorized);
                tvPrivilegeStatus.setText(getString(R.string.privilege_authorized_status));
                tvPrivilegeStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                statusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                btnRequestPermission.setText(getString(R.string.privilege_authorized_button));
                btnRequestPermission.setEnabled(false);
                if (!currentStatus.equals(lastPrivilegeStatus)) {
                    log(getString(R.string.privilege_authorized_status) + ": " + modeName);
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
                log(getString(R.string.log_external_install_request, dataUri.toString()));
                
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
                        log(getString(R.string.log_detected_file, fileType, apkCount));
                    }
                    
                    log(getString(R.string.log_processed_external_request, selectedFilePath));
                    

                    
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