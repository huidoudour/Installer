package io.github.huidoudour.Installer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

public class MeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_me);

        setupWebsiteButton();
    }

    private void setupWebsiteButton() {
        View btnWebsite = findViewById(R.id.btn_website);
        btnWebsite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 打开GitHub个人主页
                openUrl("https://github.com/huidoudour");
            }
        });
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            showSnackbar("无法打开链接");
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