package io.github.huidoudour.Installer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class MeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_me);

        setupWebsiteButton();
    }

    private void setupWebsiteButton() {
        View btnWebsite = findViewById(R.id.btn_website);
        
        // 添加点击状态反馈
        btnWebsite.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        // 按下时改变透明度，提供视觉反馈
                        v.setAlpha(0.7f);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        // 抬起或取消时恢复透明度
                        v.setAlpha(1.0f);
                        break;
                }
                return false; // 不消费事件，让onClickListener处理
            }
        });
        
        btnWebsite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 添加日志调试
                android.util.Log.d("MeActivity", "按钮被点击");
                
                // 打开GitHub个人主页
                openUrl("https://github.com/huidoudour");
            }
        });
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        
        // 添加调试信息
        android.util.Log.d("MeActivity", "尝试打开链接: " + url);
        
        try {
            // 添加Android 14兼容性标志
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // 对于Android 11+，使用更兼容的查询方式
            List<android.content.pm.ResolveInfo> activities = getPackageManager().queryIntentActivities(intent, 
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            
            android.util.Log.d("MeActivity", "可用的应用数量: " + activities.size());
            
            for (android.content.pm.ResolveInfo info : activities) {
                android.util.Log.d("MeActivity", "可用应用: " + info.activityInfo.packageName);
            }
            
            if (activities.size() > 0) {
                android.util.Log.d("MeActivity", "找到可以处理链接的应用");
                
                // 尝试启动应用选择器，让用户选择
                Intent chooserIntent = Intent.createChooser(intent, "选择打开方式");
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                // 添加异常捕获，防止Android 14上的安全限制
                try {
                    startActivity(chooserIntent);
                    android.util.Log.d("MeActivity", "成功启动应用选择器");
                } catch (android.content.ActivityNotFoundException e) {
                    // 如果选择器失败，尝试直接启动
                    android.util.Log.w("MeActivity", "应用选择器失败，尝试直接启动: " + e.getMessage());
                    try {
                        startActivity(intent);
                        android.util.Log.d("MeActivity", "直接启动成功");
                    } catch (android.content.ActivityNotFoundException e2) {
                        android.util.Log.e("MeActivity", "直接启动也失败: " + e2.getMessage());
                        showSnackbar("无法打开链接，请检查浏览器应用");
                    }
                }
            } else {
                android.util.Log.d("MeActivity", "未找到可以处理链接的应用");
                
                // 在Android 14上，即使查询不到应用，也尝试直接启动
                android.util.Log.w("MeActivity", "查询不到应用，但尝试直接启动链接");
                try {
                    startActivity(intent);
                    android.util.Log.d("MeActivity", "直接启动成功（绕过包可见性限制）");
                } catch (android.content.ActivityNotFoundException e) {
                    android.util.Log.e("MeActivity", "直接启动失败: " + e.getMessage());
                    
                    // 提供更详细的错误信息和解决方案
                    String errorMessage = "无法打开链接。\n" +
                        "可能的原因：\n" +
                        "1. 设备上没有安装浏览器应用\n" +
                        "2. Android 14包可见性限制\n" +
                        "3. 系统安全设置阻止了链接打开\n" +
                        "\n请尝试安装Chrome、Firefox或其他浏览器应用。";
                    showSnackbar(errorMessage);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MeActivity", "打开链接失败: " + e.getMessage(), e);
            showSnackbar("打开链接失败: " + e.getMessage());
        }
    }

    private void showSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // 添加返回动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}