package io.github.huidoudour.Installer;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import io.github.huidoudour.Installer.utils.ApkAnalyzer;
import io.github.huidoudour.Installer.utils.LanguageManager;
import io.github.huidoudour.Installer.utils.XapkInstaller;
import io.github.huidoudour.Installer.utils.ShizukuInstallHelper;
import rikka.shizuku.Shizuku;

public class InstallActivity extends AppCompatActivity {

    private static final String TAG = "InstallActivity";
    
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
        setContentView(R.layout.activity_install);
        
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
            showErrorAndExit("无效的安装请求");
        }
    }
    
    private void processInstallFile() {
        try {
            // 获取文件路径
            filePath = getFilePathFromUri(installUri);
            if (filePath == null) {
                showErrorAndExit("无法访问安装文件");
                return;
            }
            
            // 检测文件类型
            isXapkFile = XapkInstaller.isXapkFile(filePath);
            
            // 显示安装信息
            displayInstallInfo();
            
        } catch (Exception e) {
            Log.e(TAG, "处理安装文件失败", e);
            showErrorAndExit("处理安装文件时发生错误");
        }
    }
    
    private void displayInstallInfo() {
        try {
            // 文件基本信息
            String fileSize = ApkAnalyzer.getFileSize(filePath);
            tvFileSize.setText(fileSize != null ? fileSize : "未知");
            
            if (!isXapkFile) {
                // 单个APK文件信息
                String packageName = ApkAnalyzer.getPackageName(this, filePath);
                String versionInfo = ApkAnalyzer.getVersionInfo(this, filePath);
                
                tvPackageName.setText(packageName != null ? packageName : "未知");
                tvVersion.setText(versionInfo != null ? versionInfo : "未知");
                
                // 尝试获取应用名称（使用包名作为应用名称）
                tvAppName.setText(packageName != null ? packageName : "未知应用");
                
                // 设置应用图标
                setAppIcon();
            } else {
                // XAPK文件信息
                tvAppName.setText("XAPK安装包");
                tvPackageName.setText("XAPK");
                int apkCount = XapkInstaller.getApkCount(filePath);
                tvVersion.setText(apkCount > 0 ? apkCount + " 个APK文件" : "未知");
                
                // 设置默认图标
                ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            
            // 显示安装信息界面
            layoutInstallInfo.setVisibility(View.VISIBLE);
            layoutProgress.setVisibility(View.GONE);
            
            // 检查Shizuku状态
            checkShizukuStatus();
            
        } catch (Exception e) {
            Log.e(TAG, "显示安装信息失败", e);
            showErrorAndExit("无法解析安装文件信息");
        }
    }
    
    private void setAppIcon() {
        if (!isXapkFile) {
            try {
                // 尝试从APK文件中提取应用图标
                android.graphics.drawable.Drawable icon = ApkAnalyzer.getAppIcon(this, filePath);
                if (icon != null) {
                    ivAppIcon.setImageDrawable(icon);
                } else {
                    // 使用默认图标
                    ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            } catch (Exception e) {
                Log.e(TAG, "设置应用图标失败", e);
                ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
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
            Toast.makeText(this, "需要Shizuku授权才能安装应用", Toast.LENGTH_LONG).show();
        }
    }
    
    private void startInstallation() {
        try {
            // 显示进度界面
            layoutInstallInfo.setVisibility(View.GONE);
            layoutProgress.setVisibility(View.VISIBLE);
            
            // 开始安装过程
            if (isXapkFile) {
                // XAPK安装
                XapkInstaller.installXapk(this, filePath, new XapkInstaller.InstallCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(InstallActivity.this, "XAPK安装成功", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(InstallActivity.this, "XAPK安装失败: " + error, Toast.LENGTH_LONG).show();
                            layoutProgress.setVisibility(View.GONE);
                            layoutInstallInfo.setVisibility(View.VISIBLE);
                        });
                    }
                });
            } else {
                // APK安装
                ShizukuInstallHelper.installApk(this, filePath, new ShizukuInstallHelper.InstallCallback() {
                    @Override
                    public void onProgress(String message) {
                        // 在安装过程中不显示进度消息
                    }
                    
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            Toast.makeText(InstallActivity.this, "APK安装成功", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(InstallActivity.this, "APK安装失败: " + error, Toast.LENGTH_LONG).show();
                            layoutProgress.setVisibility(View.GONE);
                            layoutInstallInfo.setVisibility(View.VISIBLE);
                        });
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "开始安装失败", e);
            Toast.makeText(this, "安装过程启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
                ContentResolver contentResolver = getContentResolver();
                InputStream inputStream = contentResolver.openInputStream(uri);
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
            Log.e(TAG, "从URI获取文件路径失败", e);
        }
        return null;
    }
    
    private void showErrorAndExit(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("安装错误")
                .setMessage(message)
                .setPositiveButton("确定", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }
}