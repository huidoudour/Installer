package io.github.huidoudour.Installer;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
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

import io.github.huidoudour.Installer.LanguageManager;
import io.github.huidoudour.Installer.XapkInstaller;
import io.github.huidoudour.Installer.ShizukuInstallHelper;

import io.github.huidoudour.Installer.PrivilegeHelper;
import io.github.huidoudour.Installer.PrivilegeHelper.PrivilegeMode;
import rikka.shizuku.Shizuku;
import io.github.huidoudour.Installer.R;

public class InstallerActivity extends AppCompatActivity {

    private static final String TAG = "InstallerActivity";
    
    private LinearLayout layoutInstallInfo;
    private LinearLayout layoutProgress;
    private LinearLayout layoutCompletion;
    private ImageView ivAppIcon;
    private TextView tvAppName;
    private TextView tvPackageName;
    private TextView tvVersion;
    private TextView tvFileSize;
    private TextView tvMinSdk;
    private TextView tvTargetSdk;
    private Button btnInstall;
    private Button btnCancel;
    private Button btnCancelProgress;
    private Button btnOpenApp;
    private Button btnFinish;
    private com.google.android.material.progressindicator.CircularProgressIndicator progressInstall;
    
    private Uri installUri;
    private String filePath;
    private boolean isXapkFile = false;
    
    // 安装状态管理
    private boolean isInstalling = false;
    private String installedPackageName = null;
    
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
        layoutCompletion = findViewById(R.id.layout_completion);
        ivAppIcon = findViewById(R.id.iv_app_icon);
        tvAppName = findViewById(R.id.tv_app_name);
        tvPackageName = findViewById(R.id.tv_package_name);
        tvVersion = findViewById(R.id.tv_version);
        tvFileSize = findViewById(R.id.tv_file_size);
        tvMinSdk = findViewById(R.id.tv_min_sdk);
        tvTargetSdk = findViewById(R.id.tv_target_sdk);
        btnInstall = findViewById(R.id.btn_install);
        btnCancel = findViewById(R.id.btn_cancel);
        btnCancelProgress = findViewById(R.id.btn_cancel_progress);
        btnOpenApp = findViewById(R.id.btn_open_app);
        btnFinish = findViewById(R.id.btn_finish);
        progressInstall = findViewById(R.id.progress_install);
        
        btnInstall.setOnClickListener(v -> startInstallation());
        btnCancel.setOnClickListener(v -> finish());
        btnCancelProgress.setOnClickListener(v -> onCancelInstallation());
        btnOpenApp.setOnClickListener(v -> onOpenInstalledApp());
        btnFinish.setOnClickListener(v -> finish());
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
                
                // XAPK文件显示未知SDK信息
                tvMinSdk.setText(getString(R.string.unknown));
                tvTargetSdk.setText(getString(R.string.unknown));
                
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
                
