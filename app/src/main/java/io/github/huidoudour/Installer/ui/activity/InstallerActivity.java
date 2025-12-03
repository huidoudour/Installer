package io.github.huidoudour.Installer.ui.activity;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import io.github.huidoudour.Installer.utils.LanguageManager;
import io.github.huidoudour.Installer.utils.XapkInstaller;
import io.github.huidoudour.Installer.utils.ShizukuInstallHelper;
import rikka.shizuku.Shizuku;
import io.github.huidoudour.Installer.R;

public class InstallerActivity extends AppCompatActivity {

    private static final String TAG = "InstallerActivity";
    
    private LinearLayout layoutInstallInfo;
    private LinearLayout layoutProgress;
    private ImageView ivAppIcon;
    private TextView tvAppName;
    private TextView tvPackageName;
    private TextView tvVersion;
    private TextView tvFileSize;
    private Button btnInstall;
    private Button btnCancel;
    private Button btnCancelProgress;
    private Button btnInstallDisabled;
    
    private Uri installUri;
    private String filePath;
    private boolean isXapkFile = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用用户选择的语言
        LanguageManager.applyUserLanguagePreference(this);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_installer);
        
        // 初始化视图
        initViews();
        
        // 处理安装意图
        handleInstallIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleInstallIntent(intent);
    }
    
    private void initViews() {
        layoutInstallInfo = findViewById(R.id.layout_install_info);
        layoutProgress = findViewById(R.id.layout_progress);
        ivAppIcon = findViewById(R.id.iv_app_icon);
        tvAppName = findViewById(R.id.tv_app_name);
        tvPackageName = findViewById(R.id.tv_package_name);
        tvVersion = findViewById(R.id.tv_version);
        tvFileSize = findViewById(R.id.tv_file_size);
        btnInstall = findViewById(R.id.btn_install);
        btnCancel = findViewById(R.id.btn_cancel);
        btnCancelProgress = findViewById(R.id.btn_cancel_progress);
        btnInstallDisabled = findViewById(R.id.btn_install_disabled);
        
        btnInstall.setOnClickListener(v -> startInstallation());
        btnCancel.setOnClickListener(v -> finish());
        btnCancelProgress.setOnClickListener(v -> finish());
    }
    
    private void handleInstallIntent(Intent intent) {
        if (intent == null) return;
        
        String action = intent.getAction();
        Uri data = intent.getData();
        
        if ((Intent.ACTION_VIEW.equals(action) || Intent.ACTION_INSTALL_PACKAGE.equals(action)) && data != null) {
            installUri = data;
            processInstallFile();
        } else {
            // 如果没有有效的安装意图，显示错误并退出
            showErrorAndExit(getString(R.string.invalid_install_request));
        }
    }
    
    private void processInstallFile() {
        try {
            // 获取文件路径
            filePath = getFilePathFromUri(installUri);
            if (filePath == null) {
                showErrorAndExit(getString(R.string.cannot_access_install_file));
                return;
            }
            
            // 检测文件类型
            isXapkFile = XapkInstaller.isXapkFile(filePath);
            
            // 显示安装信息
            displayInstallInfo();
            
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.process_install_file_failed), e);
            showErrorAndExit(getString(R.string.error_processing_install_file));
        }
    }
    
    private void displayInstallInfo() {
        try {
            if (!isXapkFile) {
                // 单个APK：解析APK信息
                parseApkInfo(filePath);
            } else {
                // XAPK文件：保留数量统计与默认图标
                tvAppName.setText(R.string.xapk_package);
                tvPackageName.setText(R.string.xapk);
                int apkCount = XapkInstaller.getApkCount(this, filePath);
                tvVersion.setText(apkCount > 0 ? getString(R.string.apk_files_count, apkCount) : getString(R.string.unknown));
                
                // 获取文件大小
                File file = new File(filePath);
                if (file.exists()) {
                    long fileSizeInBytes = file.length();
                    String fileSize = formatFileSize(fileSizeInBytes);
                    tvFileSize.setText(fileSize);
                } else {
                    tvFileSize.setText(getString(R.string.unknown));
                }
                
                ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }

            // 显示安装信息界面
            layoutInstallInfo.setVisibility(View.VISIBLE);
            layoutProgress.setVisibility(View.GONE);

            // 检查Shizuku状态
            checkShizukuStatus();
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.display_install_info_failed), e);
            showErrorAndExit(getString(R.string.parse_file_failed));
        }
    }
    
    /**
     * 解析APK信息
     */
    private void parseApkInfo(String apkPath) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkPath, 
                PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS);
            
            if (packageInfo != null) {
                // 设置应用源路径，以便能够加载图标和标签
                ApplicationInfo appInfo = packageInfo.applicationInfo;
                appInfo.sourceDir = apkPath;
                appInfo.publicSourceDir = apkPath;
                
                // 获取应用名称
                CharSequence appName = appInfo.loadLabel(pm);
                tvAppName.setText(appName != null ? appName.toString() : getString(R.string.unknown_app));
                
                // 获取包名
                tvPackageName.setText(packageInfo.packageName);
                
                // 获取版本信息
                String versionName = packageInfo.versionName != null ? packageInfo.versionName : "";
                long versionCode = packageInfo.getLongVersionCode();
                tvVersion.setText(String.format("%s (%d)", versionName, versionCode));
                
                // 获取文件大小
                File file = new File(apkPath);
                if (file.exists()) {
                    long fileSizeInBytes = file.length();
                    String fileSize = formatFileSize(fileSizeInBytes);
                    tvFileSize.setText(fileSize);
                } else {
                    tvFileSize.setText(getString(R.string.unknown));
                }
                
                // 获取应用图标
                try {
                    Drawable icon = appInfo.loadIcon(pm);
                    if (icon != null) {
                        ivAppIcon.setImageDrawable(icon);
                    } else {
                        ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "加载应用图标失败", e);
                    ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            } else {
                // 解析失败，显示默认信息
                setDefaultInfo();
            }
        } catch (Exception e) {
            Log.e(TAG, "解析APK信息失败", e);
            setDefaultInfo();
        }
    }
    
    /**
     * 设置默认信息
     */
    private void setDefaultInfo() {
        tvAppName.setText(getString(R.string.unknown_app));
        tvPackageName.setText(getString(R.string.unknown));
        tvVersion.setText(getString(R.string.unknown));
        tvFileSize.setText(getString(R.string.unknown));
        ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
    }
    
    /**
     * 格式化文件大小
     */
    private String formatFileSize(long sizeInBytes) {
        if (sizeInBytes < 1024) {
            return sizeInBytes + " B";
        } else if (sizeInBytes < 1024 * 1024) {
            return String.format("%.2f KB", sizeInBytes / 1024.0);
        } else if (sizeInBytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", sizeInBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", sizeInBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    private void checkShizukuStatus() {
        boolean shizukuReady = false;
        try {
            shizukuReady = Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    !(Shizuku.isPreV11() || Shizuku.getVersion() < 10);
        } catch (Throwable t) {
            shizukuReady = false;
        }
        
        if (shizukuReady) {
            btnInstall.setEnabled(true);
            btnInstallDisabled.setVisibility(View.GONE);
        } else {
            btnInstall.setEnabled(false);
            btnInstallDisabled.setVisibility(View.VISIBLE);
            
            // 显示Shizuku状态说明
            Toast.makeText(this, R.string.shizuku_required, Toast.LENGTH_LONG).show();
        }
    }
    
    private void startInstallation() {
        try {
            // 显示进度界面
            layoutInstallInfo.setVisibility(View.GONE);
            layoutProgress.setVisibility(View.VISIBLE);
            
            // 开始安装过程
            if (isXapkFile) {
                // XAPK/APKS 安装（单会话写入所有 APK）
                ShizukuInstallHelper.installXapk(this, filePath, true, true, new ShizukuInstallHelper.InstallCallback() {
                    @Override
                    public void onProgress(String message) {
                        // 保持静默或可选展示
                    }
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            Toast.makeText(InstallerActivity.this, R.string.xapk_install_success, Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(InstallerActivity.this, getString(R.string.xapk_install_failed, error), Toast.LENGTH_LONG).show();
                            layoutProgress.setVisibility(View.GONE);
                            layoutInstallInfo.setVisibility(View.VISIBLE);
                        });
                    }
                });
            } else {
                // APK安装
                ShizukuInstallHelper.installApk(this, filePath, true, true, new ShizukuInstallHelper.InstallCallback() {
                    @Override
                    public void onProgress(String message) {
                        // 在安装过程中不显示进度消息
                    }
                    
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            Toast.makeText(InstallerActivity.this, R.string.apk_install_success, Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(InstallerActivity.this, getString(R.string.apk_install_failed, error), Toast.LENGTH_LONG).show();
                            layoutProgress.setVisibility(View.GONE);
                            layoutInstallInfo.setVisibility(View.VISIBLE);
                        });
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.start_installation_failed), e);
            Toast.makeText(this, getString(R.string.install_process_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            layoutProgress.setVisibility(View.GONE);
            layoutInstallInfo.setVisibility(View.VISIBLE);
        }
    }
    
    private String getFilePathFromUri(Uri uri) {
        try {
            if ("file".equals(uri.getScheme())) {
                return uri.getPath();
            } else if ("content".equals(uri.getScheme())) {
                // 复制内容到临时文件
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) return null;
                
                // 创建临时文件
                File tempFile = new File(getCacheDir(), "temp_install.apk");
                FileOutputStream outputStream = new FileOutputStream(tempFile);
                
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                
                inputStream.close();
                outputStream.close();
                
                return tempFile.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.get_file_path_from_uri_failed), e);
        }
        return null;
    }
    
    private void showErrorAndExit(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.install_error)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }
}
