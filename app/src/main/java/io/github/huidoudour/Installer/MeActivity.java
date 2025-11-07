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
            // 检查是否有应用可以处理这个Intent
            if (intent.resolveActivity(getPackageManager()) != null) {
                android.util.Log.d("MeActivity", "找到可以处理链接的应用");
                
                // 添加FLAG_ACTIVITY_NEW_TASK标志，确保在新的任务栈中打开
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                // 列出所有可以处理VIEW intent的应用（用于调试）
                List<android.content.pm.ResolveInfo> activities = getPackageManager().queryIntentActivities(intent, 0);
                android.util.Log.d("MeActivity", "可用的应用数量: " + activities.size());
                
                for (android.content.pm.ResolveInfo info : activities) {
                    android.util.Log.d("MeActivity", "可用应用: " + info.activityInfo.packageName);
                }
                
                // 尝试启动应用选择器，让用户选择
                Intent chooserIntent = Intent.createChooser(intent, "选择打开方式");
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooserIntent);
                android.util.Log.d("MeActivity", "成功启动应用选择器");
            } else {
                android.util.Log.d("MeActivity", "未找到可以处理链接的应用");
                
                // 提供更详细的错误信息
                showSnackbar("未找到可以处理此链接的应用，请检查浏览器是否安装正确");
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