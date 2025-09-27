package io.github.huidoudour.Installer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView tvShizukuStatus;
    private TextView tvSelectedFile;
    private TextView tvLog;
    private Button btnSelectFile;
    private Button btnRequestPermission;
    private Button btnInstall;

    private Uri selectedApkUri;
    private static final int REQUEST_CODE_SELECT_APK = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();

        appendLog("应用启动成功");
        checkShizukuAvailability();
    }

    @SuppressLint("SetTextI18n")
    private void initViews() {
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvLog = findViewById(R.id.tvLog);
        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnRequestPermission = findViewById(R.id.btnRequestPermission);
        btnInstall = findViewById(R.id.btnInstall);

        // 初始状态
        tvShizukuStatus.setText("Shizuku权限: 未授权");
        btnInstall.setEnabled(false);
    }

    private void setupClickListeners() {
        btnSelectFile.setOnClickListener(v -> selectApkFile());
        btnRequestPermission.setOnClickListener(v -> requestShizukuPermission());
        btnInstall.setOnClickListener(v -> installApk());
    }

    private void checkShizukuAvailability() {
        try {
            // 安全地检查Shizuku类是否存在
            Class.forName("rikka.shizuku.Shizuku");
            appendLog("Shizuku库加载成功");
            updateShizukuStatus(true, "库可用");
        } catch (ClassNotFoundException e) {
            appendLog("Shizuku库未找到: " + e.getMessage());
            updateShizukuStatus(false, "库未集成");
        } catch (Exception e) {
            appendLog("检查Shizuku时出错: " + e.getMessage());
            updateShizukuStatus(false, "检查失败");
        }
    }

    private void updateShizukuStatus(boolean available, String message) {
        runOnUiThread(() -> {
            String statusText = available ? "Shizuku权限: 已就绪" : "Shizuku权限: " + message;
            tvShizukuStatus.setText(statusText);

            if (available) {
                tvShizukuStatus.setBackgroundColor(0xFFE8F5E8);
                tvShizukuStatus.setTextColor(0xFF2E7D32);
            } else {
                tvShizukuStatus.setBackgroundColor(0xFFFFEBEE);
                tvShizukuStatus.setTextColor(0xFFC62828);
            }

            updateInstallButtonState();
        });
    }

    private void selectApkFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.android.package-archive");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择APK文件"), REQUEST_CODE_SELECT_APK);
            appendLog("打开文件选择器");
        } catch (Exception e) {
            appendLog("无法打开文件选择器: " + e.getMessage());
        }
    }

    private void requestShizukuPermission() {
        appendLog("请求Shizuku权限功能待实现");
    }

    private void installApk() {
        appendLog("安装APK功能待实现");
    }

    private void updateInstallButtonState() {
        boolean canInstall = (selectedApkUri != null);
        runOnUiThread(() -> {
            btnInstall.setEnabled(canInstall);
            btnInstall.setBackgroundColor(canInstall ? 0xFF2196F3 : 0xFF9E9E9E);
        });
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            String currentText = tvLog.getText().toString();
            String newText = "[" + System.currentTimeMillis() + "] " + message + "\n" + currentText;

            // 限制日志长度
            if (newText.length() > 1000) {
                newText = newText.substring(0, 1000) + "\n...（日志已截断）";
            }

            tvLog.setText(newText);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_APK && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                selectedApkUri = data.getData();
                String fileName = getFileNameFromUri(selectedApkUri);
                tvSelectedFile.setText("已选择文件: " + fileName);
                appendLog("已选择文件: " + fileName);
                updateInstallButtonState();
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = null;  // 使用具体的Cursor类型，而不是var
            try {
                cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex("_display_name");
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "未知文件";
    }
}