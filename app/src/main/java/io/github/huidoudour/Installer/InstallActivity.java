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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

import io.github.huidoudour.Installer.utils.ApkAnalyzer;
import io.github.huidoudour.Installer.utils.XapkInstaller;
import io.github.huidoudour.Installer.utils.ShizukuInstallHelper;
import rikka.shizuku.Shizuku;

public class InstallActivity extends AppCompatActivity {

    private static final String TAG = "InstallActivity";
    
    private LinearLayout layoutInstallInfo;
    private LinearLayout layoutProgress;
    private TextView tvAppName;
    private TextView tvPackageName;
    private TextView tvVersion;
    private TextView tvFileSize;
    private TextView tvInstallStatus;
    private ProgressBar progressBar;
    private Button btnInstall;
    private Button btnCancel;
    
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
        tvAppName = findViewById(R.id.tv_app_name);
        tvPackageName = findViewById(R.id.tv_package_name);
        tvVersion = findViewById(R.id.tv_version);
        tvFileSize = findViewById(R.id.tv_file_size);
        tvInstallStatus = findViewById(R.id.tv_install_status);
        progressBar = findViewById(R.id.progress_bar);
        btnInstall = findViewById(R.id.btn_install);
        btnCancel = findViewById(R.id.btn_cancel);
        
        btnInstall.setOnClickListener(v -> startInstallation());
        btnCancel.setOnClickListener(v -> finish());
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
            tvFileSize.setText("文件大小: " + fileSize);
            
            if (!isXapkFile) {
                // 单个APK文件信息
                String packageName = ApkAnalyzer.getPackageName(this, filePath);
                String versionInfo = ApkAnalyzer.getVersionInfo(this, filePath);
                
                tvPackageName.setText("包名: " + (packageName != null ? packageName : "未知"));
                tvVersion.setText("版本: " + (versionInfo != null ? versionInfo : "未知"));
                
                // 尝试获取应用名称（使用包名作为应用名称）
                tvAppName.setText(packageName != null ? packageName : "未知应用");
            } else {
                // XAPK文件信息
                tvAppName.setText("XAPK安装包");
                tvPackageName.setText("文件类型: XAPK");
                int apkCount = XapkInstaller.getApkCount(filePath);
                tvVersion.setText("包含 " + apkCount + " 个APK文件");
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
        // 切换到进度界面
        layoutInstallInfo.setVisibility(View.GONE);
        layoutProgress.setVisibility(View.VISIBLE);
        
        tvInstallStatus.setText("准备安装...");
        progressBar.setIndeterminate(true);
        
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
                        tvInstallStatus.setText(message);
                    });
                }

                @Override
                public void onSuccess(String message) {
                    runOnUiThread(() -> {
                        tvInstallStatus.setText("安装成功！");
                        progressBar.setIndeterminate(false);
                        progressBar.setProgress(100);
                        
                        new MaterialAlertDialogBuilder(InstallActivity.this)
                            .setTitle("安装完成")
                            .setMessage(message)
                            .setPositiveButton("确定", (dialog, which) -> finish())
                            .show();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        tvInstallStatus.setText("安装失败");
                        progressBar.setIndeterminate(false);
                        
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
                        tvInstallStatus.setText(message);
                    });
                }

                @Override
                public void onSuccess(String message) {
                    runOnUiThread(() -> {
                        tvInstallStatus.setText("安装成功！");
                        progressBar.setIndeterminate(false);
                        progressBar.setProgress(100);
                        
                        new MaterialAlertDialogBuilder(InstallActivity.this)
                            .setTitle("安装完成")
                            .setMessage(message)
                            .setPositiveButton("确定", (dialog, which) -> finish())
                            .show();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        tvInstallStatus.setText("安装失败");
                        progressBar.setIndeterminate(false);
                        
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
}