package io.github.huidoudour.Installer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

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
        Button btnWebsite = findViewById(R.id.btn_website);
        
        btnWebsite.setOnClickListener(v -> {
            // 打开 GitHub 个人主页
            openUrl("https://github.com/huidoudour");
        });
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
            
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("MeActivity", "Failed to open URL: " + e.getMessage(), e);
            showNotification(getString(R.string.link_open_failed, e.getMessage()));
        }
    }

    private void showNotification(String message) {
        NotificationHelper.showNotification(this, message);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // 添加返回动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