                // 获取SDK信息
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    int minSdk = packageInfo.applicationInfo.minSdkVersion;
                    tvMinSdk.setText(String.valueOf(minSdk));
                } else {
                    tvMinSdk.setText(getString(R.string.unknown));
                }
                
                int targetSdk = packageInfo.applicationInfo.targetSdkVersion;
                tvTargetSdk.setText(String.valueOf(targetSdk));
                
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
        tvMinSdk.setText(getString(R.string.unknown));
        tvTargetSdk.setText(getString(R.string.unknown));
        ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
    }
    
    /**
     * 获取已安装应用的信息
     */
    private PackageInfo getInstalledAppInfo(String packageName) {
        try {
            Log.d(TAG, getString(R.string.checking_app_installed, packageName));
            
            // 使用QUERY_ALL_PACKAGES权限直接查询包信息
            PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);
            Log.d(TAG, getString(R.string.app_already_installed, info.versionName, info.getLongVersionCode()));
            return info;
            
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, getString(R.string.app_not_installed, packageName));
            return null;
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.check_app_install_error, e.getMessage()));
            return null;
        }
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
        } else {
            btnInstall.setEnabled(false);
            // 显示Shizuku状态说明
            Toast.makeText(this, R.string.shizuku_required, Toast.LENGTH_LONG).show();
        }
    }
    
    private void startInstallation() {
        if (isInstalling) return;
        
        try {
            isInstalling = true;
            
            // 显示进度界面
            layoutInstallInfo.setVisibility(View.GONE);
            layoutProgress.setVisibility(View.VISIBLE);
            layoutCompletion.setVisibility(View.GONE);
            
            // 循环进度条自动运行动画
            
            // 获取当前授权器 - 现在只支持Shizuku
            PrivilegeMode currentMode = PrivilegeHelper.PrivilegeMode.SHIZUKU;
            
            // 开始安装过程
            if (isXapkFile) {
                // XAPK/APKS 安装
                ShizukuInstallHelper.installXapk(this, filePath, true, true, new ShizukuInstallHelper.InstallCallback() {
                @Override
                public void onProgress(String message) {
                    // 保持静默或可选展示
                }
                @Override
                public void onSuccess(String message) {
                    runOnUiThread(() -> {
                        showCompletionUI();
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        isInstalling = false;
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
                            showCompletionUI();
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            isInstalling = false;
                            Toast.makeText(InstallerActivity.this, getString(R.string.apk_install_failed, error), Toast.LENGTH_LONG).show();
                            layoutProgress.setVisibility(View.GONE);
                            layoutInstallInfo.setVisibility(View.VISIBLE);
                        });
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.start_installation_failed), e);
            isInstalling = false;
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
    
    /**
     * 显示安装完成界面
     */
    private void showCompletionUI() {
        isInstalling = false;
        layoutProgress.setVisibility(View.GONE);
        layoutInstallInfo.setVisibility(View.GONE);
        layoutCompletion.setVisibility(View.VISIBLE);
        
        // 保存安装的包名用于打开应用
        Log.d(TAG, getString(R.string.save_installed_package_name));
        installedPackageName = tvPackageName.getText().toString();
        
        // 检查已安装的应用信息并更新按钮和版本显示
        Log.d(TAG, getString(R.string.checking_installed_app_info));
        checkAndUpdateInstalledAppInfo();
    }
    
    /**
     * 检查并更新已安装应用信息
     */
    private void checkAndUpdateInstalledAppInfo() {
        String packageName = tvPackageName.getText().toString();
        if (packageName == null || packageName.isEmpty()) return;
        
        new Thread(() -> {
            try {
                // 获取已安装应用的信息
                PackageInfo installedInfo = getInstalledAppInfo(packageName);
                
                runOnUiThread(() -> {
                    if (installedInfo != null) {
                        // 应用已安装，更新版本信息和按钮
                        updateVersionInfoForInstalledApp(installedInfo);
                        updateInstallButtonForInstalledApp(installedInfo);
                    } else {
                        // 应用未安装，显示安装信息
                        updateVersionInfoForNewInstall();
                        updateInstallButtonForNewInstall();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, getString(R.string.check_installed_app_info_failed, e.getMessage()));
            }
        }).start();
    }
    
    /**
     * 更新已安装应用的版本信息
     */
    private void updateVersionInfoForInstalledApp(PackageInfo installedInfo) {
        try {
            // 获取待安装APK的信息
            PackageManager pm = getPackageManager();
            PackageInfo apkInfo = pm.getPackageArchiveInfo(filePath, 0);
            
            if (apkInfo != null) {
                String apkVersionName = apkInfo.versionName != null ? apkInfo.versionName : "";
                long apkVersionCode = apkInfo.getLongVersionCode();
                
                String installedVersionName = installedInfo.versionName != null ? installedInfo.versionName : "";
                long installedVersionCode = installedInfo.getLongVersionCode();
                
                // 显示版本对比：旧版本 → 新版本
                String versionDisplay = String.format("%s (%d) → %s (%d)", 
                    installedVersionName, installedVersionCode, 
                    apkVersionName, apkVersionCode);
                tvVersion.setText(versionDisplay);
            }
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.updating_version_info_installed_app) + ": " + e.getMessage());
        }
    }
    
    /**
     * 更新新安装的版本信息
     */
    private void updateVersionInfoForNewInstall() {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo apkInfo = pm.getPackageArchiveInfo(filePath, 0);
            
            if (apkInfo != null) {
                String versionName = apkInfo.versionName != null ? apkInfo.versionName : "";
                long versionCode = apkInfo.getLongVersionCode();
                tvVersion.setText(String.format("%s (%d)", versionName, versionCode));
            }
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.updating_version_info_new_install) + ": " + e.getMessage());
        }
    }
    
    /**
     * 为已安装应用更新安装按钮
     */
    private void updateInstallButtonForInstalledApp(PackageInfo installedInfo) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo apkInfo = pm.getPackageArchiveInfo(filePath, 0);
            
            if (apkInfo != null) {
                long apkVersionCode = apkInfo.getLongVersionCode();
                long installedVersionCode = installedInfo.getLongVersionCode();
                
                if (apkVersionCode == installedVersionCode) {
                    // 版本相同，显示ReInstall
                    btnInstall.setText(R.string.reinstall);
                } else {
                    // 版本不同，显示Install
                    btnInstall.setText(R.string.install);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.updating_install_button_installed_app) + ": " + e.getMessage());
        }
    }
    
    /**
     * 为新安装更新安装按钮
     */
    private void updateInstallButtonForNewInstall() {
        btnInstall.setText(R.string.install);
    }
    
    /**
     * 取消安装
     */
    private void onCancelInstallation() {
        isInstalling = false;
        layoutProgress.setVisibility(View.GONE);
        layoutInstallInfo.setVisibility(View.VISIBLE);
        Toast.makeText(this, R.string.install_cancelled, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 打开已安装的应用
     */
    private void onOpenInstalledApp() {
        if (installedPackageName == null || installedPackageName.isEmpty()) {
            Toast.makeText(this, R.string.cannot_open_app, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 使用Shizuku Shell命令启动应用
        launchAppViaShizukuShell(installedPackageName);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    
    /**
     * 使用Shizuku Shell命令启动应用
     */
    private void launchAppViaShizukuShell(String packageName) {
        try {
            Log.d(TAG, getString(R.string.launching_app_via_shizuku_shell, packageName));
            
            // 异步执行Shell命令
            new Thread(() -> {
                try {
                    // 使用am start命令
                    String command = "am start -n " + packageName + "/.MainActivity";
                    
                    // 执行命令
                    Process process = Runtime.getRuntime().exec(new String[]{
                        "sh", "-c", command
                    });
                    
                    int exitCode = process.waitFor();
                    Log.d(TAG, getString(R.string.shell_command_result, exitCode));
                    
                    runOnUiThread(() -> {
                        if (exitCode == 0) {
                            Toast.makeText(this, getString(R.string.app_launched_success, packageName), 
                                Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            // 回退到传统方式
                            launchAppTraditional(packageName);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, getString(R.string.shizuku_shell_launch_failed, e.getMessage()));
                    runOnUiThread(() -> {
                        // 回退到传统方式
                        launchAppTraditional(packageName);
                    });
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.launch_app_via_shizuku_failed, e.getMessage()), e);
            // 回退到传统启动方式
            launchAppTraditional(packageName);
        }
    }
    
    /**
     * 传统的应用启动方式（回退方案）
     */
    private void launchAppTraditional(String packageName) {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                startActivity(launchIntent);
                finish();
            } else {
                Toast.makeText(this, getString(R.string.app_no_launcher, packageName), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.open_app_failed, e.getMessage()));
            Toast.makeText(this, getString(R.string.open_app_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }
}