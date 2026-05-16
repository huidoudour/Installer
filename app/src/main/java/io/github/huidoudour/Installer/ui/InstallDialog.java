package io.github.huidoudour.Installer.ui;

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
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.util.DhizukuInstallHelper;
import io.github.huidoudour.Installer.util.LanguageManager;
import io.github.huidoudour.Installer.util.LogManager;
import io.github.huidoudour.Installer.util.PackageInfoHelper;
import io.github.huidoudour.Installer.util.PrivilegeHelper;
import io.github.huidoudour.Installer.util.ShizukuInstallHelper;
import io.github.huidoudour.Installer.util.XapkInstaller;
import rikka.shizuku.Shizuku;

public class InstallDialog extends AppCompatActivity {

    private static final String TAG = "InstallDialog";

    // 安装对话框视图
    private LinearLayout layoutInstallInfo;
    private LinearLayout layoutCompletion;
    private LinearLayout layoutButtons;
    private LinearLayout layoutInstallingProgress;
    private ImageView ivAppIcon;
    private TextView tvAppName;
    private TextView tvPackageName;
    private TextView tvVersion;
    private TextView tvMinSdk;
    private TextView tvTargetSdk;
    private TextView tvUpgradeVersion;  // 升级版本显示
    private Button btnInstall;
    private Button btnCancel;
    private Button btnCancelProgress;
    private Button btnOpenApp;
    private Button btnFinish;
    private Button btnPrivilege;
    private Button btnCompletionBack;
    private com.google.android.material.progressindicator.LinearProgressIndicator progressInstall;

    // 权限对话框
    private AlertDialog privilegeDialog;
    private RadioButton rbShizuku;
    private RadioButton rbDhizuku;
    private TextView tvShizukuStatus;
    private TextView tvDhizukuStatus;
    private Button btnShizukuGrant;
    private Button btnDhizukuGrant;
    private com.google.android.material.card.MaterialCardView cardShizuku;
    private com.google.android.material.card.MaterialCardView cardDhizuku;
    private ImageView ivShizukuIcon;
    private ImageView ivDhizukuIcon;
    
    // 图标缓存
    private android.util.LruCache<String, android.graphics.drawable.Drawable> iconCache;

    private Uri installUri;
    private String filePath;
    private boolean isXapkFile = false;

    // 安装状态管理
    private boolean isInstalling = false;
    private String installedPackageName = null;

    // 当前选择的授权方式
    private PrivilegeHelper.PrivilegeMode currentMode = PrivilegeHelper.PrivilegeMode.SHIZUKU;

    private LogManager logManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用用户选择的主题
        io.github.huidoudour.Installer.util.ThemeManager.applyUserThemePreference(this);
        
