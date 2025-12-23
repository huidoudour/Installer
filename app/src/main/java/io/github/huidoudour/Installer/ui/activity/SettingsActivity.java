package io.github.huidoudour.Installer.ui.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import com.google.android.material.color.DynamicColors;
import io.github.huidoudour.Installer.databinding.ActivitySettingsBinding;
import io.github.huidoudour.Installer.ui.settings.SettingsFragment;
import io.github.huidoudour.Installer.utils.LanguageManager;

/**
 * 设置页面 Activity
 * 用于直接启动设置页面
 */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用用户选择的语言
        LanguageManager.applyUserLanguagePreference(this);
        
        // 启用动态颜色（壁纸取色）- Android 12+
        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 隐藏 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // 启用边到边显示
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // 加载设置 Fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(binding.fragmentContainer.getId(), new SettingsFragment())
                    .commit();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // 添加返回动画
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }
}
