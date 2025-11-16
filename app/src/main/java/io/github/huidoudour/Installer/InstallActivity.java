package io.github.huidoudour.Installer;

import android.content.ContentResolver;
import android.content.Context;
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
            btnInstall.setText("安装");
        } else {
            btnInstall.setEnabled(false);
            btnInstall.setText("Shizuku未就绪");
            
            new MaterialAlertDialogBuilder(this)
                .setTitle("Shizuku未就绪")
                .setMessage("需要Shizuku权限才能进行安装。请确保Shizuku服务已启动并授予权限。")
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", (dialog, which) -> finish())
                .show();
        }
    }
    
    private void startInstallation() {
        // 切换到安装中状态界面
        layoutInstallInfo.setVisibility(View.GONE);
        layoutProgress.setVisibility(View.VISIBLE);
        
        if (isXapkFile) {
            installXapk();
        } else {
            installSingleApk();
        }
    }
    
    private void installSingleApk() {
        File apkFile = new File(filePath);
        if (!apkFile.exists()) {
            showErrorAndExit("安装文件不存在");
            return;
        }
        
        ShizukuInstallHelper.installSingleApk(
            apkFile,
            false, // replaceExisting
            false, // grantPermissions
            new ShizukuInstallHelper.InstallCallback() {
                @Override
                public void onProgress(String message) {
                    runOnUiThread(() -> {
                        // 可以在这里更新按钮文本显示进度
                        btnInstallDisabled.setText(message);
                    });
                }

                @Override
                public void onSuccess(String message) {
                    runOnUiThread(() -> {
                        // 切换到安装完成状态界面
                        layoutInstallInfo.setVisibility(View.VISIBLE);
                        layoutProgress.setVisibility(View.GONE);
                        
                        // 修改按钮文本和功能
                        btnInstall.setText("打开");
                        btnCancel.setText("完成");
                        
                        // 设置按钮点击事件
                        btnInstall.setOnClickListener(v -> openApp());
                        btnCancel.setOnClickListener(v -> finish());
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        new MaterialAlertDialogBuilder(InstallActivity.this)
                            .setTitle("安装失败")
                            .setMessage(error)
                            .setPositiveButton("确定", null)
                            .setNegativeButton("重试", (dialog, which) -> startInstallation())
                            .show();
                    });
                }
            }
        );
    }
    
    private void installXapk() {
        ShizukuInstallHelper.installXapk(
            this,
            filePath,
            false, // replaceExisting
            false, // grantPermissions
            new ShizukuInstallHelper.InstallCallback() {
                @Override
                public void onProgress(String message) {
                    runOnUiThread(() -> {
                        // 可以在这里更新按钮文本显示进度
                        btnInstallDisabled.setText(message);
                    });
                }

                @Override
                public void onSuccess(String message) {
                    runOnUiThread(() -> {
                        // 切换到安装完成状态界面
                        layoutInstallInfo.setVisibility(View.VISIBLE);
                        layoutProgress.setVisibility(View.GONE);
                        
                        // 修改按钮文本和功能
                        btnInstall.setText("打开");
                        btnCancel.setText("完成");
                        
                        // 设置按钮点击事件
                        btnInstall.setOnClickListener(v -> openApp());
                        btnCancel.setOnClickListener(v -> finish());
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        new MaterialAlertDialogBuilder(InstallActivity.this)
                            .setTitle("安装失败")
                            .setMessage(error)
                            .setPositiveButton("确定", null)
                            .setNegativeButton("重试", (dialog, which) -> startInstallation())
                            .show();
                    });
                }
            }
        );
    }
    
    private void showErrorAndExit(String message) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("错误")
            .setMessage(message)
            .setPositiveButton("确定", (dialog, which) -> finish())
            .setOnDismissListener(dialog -> finish())
            .show();
    }
    
    /**
     * 从URI获取文件路径
     */
    private String getFilePathFromUri(Uri uri) {
        if (uri == null) {
            Log.e(TAG, "URI为空");
            return null;
        }

        Log.d(TAG, "处理URI: " + uri.toString());
        Log.d(TAG, "URI scheme: " + uri.getScheme());

        if ("file".equals(uri.getScheme())) {
            String path = uri.getPath();
            Log.d(TAG, "文件路径: " + path);
            return path;
        }

        // 处理content://类型的URI（来自外部应用的文件分享）
        if ("content".equals(uri.getScheme())) {
            ContentResolver contentResolver = getContentResolver();
            Cursor cursor = null;
            String fileName = null;
            
            try {
                // 首先尝试获取文件名
                String[] projection = {OpenableColumns.DISPLAY_NAME};
                cursor = contentResolver.query(uri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                        Log.d(TAG, "获取到文件名: " + fileName);
                    }
                }
                if (fileName == null) {
                    // 如果无法获取文件名，使用URI的最后路径段
                    fileName = uri.getLastPathSegment();
                    if (fileName == null) fileName = "selected.apk";
                    Log.d(TAG, "使用备用文件名: " + fileName);
                }

                // 复制文件到缓存目录
                File cacheFile = copyUriToCache(uri, fileName);
                if (cacheFile != null) {
                    Log.d(TAG, "文件复制成功: " + cacheFile.getAbsolutePath());
                    return cacheFile.getAbsolutePath();
                } else {
                    Log.e(TAG, "文件复制失败");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "权限不足，无法访问URI", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "权限不足，无法访问文件", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "处理content URI失败", e);
            } finally {
                if (cursor != null) cursor.close();
            }
            
            // 如果上面的方法失败，尝试备用方法
            try {
                String fallbackFileName = fileName != null ? fileName : "selected.apk";
                Log.d(TAG, "尝试备用方法，文件名: " + fallbackFileName);
                String result = copyUriToCacheWithInputStream(uri, fallbackFileName);
                if (result != null) {
                    Log.d(TAG, "备用方法成功: " + result);
                } else {
                    Log.e(TAG, "备用方法也失败");
                }
                return result;
            } catch (Exception e) {
                Log.e(TAG, "备用方法也失败", e);
            }
        }
        
        Log.e(TAG, "所有方法都失败，返回null");
        return null;
    }
    
    private File copyUriToCache(Uri uri, String fileName) {
        ParcelFileDescriptor pfd = null;
        FileInputStream in = null;
        FileOutputStream out = null;
        
        try {
            Log.d(TAG, "开始复制文件到缓存，文件名: " + fileName);
            
            pfd = getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) {
                Log.e(TAG, "无法获取ParcelFileDescriptor");
                return null;
            }
            
            in = new FileInputStream(pfd.getFileDescriptor());
            File outputFile = new File(getCacheDir(), fileName);
            out = new FileOutputStream(outputFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            Log.d(TAG, "文件复制完成，总字节数: " + totalBytes);
            
            return outputFile;
        } catch (SecurityException e) {
            Log.e(TAG, "权限不足，无法访问文件", e);
            return null;
        } catch (FileNotFoundException e) {
            Log.e(TAG, "文件未找到", e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "IO异常", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "复制到 cache 失败", e);
            return null;
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (pfd != null) pfd.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭流失败", e);
            }
        }
    }
    
    private String copyUriToCacheWithInputStream(Uri uri, String fileName) {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            // 使用InputStream直接复制文件
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "无法打开输入流");
                return null;
            }
            
            // 确保文件名有效
            if (fileName == null) {
                fileName = "selected.apk";
            }
            
            File outputFile = new File(getCacheDir(), fileName);
            outputStream = new FileOutputStream(outputFile);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            Log.d(TAG, "使用InputStream成功复制文件到缓存: " + outputFile.getAbsolutePath());
            return outputFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "使用InputStream复制文件失败", e);
            return null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭流失败", e);
            }
        }
    }
    
    /**
     * 打开已安装的应用
     */
    private void openApp() {
        try {
            // 获取已安装应用的包名
            String packageName = null;
            if (!isXapkFile) {
                // 单个APK文件，直接从APK中获取包名
                packageName = ApkAnalyzer.getPackageName(this, filePath);
            } else {
                // XAPK文件，解压并获取第一个APK的包名
                try {
                    List<File> apkFiles = XapkInstaller.extractXapk(this, filePath);
                    if (!apkFiles.isEmpty()) {
                        packageName = ApkAnalyzer.getPackageName(this, apkFiles.get(0).getAbsolutePath());
                        // 清理临时文件
                        XapkInstaller.cleanupTempFiles(apkFiles);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解压XAPK文件失败", e);
                }
            }
            
            if (packageName != null) {
                // 尝试打开应用
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                    finish(); // 打开应用后关闭安装页面
                } else {
                    // 应用可能没有启动器Activity，显示提示
                    Toast.makeText(this, "无法打开应用，可能没有启动器界面", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "无法获取应用包名", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "打开应用失败", e);
            Toast.makeText(this, "打开应用失败", Toast.LENGTH_SHORT).show();
        }
    }
}