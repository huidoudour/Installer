package io.github.huidoudour.Installer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import io.github.huidoudour.Installer.utils.LanguageManager;
import io.github.huidoudour.Installer.R;

public class MeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用用户选择的语言
        LanguageManager.applyUserLanguagePreference(this);
        
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
                android.util.Log.d("MeActivity", getString(R.string.button_clicked));
                
                // 打开GitHub个人主页
                openUrl("https://github.com/huidoudour");
            }
        });
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        
        // 添加调试信息
        android.util.Log.d("MeActivity", getString(R.string.attempting_to_open_link, url));
        
        try {
            // 添加Android 14兼容性标志
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // 对于Android 11+，使用更兼容的查询方式
            List<android.content.pm.ResolveInfo> activities = getPackageManager().queryIntentActivities(intent, 
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            
            android.util.Log.d("MeActivity", getString(R.string.available_apps_count, activities.size()));
            
            for (android.content.pm.ResolveInfo info : activities) {
                android.util.Log.d("MeActivity", getString(R.string.available_app, info.activityInfo.packageName));
            }
            
            if (activities.size() > 0) {
                android.util.Log.d("MeActivity", getString(R.string.found_apps_to_handle_link));
                
                // 尝试启动应用选择器，让用户选择
                Intent chooserIntent = Intent.createChooser(intent, getString(R.string.choose_open_method));
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                // 添加异常捕获，防止Android 14上的安全限制
                try {
                    startActivity(chooserIntent);
                    android.util.Log.d("MeActivity", getString(R.string.chooser_started_successfully));
                } catch (android.content.ActivityNotFoundException e) {
                    // 如果选择器失败，尝试直接启动
                    android.util.Log.w("MeActivity", getString(R.string.chooser_failed_try_direct_launch, e.getMessage()));
                    try {
                        startActivity(intent);
                        android.util.Log.d("MeActivity", getString(R.string.direct_launch_success));
                    } catch (android.content.ActivityNotFoundException e2) {
                        android.util.Log.e("MeActivity", getString(R.string.direct_launch_failed, e2.getMessage()));
                        showSnackbar(getString(R.string.open_link_failed));
                    }
                }
            } else {
                android.util.Log.d("MeActivity", getString(R.string.no_apps_found_to_handle_link));
                
                // 在Android 14上，即使查询不到应用，也尝试直接启动
                android.util.Log.w("MeActivity", getString(R.string.query_failed_but_try_direct_launch));
                try {
                    startActivity(intent);
                    android.util.Log.d("MeActivity", getString(R.string.direct_launch_success_bypass));
                } catch (android.content.ActivityNotFoundException e) {
                    android.util.Log.e("MeActivity", getString(R.string.direct_launch_failed, e.getMessage()));
                    
                    // 提供更详细的错误信息和解决方案
                    String errorMessage = getString(R.string.link_open_error);
                    showSnackbar(errorMessage);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MeActivity", getString(R.string.open_link_failed_log, e.getMessage()), e);
            showSnackbar(getString(R.string.link_open_failed, e.getMessage()));
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