        // 应用用户选择的语言
        LanguageManager.applyUserLanguagePreference(this);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.dialog_install);
        
        // 初始化图标缓存 (最多缓存10个图标)
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        iconCache = new android.util.LruCache<>(cacheSize);

        // 初始化日志管理器
        logManager = LogManager.getInstance();
        logManager.setContext(this);

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
        layoutCompletion = findViewById(R.id.layout_completion);
        layoutButtons = findViewById(R.id.layout_buttons);
        layoutInstallingProgress = findViewById(R.id.layout_installing_progress);
        ivAppIcon = findViewById(R.id.iv_app_icon);
        tvAppName = findViewById(R.id.tv_app_name);
        tvPackageName = findViewById(R.id.tv_package_name);
        tvVersion = findViewById(R.id.tv_version);
        tvMinSdk = findViewById(R.id.tv_min_sdk);
        tvTargetSdk = findViewById(R.id.tv_target_sdk);
        tvUpgradeVersion = findViewById(R.id.tv_upgrade_version);
        btnInstall = findViewById(R.id.btn_install);
        btnCancel = findViewById(R.id.btn_cancel);
        btnCancelProgress = findViewById(R.id.btn_cancel_progress);
        btnOpenApp = findViewById(R.id.btn_open_app);
        btnFinish = findViewById(R.id.btn_finish);
        btnPrivilege = findViewById(R.id.btn_privilege);
        btnCompletionBack = findViewById(R.id.btn_completion_back);
        progressInstall = findViewById(R.id.progress_install);

        // 从保存的设置中读取当前授权方式
        currentMode = PrivilegeHelper.getCurrentMode(this);

        // 设置按钮点击事件
        btnInstall.setOnClickListener(v -> startInstallation());
        btnCancel.setOnClickListener(v -> finish());
        btnCancelProgress.setOnClickListener(v -> onCancelInstallation());
        btnOpenApp.setOnClickListener(v -> onOpenInstalledApp());
        btnFinish.setOnClickListener(v -> finish());
        btnPrivilege.setOnClickListener(v -> showPrivilegeDialog());
        btnCompletionBack.setOnClickListener(v -> backToInstallInfoFromCompletion());
    }

    /**
     * 显示权限选择对话框
     */
    private void showPrivilegeDialog() {
        // 创建权限对话框视图
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_privilege, null);

        // 初始化权限对话框视图
        rbShizuku = dialogView.findViewById(R.id.rb_shizuku);
        rbDhizuku = dialogView.findViewById(R.id.rb_dhizuku);
        tvShizukuStatus = dialogView.findViewById(R.id.tv_shizuku_status);
        tvDhizukuStatus = dialogView.findViewById(R.id.tv_dhizuku_status);
        btnShizukuGrant = dialogView.findViewById(R.id.btn_shizuku_grant);
        btnDhizukuGrant = dialogView.findViewById(R.id.btn_dhizuku_grant);
        cardShizuku = dialogView.findViewById(R.id.card_shizuku);
        cardDhizuku = dialogView.findViewById(R.id.card_dhizuku);
        ivShizukuIcon = dialogView.findViewById(R.id.iv_shizuku_icon);
        ivDhizukuIcon = dialogView.findViewById(R.id.iv_dhizuku_icon);
        Button btnPrivilegeCancel = dialogView.findViewById(R.id.btn_privilege_cancel);
        Button btnPrivilegeNext = dialogView.findViewById(R.id.btn_privilege_next);

        // 加载 Shizuku 和 Dhizuku 的应用图标（暂时注释，使用静态SVG图标）
        loadAppIcon("moe.shizuku.privileged.api", ivShizukuIcon);
        loadAppIcon("com.rosan.dhizuku", ivDhizukuIcon);

        // 更新授权方式 UI
        updatePrivilegeModeUI();

        // 设置权限选择事件（点击整个卡片）
        cardShizuku.setOnClickListener(v -> selectPrivilegeMode(PrivilegeHelper.PrivilegeMode.SHIZUKU));
        cardDhizuku.setOnClickListener(v -> selectPrivilegeMode(PrivilegeHelper.PrivilegeMode.DHIZUKU));

        // 设置按钮点击事件
        btnPrivilegeCancel.setOnClickListener(v -> privilegeDialog.dismiss());
        btnPrivilegeNext.setOnClickListener(v -> {
            privilegeDialog.dismiss();
            // 返回安装对话框后更新安装按钮状态
            updateInstallButtonState();
        });

        // 创建并显示对话框
        privilegeDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        
        privilegeDialog.setCanceledOnTouchOutside(true); // 点击外部区域关闭
        privilegeDialog.show();

        // 检查权限状态
        checkPrivilegeStatus();
    }

    /**
     * 选择授权方式
     */
    private void selectPrivilegeMode(PrivilegeHelper.PrivilegeMode mode) {
        currentMode = mode;
        PrivilegeHelper.saveCurrentMode(this, mode);
        updatePrivilegeModeUI();

        // 显示切换提示
        if (mode == PrivilegeHelper.PrivilegeMode.SHIZUKU) {
            Toast.makeText(this, R.string.switched_to_shizuku, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.switched_to_dhizuku, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 更新授权方式UI
     */
    private void updatePrivilegeModeUI() {
        if (currentMode == PrivilegeHelper.PrivilegeMode.SHIZUKU) {
            if (rbShizuku != null) rbShizuku.setChecked(true);
            if (rbDhizuku != null) rbDhizuku.setChecked(false);
        } else {
            if (rbShizuku != null) rbShizuku.setChecked(false);
            if (rbDhizuku != null) rbDhizuku.setChecked(true);
        }
    }

    /**
     * 从缓存或PackageManager加载应用图标
     * @param packageName 包名
     * @param imageView 显示图标的ImageView
     */
    private void loadAppIcon(String packageName, ImageView imageView) {
        if (imageView == null) return;
        
        // 先从缓存中查找
        android.graphics.drawable.Drawable cachedIcon = iconCache.get(packageName);
        if (cachedIcon != null) {
            imageView.setImageDrawable(cachedIcon);
            Log.d(TAG, "Using cached icon for: " + packageName);
            return;
        }
        
        // 缓存中没有，从PackageManager加载
        try {
            PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            android.graphics.drawable.Drawable icon = appInfo.loadIcon(pm);
            
            if (icon != null) {
                // 存入缓存
                iconCache.put(packageName, icon);
                imageView.setImageDrawable(icon);
                Log.d(TAG, "Loaded and cached icon for: " + packageName);
            } else {
                // 如果获取失败，使用默认图标
                setDefaultIcon(imageView, packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load icon for " + packageName + ": " + e.getMessage());
            setDefaultIcon(imageView, packageName);
        }
    }
    
    /**
     * 设置默认图标
     */
    private void setDefaultIcon(ImageView imageView, String packageName) {
        if ("moe.shizuku.privileged.api".equals(packageName)) {
            imageView.setImageResource(R.drawable.ic_shizuku);
        } else if ("com.rosan.dhizuku".equals(packageName)) {
            imageView.setImageResource(R.drawable.ic_warning);
        } else {
            imageView.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }
    
    /**
     * 请求 Shizuku 权限
     */
    private void requestShizukuPermission() {
        Toast.makeText(this, R.string.requesting_shizuku_auth, Toast.LENGTH_SHORT).show();
        PrivilegeHelper.requestShizukuPermission(1001);
    }

    /**
     * 请求 Dhizuku 权限
     */
    private void requestDhizukuPermission() {
        Toast.makeText(this, R.string.request_dhizuku_permission, Toast.LENGTH_SHORT).show();
        PrivilegeHelper.requestDhizukuPermission(this);
    }

    private void handleInstallIntent(Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        Uri data = intent.getData();

        // 处理 VIEW 或 INSTALL_PACKAGE 动作
        if ((Intent.ACTION_VIEW.equals(action) || Intent.ACTION_INSTALL_PACKAGE.equals(action)) && data != null) {
            installUri = data;
            processInstallFile();
        }
        // 处理 SEND 动作（从分享功能接收文件）
        else if (Intent.ACTION_SEND.equals(action)) {
            handleSendIntent(intent);
        } else {
            // 如果没有有效的安装意图，显示错误并退出
            showErrorAndExit(getString(R.string.invalid_install_request));
        }
    }

    /**
     * 处理分享意图
     */
    private void handleSendIntent(Intent intent) {
        try {
            // 获取分享的 URI
            Uri sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

            if (sharedUri != null) {
                Log.d(TAG, "Received shared file: " + sharedUri.toString());
                installUri = sharedUri;
                processInstallFile();
            } else {
                showErrorAndExit(getString(R.string.invalid_install_request));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle send intent", e);
            showErrorAndExit(getString(R.string.error_processing_install_file));
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
                tvPackageName.setText(getString(R.string.package_name_label) + getString(R.string.xapk));
                int apkCount = XapkInstaller.getApkCount(this, filePath);
                tvVersion.setText(getString(R.string.current_version) + (apkCount > 0 ? getString(R.string.apk_files_count, apkCount) : getString(R.string.unknown)));

                // XAPK文件显示未知SDK信息
                tvMinSdk.setText(getString(R.string.min_sdk) + getString(R.string.unknown));
                tvTargetSdk.setText(getString(R.string.target_sdk) + getString(R.string.unknown));

                ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }

            // 显示安装信息界面
            layoutInstallInfo.setVisibility(View.VISIBLE);
            layoutCompletion.setVisibility(View.GONE);
            layoutButtons.setVisibility(View.VISIBLE);
            layoutInstallingProgress.setVisibility(View.GONE);

            // 检查并显示版本比对信息
            checkAndDisplayVersionComparison();

            // 检查 Shizuku 和 Dhizuku 状态
            checkInstallButtonState();

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
                tvPackageName.setText(getString(R.string.package_name_label) + packageInfo.packageName);

                // 获取版本信息
                String versionName = packageInfo.versionName != null ? packageInfo.versionName : "";
                long versionCode = packageInfo.getLongVersionCode();
                tvVersion.setText(getString(R.string.current_version) + String.format("%s (%d)", versionName, versionCode));

                // 获取SDK信息
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    int minSdk = packageInfo.applicationInfo.minSdkVersion;
                    tvMinSdk.setText(getString(R.string.min_sdk) + minSdk);
                } else {
                    tvMinSdk.setText(getString(R.string.min_sdk) + getString(R.string.unknown));
                }

                int targetSdk = packageInfo.applicationInfo.targetSdkVersion;
                tvTargetSdk.setText(getString(R.string.target_sdk) + targetSdk);

                // 获取应用图标
                try {
                    Drawable icon = appInfo.loadIcon(pm);
                    if (icon != null) {
                        ivAppIcon.setImageDrawable(icon);
                    } else {
                        ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load app icon", e);
                    ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            } else {
                // 解析失败，显示默认信息
                setDefaultInfo();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse APK info", e);
            setDefaultInfo();
        }
    }

    /**
     * 设置默认信息
     */
    private void setDefaultInfo() {
        tvAppName.setText(getString(R.string.unknown_app));
        tvPackageName.setText(getString(R.string.package_name_label) + getString(R.string.unknown));
        tvVersion.setText(getString(R.string.current_version) + getString(R.string.unknown));
        tvMinSdk.setText(getString(R.string.min_sdk) + getString(R.string.unknown));
        tvTargetSdk.setText(getString(R.string.target_sdk) + getString(R.string.unknown));
        ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
    }

    /**
     * 获取已安装应用的信息
     */
    private PackageInfo getInstalledAppInfo(String packageName) {
        try {
            Log.d(TAG, getString(R.string.checking_app_installed, packageName));

            // 使用增强版包信息检查工具
            PackageInfo info = PackageInfoHelper.getInstalledAppInfo(this, packageName);
            if (info != null) {
                Log.d(TAG, getString(R.string.app_already_installed, info.versionName, info.getLongVersionCode()));
            } else {
                Log.d(TAG, getString(R.string.app_not_installed, packageName));
            }
            return info;

        } catch (Exception e) {
            Log.e(TAG, getString(R.string.check_app_install_error, e.getMessage()));
            return null;
        }
    }

    /**
     * 检查权限状态（用于权限对话框）
     */
    private void checkPrivilegeStatus() {
        // 检查 Shizuku 状态
        new Thread(() -> {
            PrivilegeHelper.PrivilegeStatus shizukuStatus = PrivilegeHelper.checkShizukuStatus();
            PrivilegeHelper.PrivilegeStatus dhizukuStatus = PrivilegeHelper.checkDhizukuStatus(this);

            runOnUiThread(() -> {
                updateShizukuStatusUI(shizukuStatus);
                updateDhizukuStatusUI(dhizukuStatus);
            });
        }).start();
    }

    /**
     * 检查安装按钮状态（用于安装对话框）
     */
    private void checkInstallButtonState() {
        updateInstallButtonState();
    }

    /**
     * 更新 Shizuku 状态UI
     */
    private void updateShizukuStatusUI(PrivilegeHelper.PrivilegeStatus status) {
        if (tvShizukuStatus == null) return;

        switch (status) {
            case NOT_INSTALLED:
                tvShizukuStatus.setText(R.string.shizuku_not_running);
                tvShizukuStatus.setTextColor(getColor(R.color.status_error));
                btnShizukuGrant.setVisibility(View.GONE);
                break;
            case NOT_RUNNING:
                tvShizukuStatus.setText(R.string.shizuku_not_running);
                tvShizukuStatus.setTextColor(getColor(R.color.status_error));
                btnShizukuGrant.setVisibility(View.GONE);
                break;
            case NOT_AUTHORIZED:
                tvShizukuStatus.setText(R.string.shizuku_connected_but_not_authorized);
                tvShizukuStatus.setTextColor(getColor(R.color.status_warning));
                btnShizukuGrant.setVisibility(View.VISIBLE);
                btnShizukuGrant.setOnClickListener(v -> requestShizukuPermission());
                break;
            case AUTHORIZED:
                tvShizukuStatus.setText(R.string.shizuku_connected_and_authorized);
                tvShizukuStatus.setTextColor(getColor(R.color.status_success));
                btnShizukuGrant.setVisibility(View.GONE);
                break;
            case VERSION_TOO_LOW:
                tvShizukuStatus.setText(R.string.shizuku_version_too_low);
                tvShizukuStatus.setTextColor(getColor(R.color.status_error));
                btnShizukuGrant.setVisibility(View.GONE);
                break;
            default:
                tvShizukuStatus.setText(R.string.status_unknown);
                tvShizukuStatus.setTextColor(getColor(R.color.status_warning));
                btnShizukuGrant.setVisibility(View.GONE);
                break;
        }
    }

    /**
     * 更新 Dhizuku 状态UI
     */
    private void updateDhizukuStatusUI(PrivilegeHelper.PrivilegeStatus status) {
        if (tvDhizukuStatus == null) return;

        switch (status) {
            case NOT_INSTALLED:
                tvDhizukuStatus.setText(R.string.dhizuku_not_running);
                tvDhizukuStatus.setTextColor(getColor(R.color.status_error));
                btnDhizukuGrant.setVisibility(View.GONE);
                break;
            case NOT_RUNNING:
                tvDhizukuStatus.setText(R.string.dhizuku_not_running);
                tvDhizukuStatus.setTextColor(getColor(R.color.status_error));
                btnDhizukuGrant.setVisibility(View.GONE);
                break;
            case NOT_AUTHORIZED:
                tvDhizukuStatus.setText(R.string.dhizuku_connected_but_not_authorized);
                tvDhizukuStatus.setTextColor(getColor(R.color.status_warning));
                btnDhizukuGrant.setVisibility(View.VISIBLE);
                btnDhizukuGrant.setOnClickListener(v -> requestDhizukuPermission());
                break;
            case AUTHORIZED:
                tvDhizukuStatus.setText(R.string.dhizuku_connected_and_authorized);
                tvDhizukuStatus.setTextColor(getColor(R.color.status_success));
                btnDhizukuGrant.setVisibility(View.GONE);
                break;
            case VERSION_TOO_LOW:
                tvDhizukuStatus.setText(R.string.dhizuku_version_too_low);
                tvDhizukuStatus.setTextColor(getColor(R.color.status_error));
                btnDhizukuGrant.setVisibility(View.GONE);
                break;
            default:
                tvDhizukuStatus.setText(R.string.status_unknown);
                tvDhizukuStatus.setTextColor(getColor(R.color.status_warning));
                btnDhizukuGrant.setVisibility(View.GONE);
                break;
        }
    }

    /**
     * 更新安装按钮状态
     */
    private void updateInstallButtonState() {
        boolean canInstall = false;

        if (currentMode == PrivilegeHelper.PrivilegeMode.SHIZUKU) {
            PrivilegeHelper.PrivilegeStatus status = PrivilegeHelper.checkShizukuStatus();
            canInstall = (status == PrivilegeHelper.PrivilegeStatus.AUTHORIZED);
        } else {
            PrivilegeHelper.PrivilegeStatus status = PrivilegeHelper.checkDhizukuStatus(this);
            canInstall = (status == PrivilegeHelper.PrivilegeStatus.AUTHORIZED);
        }

        btnInstall.setEnabled(canInstall);
    }

    private void log(String message) {
        if (logManager != null) {
            logManager.addLog("[Dialog] " + message);
        }
    }

    private void startInstallation() {
        if (isInstalling) return;

        try {
            isInstalling = true;

            // 显示进度界面（保持在当前对话框，只切换按钮区域）
            layoutButtons.setVisibility(View.GONE);
            layoutInstallingProgress.setVisibility(View.VISIBLE);

            // 根据当前选择的授权方式选择安装助手
            if (currentMode == PrivilegeHelper.PrivilegeMode.SHIZUKU) {
                startShizukuInstallation();
            } else {
                startDhizukuInstallation();
            }

        } catch (Exception e) {
            Log.e(TAG, getString(R.string.start_installation_failed), e);
            isInstalling = false;
            Toast.makeText(this, getString(R.string.install_process_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            layoutInstallingProgress.setVisibility(View.GONE);
            layoutButtons.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 使用 Shizuku 安装
     */
    private void startShizukuInstallation() {
        log(getString(R.string.start_apk_install));
        if (isXapkFile) {
            // XAPK/APKS 安装
            ShizukuInstallHelper.installXapk(this, filePath, true, true, new ShizukuInstallHelper.InstallCallback() {
                @Override
                public void onProgress(String message) {
                    log(message);
                }

                @Override
                public void onSuccess(String message) {
                    log(message);
                    runOnUiThread(() -> showCompletionUI());
                }

                @Override
                public void onError(String error) {
                    log(getString(R.string.error_prefix, error));
                    runOnUiThread(() -> {
                        isInstalling = false;
                        Toast.makeText(InstallDialog.this, getString(R.string.xapk_install_failed, error), Toast.LENGTH_LONG).show();
                        layoutInstallingProgress.setVisibility(View.GONE);
                        layoutButtons.setVisibility(View.VISIBLE);
                        updateInstallButtonState();
                    });
                }
            });
        } else {
            // APK安装
            ShizukuInstallHelper.installApk(this, filePath, true, true, new ShizukuInstallHelper.InstallCallback() {
                @Override
                public void onProgress(String message) {
                    log(message);
                }

                @Override
                public void onSuccess(String message) {
                    log(message);
                    runOnUiThread(() -> showCompletionUI());
                }

                @Override
                public void onError(String error) {
                    log(getString(R.string.error_prefix, error));
                    runOnUiThread(() -> {
                        isInstalling = false;
                        Toast.makeText(InstallDialog.this, getString(R.string.apk_install_failed, error), Toast.LENGTH_LONG).show();
                        layoutInstallingProgress.setVisibility(View.GONE);
                        layoutButtons.setVisibility(View.VISIBLE);
                        updateInstallButtonState();
                    });
                }
            });
        }
    }

    /**
     * 使用 Dhizuku 安装
     */
    private void startDhizukuInstallation() {
        log(getString(R.string.start_apk_install));
        if (isXapkFile) {
            // XAPK/APKS 安装
            DhizukuInstallHelper.installXapk(this, filePath, true, true, new DhizukuInstallHelper.InstallCallback() {
                @Override
                public void onProgress(String message) {
                    log(message);
                }

                @Override
                public void onSuccess(String message) {
                    log(message);
                    runOnUiThread(() -> showCompletionUI());
                }

                @Override
                public void onError(String error) {
                    log(getString(R.string.error_prefix, error));
                    runOnUiThread(() -> {
                        isInstalling = false;
                        Toast.makeText(InstallDialog.this, getString(R.string.xapk_install_failed, error), Toast.LENGTH_LONG).show();
                        layoutInstallingProgress.setVisibility(View.GONE);
                        layoutButtons.setVisibility(View.VISIBLE);
                        updateInstallButtonState();
                    });
                }
            });
        } else {
            // APK安装
            DhizukuInstallHelper.installApk(this, filePath, true, true, new DhizukuInstallHelper.InstallCallback() {
                @Override
                public void onProgress(String message) {
                    log(message);
                }

                @Override
                public void onSuccess(String message) {
                    log(message);
                    runOnUiThread(() -> showCompletionUI());
                }

                @Override
                public void onError(String error) {
                    log(getString(R.string.error_prefix, error));
                    runOnUiThread(() -> {
                        isInstalling = false;
                        Toast.makeText(InstallDialog.this, getString(R.string.apk_install_failed, error), Toast.LENGTH_LONG).show();
                        layoutInstallingProgress.setVisibility(View.GONE);
                        layoutButtons.setVisibility(View.VISIBLE);
                        updateInstallButtonState();
                    });
                }
            });
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

                // 获取文件名，用于确定正确的扩展名
                String fileName = getFileNameFromUri(uri);
                String extension = getFileExtension(fileName);

                // 根据文件类型创建临时文件
                File tempFile;
                if (".xapk".equals(extension) || ".apks".equals(extension) || ".apkm".equals(extension)) {
                    tempFile = new File(getCacheDir(), "temp_install_" + System.currentTimeMillis() + extension);
                } else {
                    tempFile = new File(getCacheDir(), "temp_install.apk");
                }

                FileOutputStream outputStream = new FileOutputStream(tempFile);

                byte[] buffer = new byte[8192]; // 增加缓冲区大小以提高性能
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }

                inputStream.close();
                outputStream.flush();
                outputStream.close();

                Log.d(TAG, "Temp file created: " + tempFile.getAbsolutePath());
                return tempFile.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.get_file_path_from_uri_failed), e);
        }
        return null;
    }

    /**
     * 从 URI 获取文件名
     */
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;

        // 尝试从 URI 路径中提取文件名
        if (uri.getPath() != null) {
            String path = uri.getPath();
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash != -1 && lastSlash < path.length() - 1) {
                fileName = path.substring(lastSlash + 1);
            }
        }

        // 如果无法从路径中获取，尝试查询 ContentResolver
        if (fileName == null || fileName.isEmpty()) {
            try {
                String[] projection = {android.provider.OpenableColumns.DISPLAY_NAME};
                android.database.Cursor cursor = getContentResolver().query(
                    uri, projection, null, null, null);
                if (cursor != null) {
                    try {
                        int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (cursor.moveToFirst() && nameIndex != -1) {
                            fileName = cursor.getString(nameIndex);
                        }
                    } finally {
                        cursor.close();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Cannot get filename from ContentResolver", e);
            }
        }

        return fileName != null ? fileName : "temp.apk";
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return ".apk";
        }

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot != -1 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot).toLowerCase();
        }

        return ".apk";
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
        layoutInstallingProgress.setVisibility(View.GONE);
        layoutButtons.setVisibility(View.GONE);
        layoutCompletion.setVisibility(View.VISIBLE);

        // 保存安装的包名用于打开应用
        Log.d(TAG, getString(R.string.save_installed_package_name));
        installedPackageName = tvPackageName.getText().toString();

        // 检查已安装的应用信息并更新按钮和版本显示
        Log.d(TAG, getString(R.string.checking_installed_app_info));
        checkAndDisplayVersionComparison();
    }

    /**
     * 从安装完成页面回到安装确认步骤
     */
    private void backToInstallInfoFromCompletion() {
        layoutCompletion.setVisibility(View.GONE);
        layoutButtons.setVisibility(View.VISIBLE);
        isInstalling = false;
    }

    /**
     * 检查并显示版本比对信息
     */
    private void checkAndDisplayVersionComparison() {
        // 从 tvPackageName 中提取纯包名（去掉"包名: "前缀）
        String packageNameText = tvPackageName.getText().toString();
        String packageName = extractPackageName(packageNameText);
        
        if (packageName == null || packageName.isEmpty()) {
            Log.w(TAG, "Cannot extract package name from: " + packageNameText);
            return;
        }

        Log.d(TAG, "Checking installed app for package: " + packageName);

        new Thread(() -> {
            try {
                // 获取已安装应用的信息
                PackageInfo installedInfo = getInstalledAppInfo(packageName);

                runOnUiThread(() -> {
                    if (installedInfo != null) {
                        Log.d(TAG, "App is installed, showing version comparison");
                        // 应用已安装，显示版本比对
                        displayVersionInMainField(installedInfo);
                        updateInstallButtonForInstalledApp(installedInfo);
                    } else {
                        Log.d(TAG, "App is not installed, showing basic info");
                        // 应用未安装，显示基础版本信息
                        displayBasicVersionInfo();
                        updateInstallButtonForNewInstall();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, getString(R.string.check_installed_app_info_failed, e.getMessage()));
                runOnUiThread(() -> displayBasicVersionInfo());
            }
        }).start();
    }
    
    /**
     * 从显示文本中提取纯包名
     * @param displayText 显示文本，格式为 "包名: com.example.app"
     * @return 纯包名，如 "com.example.app"
     */
    private String extractPackageName(String displayText) {
        if (displayText == null || displayText.isEmpty()) {
            return null;
        }
        
        // 查找最后一个冒号的位置
        int colonIndex = displayText.lastIndexOf(':');
        if (colonIndex != -1 && colonIndex < displayText.length() - 1) {
            // 提取冒号后的内容并去除空格
            String packageName = displayText.substring(colonIndex + 1).trim();
            return packageName.isEmpty() ? null : packageName;
        }
        
        // 如果没有冒号，直接返回原文本（可能是纯包名）
        return displayText.trim();
    }

    /**
     * 在主版本字段中显示版本对比信息
     */
    private void displayVersionInMainField(PackageInfo installedInfo) {
        try {
            // 获取待安装APK的信息
            PackageManager pm = getPackageManager();
            PackageInfo apkInfo = pm.getPackageArchiveInfo(filePath, 0);

            if (apkInfo != null) {
                String apkVersionName = apkInfo.versionName != null ? apkInfo.versionName : "Unknown";
                long apkVersionCode = apkInfo.getLongVersionCode();

                String installedVersionName = installedInfo.versionName != null ? installedInfo.versionName : "Unknown";
                long installedVersionCode = installedInfo.getLongVersionCode();

                // 显示当前版本（已安装版本）
                tvVersion.setText(getString(R.string.current_version) + String.format("%s (%d)", installedVersionName, installedVersionCode));

                // 显示升级版本（待安装版本）
                if (tvUpgradeVersion != null) {
                    tvUpgradeVersion.setText(getString(R.string.upgrade_version) + String.format("%s (%d)", apkVersionName, apkVersionCode));
                }

                // 显示SDK信息
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    int apkMinSdk = apkInfo.applicationInfo.minSdkVersion;
                    tvMinSdk.setText(getString(R.string.min_sdk) + apkMinSdk);
                } else {
                    tvMinSdk.setText(getString(R.string.min_sdk) + "N/A");
                }

                int apkTargetSdk = apkInfo.applicationInfo.targetSdkVersion;
                tvTargetSdk.setText(getString(R.string.target_sdk) + apkTargetSdk);

                // 根据版本差异调整按钮文本
                int versionComparison = PackageInfoHelper.compareVersions(apkVersionName, installedVersionName);
                if (versionComparison > 0) {
                    btnInstall.setText(R.string.upgrade);
                } else if (versionComparison < 0) {
                    btnInstall.setText(R.string.downgrade);
                } else {
                    btnInstall.setText(R.string.reinstall);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.updating_version_info_installed_app) + ": " + e.getMessage());
            displayBasicVersionInfo();
        }
    }

    /**
     * 显示基础版本信息（新安装情况）
     */
    private void displayBasicVersionInfo() {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo apkInfo = pm.getPackageArchiveInfo(filePath, 0);

            if (apkInfo != null) {
                String versionName = apkInfo.versionName != null ? apkInfo.versionName : "Unknown";
                long versionCode = apkInfo.getLongVersionCode();
                // 新安装时，当前版本显示为"未安装"，升级版本显示APK版本
                tvVersion.setText(getString(R.string.current_version) + getString(R.string.not_installed));
                if (tvUpgradeVersion != null) {
                    tvUpgradeVersion.setText(getString(R.string.upgrade_version) + String.format("%s (%d)", versionName, versionCode));
                }

                // 显示SDK信息
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    int minSdk = apkInfo.applicationInfo.minSdkVersion;
                    tvMinSdk.setText(getString(R.string.min_sdk) + minSdk);
                } else {
                    tvMinSdk.setText(getString(R.string.min_sdk) + "N/A");
                }
                int targetSdk = apkInfo.applicationInfo.targetSdkVersion;
                tvTargetSdk.setText(getString(R.string.target_sdk) + targetSdk);
            }
            btnInstall.setText(R.string.install);
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.updating_version_info_new_install) + ": " + e.getMessage());
            tvVersion.setText(R.string.unknown);
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
                    // 版本相同，显示重新安装
                    btnInstall.setText(R.string.reinstall);
                } else if (apkVersionCode > installedVersionCode) {
                    // APK版本大于已安装版本，显示升级应用
                    btnInstall.setText(R.string.upgrade_app);
                } else {
                    // APK版本小于已安装版本，显示降级(或保持安装)
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
        layoutInstallingProgress.setVisibility(View.GONE);
        layoutButtons.setVisibility(View.VISIBLE);
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

        // 使用 Shizuku Shell 命令启动应用
        launchAppViaShizukuShell(installedPackageName);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到前台时重新检查权限状态
        if (layoutInstallInfo.getVisibility() == View.VISIBLE) {
            updateInstallButtonState();
        }
        // 如果权限对话框正在显示，也更新其状态
        if (privilegeDialog != null && privilegeDialog.isShowing()) {
            checkPrivilegeStatus();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 销毁权限对话框避免内存泄漏
        if (privilegeDialog != null && privilegeDialog.isShowing()) {
            privilegeDialog.dismiss();
        }
    }

    /**
     * 使用 Shizuku Shell 命令启动应用
     */
    private void launchAppViaShizukuShell(String packageName) {
        try {
            Log.d(TAG, getString(R.string.launching_app_via_shizuku_shell, packageName));

            // 异步执行Shell命令
            new Thread(() -> {
                try {
                    // 使用 am start 命令
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
