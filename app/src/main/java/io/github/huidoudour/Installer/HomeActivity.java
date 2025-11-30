package io.github.huidoudour.Installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import io.github.huidoudour.Installer.service.HideTaskService;
import io.github.huidoudour.Installer.utils.BackgroundManager;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_BACKGROUND_DISPLAY = "background_display";
    private static final String KEY_LAUNCH_MODE = "launch_mode";
    
    private BackgroundManager backgroundManager;
    private HideTaskReceiver hideTaskReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 启用动态颜色（壁纸取色）- Android 12+
        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // 初始化后台管理器
        backgroundManager = new BackgroundManager(this);
        
        // 注册广播接收器
        registerHideTaskReceiver();
        
        // 启动隐藏任务服务
        startHideTaskService();
        
        // 检查是否是重启意图（避免无限循环）
        boolean isRestartIntent = (getIntent().getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0;
        
        // 根据设置控制后台显示
        boolean isBackgroundDisplayEnabled = sharedPreferences.getBoolean(KEY_BACKGROUND_DISPLAY, true);
        if (!isBackgroundDisplayEnabled && !isRestartIntent) {
            // 如果后台显示被禁用且不是重启意图，设置任务属性以排除从最近任务列表
            setTaskExcludeFromRecents();
            return; // 立即返回，避免继续执行下面的代码
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
     * 设置任务排除在最近任务列表之外
     */
    private void setTaskExcludeFromRecents() {
        // 最可靠的方法：重新启动应用，确保标志在Activity启动前设置
        // 因为FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS必须在Activity启动前设置才有效
        Intent restartIntent = new Intent(this, HomeActivity.class);
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(restartIntent);
        finish();
    }
    
    /**
     * 动态设置任务是否排除在最近任务列表之外
     * @param exclude 是否排除
     */
    public void setExcludeFromRecents(boolean exclude) {
        try {
            // 使用反射设置任务属性（需要系统权限，可能不适用于所有设备）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (exclude) {
                    moveTaskToBack(true);
                    // 尝试通过ActivityManager设置任务属性
                    // 注意：这需要系统权限，在普通应用中可能不起作用
                }
            }
        } catch (Exception e) {
            Log.w("HomeActivity", "动态设置任务属性失败: " + e.getMessage());
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 处理暂停时的隐藏逻辑
        if (backgroundManager != null) {
            backgroundManager.handleOnPause();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 处理恢复时的显示逻辑
        if (backgroundManager != null) {
            backgroundManager.handleOnResume();
        }
    }
    
    /**
     * 获取后台管理器实例
     * @return BackgroundManager实例
     */
    public BackgroundManager getBackgroundManager() {
        return backgroundManager;
    }
    
    /**
     * 注册隐藏任务广播接收器
     */
    private void registerHideTaskReceiver() {
        hideTaskReceiver = new HideTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("io.github.huidoudour.Installer.HIDE_APP");
        filter.addAction("io.github.huidoudour.Installer.SHOW_APP");
        registerReceiver(hideTaskReceiver, filter);
    }
    
    /**
     * 启动隐藏任务服务
     */
    private void startHideTaskService() {
        Intent serviceIntent = new Intent(this, HideTaskService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
    
    /**
     * 隐藏任务广播接收器
     */
    private class HideTaskReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String action = intent.getAction();
                if ("io.github.huidoudour.Installer.HIDE_APP".equals(action)) {
                    setExcludeFromRecents(true);
                } else if ("io.github.huidoudour.Installer.SHOW_APP".equals(action)) {
                    // 注意：Android系统限制了应用主动将自己添加到最近任务列表
                    // 这里主要是为了恢复应用的正常状态
                }
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 注销广播接收器
        if (hideTaskReceiver != null) {
            unregisterReceiver(hideTaskReceiver);
        }
    }
}