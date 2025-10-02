package io.github.huidoudour.Installer;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // 启用边缘到边缘显示（替代 EdgeToEdge.enable(this)）
        enableEdgeToEdge();
    }

    private void enableEdgeToEdge() {
        // 让内容延伸到系统栏后面
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // 设置状态栏和导航栏的样式
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        // 根据需要设置亮色或暗色图标
        windowInsetsController.setAppearanceLightStatusBars(true);
        windowInsetsController.setAppearanceLightNavigationBars(true);
    }
}