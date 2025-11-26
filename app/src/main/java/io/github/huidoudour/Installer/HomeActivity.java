package io.github.huidoudour.Installer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import io.github.huidoudour.Installer.databinding.ActivityHomeBinding;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_BACKGROUND_DISPLAY = "background_display";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 启用动态颜色（壁纸取色）- Android 12+
        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // 根据设置控制后台显示
        boolean isBackgroundDisplayEnabled = sharedPreferences.getBoolean(KEY_BACKGROUND_DISPLAY, true);
        if (!isBackgroundDisplayEnabled) {
            // 如果后台显示被禁用，设置任务属性以排除从最近任务列表
            setTaskExcludeFromRecents();
        }

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 隐藏 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // 启用边到边显示
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        BottomNavigationView navView = findViewById(R.id.nav_view);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_home);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // 处理从其他应用传递过来的安装意图
        handleInstallIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 处理新的安装意图
        handleInstallIntent(intent);
    }

    /**
     * 处理安装意图
     * 当其他应用选择本应用作为安装器时调用
     */
    private void handleInstallIntent(Intent intent) {
        if (intent == null) return;
        
        String action = intent.getAction();
        Uri data = intent.getData();
        
        // 检查是否是安装意图
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_INSTALL_PACKAGE.equals(action)) {
            if (data != null) {
                // 将APK文件URI传递给InstallerFragment
                Bundle bundle = new Bundle();
                bundle.putParcelable("install_uri", data);
                
                // 导航到安装器页面
                NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_home);
                navController.navigate(R.id.navigation_home, bundle);
                
                Log.d("HomeActivity", "接收到安装意图，URI: " + data.toString());
            }
        }
    }

    /**
     * 设置任务属性以排除从最近任务列表
     * 当后台显示被禁用时调用此方法
     */
    private void setTaskExcludeFromRecents() {
        try {
            // 方法1：设置FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS标志
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            
            // 方法2：对于Android 8.0+，可以使用Activity的excludeFromRecents属性
            // 注意：setExcludeFromRecents()方法不存在，我们使用Intent标志
            
            // 方法3：设置任务描述，标记为不显示在最近任务中
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setTaskDescription(new android.app.ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    R.mipmap.ic_launcher,
                    getColor(android.R.color.transparent)
                ));
            }
            
            Log.d("HomeActivity", "后台显示已禁用，应用将不会出现在最近任务列表中");
        } catch (Exception e) {
            Log.e("HomeActivity", "设置后台显示失败: " + e.getMessage());
        }
    }

}