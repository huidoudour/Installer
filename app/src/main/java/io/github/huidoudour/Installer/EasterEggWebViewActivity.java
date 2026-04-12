package io.github.huidoudour.Installer;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class EasterEggWebViewActivity extends AppCompatActivity {
    
    private WebView webView;
    private ProgressBar progressBar;
    private View statusBarPlaceholder;
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始设置为浅色状态栏（适配加载页面的白色背景）
        getWindow().setStatusBarColor(android.graphics.Color.WHITE);
        
        // 设置状态栏图标为深色（黑色）
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
        
        setContentView(R.layout.activity_easter_egg_webview);
        
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        statusBarPlaceholder = findViewById(R.id.statusBarPlaceholder);
        
        // 应用窗口插入选项监听器，正确处理状态栏空间
        ViewCompat.setOnApplyWindowInsetsListener(statusBarPlaceholder, (v, insets) -> {
            var systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.getLayoutParams().height = systemBars.top;
            v.requestLayout();
            return insets;
        });
        
        // 为WebView设置底部插入选项
        ViewCompat.setOnApplyWindowInsetsListener(webView, (v, insets) -> {
            var systemBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });
        
        // 配置WebView
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // 设置WebView客户端
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setProgress(100);
            }
        });
        
        // 设置WebChrome客户端来显示加载进度
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                    
                    // 页面加载完成，延迟切换到深色状态栏
                    webView.postDelayed(() -> {
                        android.util.Log.d("EasterEgg", "Page loaded, switching to dark status bar");
                        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#1a1a1a"));
                        // 移除 LIGHT_STATUS_BAR 标志，让图标变为白色
                        getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        );
                    }, 300); // 延迟300ms，确保页面渲染完成
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }
        });
        
        // 设置返回按钮处理
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        
        // 加载网页
        loadWebPage();
    }
    
    /**
     * 加载网页
     */
    private void loadWebPage() {
        String url = "https://app.ccrh-cmit.uno";
        webView.loadUrl(url);
    }
    

}
