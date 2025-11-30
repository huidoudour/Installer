package io.github.huidoudour.Installer.ui.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import io.github.huidoudour.Installer.HomeActivity;
import io.github.huidoudour.Installer.MeActivity;
import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.databinding.FragmentSettingsBinding;
import io.github.huidoudour.Installer.utils.AccessibilityHelper;
import io.github.huidoudour.Installer.utils.BackgroundManager;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_BACKGROUND_DISPLAY = "background_display";
    private static final String KEY_HIDE_ON_PAUSE = "hide_on_pause"; // 新增：暂停时隐藏
    private static final String KEY_HIDE_ON_MINIMIZE = "hide_on_minimize"; // 新增：最小化时隐藏
    private static final String KEY_HIDE_IN_TARGET_APPS = "hide_in_target_apps"; // 新增：在特定应用中隐藏

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 初始化SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);

        setupAboutButton();
        setupSettingsItems();

        return root;
    }

    private void setupAboutButton() {
        binding.btnAbout.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MeActivity.class);
            startActivity(intent);
            
            // 添加动画效果
            if (getActivity() != null) {
                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        });
    }

    private void setupSettingsItems() {
        // 主题设置点击事件
        View themeSetting = binding.getRoot().findViewById(R.id.theme_setting_layout);
        if (themeSetting != null) {
            themeSetting.setOnClickListener(v -> showSnackbar("主题设置功能开发中"));
        }

        // 语言设置点击事件
        View languageSetting = binding.getRoot().findViewById(R.id.language_setting_layout);
        if (languageSetting != null) {
            languageSetting.setOnClickListener(v -> showSnackbar("语言设置功能开发中"));
        }

        // 通知设置点击事件
        View notificationSetting = binding.getRoot().findViewById(R.id.notification_setting_layout);
        if (notificationSetting != null) {
            notificationSetting.setOnClickListener(v -> showSnackbar("通知设置已更新"));
        }

        // 后台显示设置点击事件
        View backgroundDisplayLayout = binding.getRoot().findViewById(R.id.background_display_layout);
        SwitchMaterial switchBackgroundDisplay = binding.getRoot().findViewById(R.id.switchBackgroundDisplay);
        
        if (backgroundDisplayLayout != null && switchBackgroundDisplay != null) {
            // 从SharedPreferences加载保存的状态，默认为true（显示后台）
            boolean isBackgroundDisplayEnabled = sharedPreferences.getBoolean(KEY_BACKGROUND_DISPLAY, true);
            switchBackgroundDisplay.setChecked(isBackgroundDisplayEnabled);
            
            // 设置开关状态变化监听器
            switchBackgroundDisplay.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // 保存设置到SharedPreferences
                sharedPreferences.edit().putBoolean(KEY_BACKGROUND_DISPLAY, isChecked).apply();
                
                String message = isChecked ? "后台显示已开启" : "后台显示已关闭";
                showSnackbar(message);
                
                // 应用后台显示设置更改
                applyBackgroundDisplaySetting(isChecked);
            });
            
            // 整个布局的点击事件（点击任意位置切换开关）
            backgroundDisplayLayout.setOnClickListener(v -> {
                boolean currentState = switchBackgroundDisplay.isChecked();
                switchBackgroundDisplay.setChecked(!currentState);
            });
        }
        
        // 新增：暂停时隐藏设置
        View hideOnPauseLayout = binding.getRoot().findViewById(R.id.hide_on_pause_layout);
        SwitchMaterial switchHideOnPause = binding.getRoot().findViewById(R.id.switchHideOnPause);
        
        if (hideOnPauseLayout != null && switchHideOnPause != null) {
            boolean isHideOnPauseEnabled = sharedPreferences.getBoolean(KEY_HIDE_ON_PAUSE, false);
            switchHideOnPause.setChecked(isHideOnPauseEnabled);
            
            switchHideOnPause.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sharedPreferences.edit().putBoolean(KEY_HIDE_ON_PAUSE, isChecked).apply();
                String message = isChecked ? "暂停时隐藏已开启" : "暂停时隐藏已关闭";
                showSnackbar(message);
            });
            
            hideOnPauseLayout.setOnClickListener(v -> {
                boolean currentState = switchHideOnPause.isChecked();
                switchHideOnPause.setChecked(!currentState);
            });
        }
        
        // 新增：最小化时隐藏设置
        View hideOnMinimizeLayout = binding.getRoot().findViewById(R.id.hide_on_minimize_layout);
        SwitchMaterial switchHideOnMinimize = binding.getRoot().findViewById(R.id.switchHideOnMinimize);
        
        if (hideOnMinimizeLayout != null && switchHideOnMinimize != null) {
            boolean isHideOnMinimizeEnabled = sharedPreferences.getBoolean(KEY_HIDE_ON_MINIMIZE, false);
            switchHideOnMinimize.setChecked(isHideOnMinimizeEnabled);
            
            switchHideOnMinimize.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sharedPreferences.edit().putBoolean(KEY_HIDE_ON_MINIMIZE, isChecked).apply();
                String message = isChecked ? "最小化时隐藏已开启" : "最小化时隐藏已关闭";
                showSnackbar(message);
            });
            
            hideOnMinimizeLayout.setOnClickListener(v -> {
                boolean currentState = switchHideOnMinimize.isChecked();
                switchHideOnMinimize.setChecked(!currentState);
            });
        }
        
        // 新增：在特定应用中隐藏设置
        View hideInTargetAppsLayout = binding.getRoot().findViewById(R.id.hide_in_target_apps_layout);
        SwitchMaterial switchHideInTargetApps = binding.getRoot().findViewById(R.id.switchHideInTargetApps);
        
        if (hideInTargetAppsLayout != null && switchHideInTargetApps != null) {
            boolean isHideInTargetAppsEnabled = sharedPreferences.getBoolean(KEY_HIDE_IN_TARGET_APPS, false);
            switchHideInTargetApps.setChecked(isHideInTargetAppsEnabled);
            
            switchHideInTargetApps.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sharedPreferences.edit().putBoolean(KEY_HIDE_IN_TARGET_APPS, isChecked).apply();
                String message = isChecked ? "在特定应用中隐藏已开启" : "在特定应用中隐藏已关闭";
                showSnackbar(message);
            });
            
            hideInTargetAppsLayout.setOnClickListener(v -> {
                boolean currentState = switchHideInTargetApps.isChecked();
                switchHideInTargetApps.setChecked(!currentState);
            });
        }
        
        // 辅助功能状态设置
        View accessibilityStatusLayout = binding.getRoot().findViewById(R.id.accessibility_status_layout);
        TextView tvAccessibilityStatus = binding.getRoot().findViewById(R.id.tvAccessibilityStatus);
        
        if (accessibilityStatusLayout != null && tvAccessibilityStatus != null) {
            // 更新辅助功能状态显示
            updateAccessibilityStatus(tvAccessibilityStatus);
            
            // 点击刷新状态
            accessibilityStatusLayout.setOnClickListener(v -> {
                updateAccessibilityStatus(tvAccessibilityStatus);
                showSnackbar("辅助功能状态已刷新");
            });
        }
    }

    private void showSnackbar(String message) {
        if (getActivity() != null && binding.getRoot() != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
        }
    }

    /**
     * 应用后台显示设置更改
     * @param isEnabled 是否启用后台显示
     */
    private void applyBackgroundDisplaySetting(boolean isEnabled) {
        try {
            if (getActivity() != null) {
                // 显示提示信息
                String message = isEnabled ? "后台显示已启用，应用将出现在最近任务列表中" : 
                                           "后台显示已禁用，应用将不会出现在最近任务列表中";
                showSnackbar(message + "（重启应用后生效）");
                
                // 完全重启应用以应用设置更改
                // 通过启动新的Activity实例并关闭当前实例
                Intent intent = new Intent(getActivity(), HomeActivity.class);
                
                // 添加清除任务栈的标志，确保重新开始
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                
                // 根据设置状态添加相应的标志
                if (!isEnabled) {
                    // 如果禁用后台显示，添加排除最近任务的标志
                    // 这个标志必须在Activity启动前设置才有效
                    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                } else {
                    // 如果启用后台显示，确保不添加排除标志
                    // 清除任何可能存在的排除标志
                    intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                }
                
                // 延迟启动新的Activity
                getActivity().runOnUiThread(() -> {
                    startActivity(intent);
                    
                    // 关闭当前Activity
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                });
            }
        } catch (Exception e) {
            Log.e("SettingsFragment", "应用后台显示设置失败: " + e.getMessage());
            showSnackbar("设置更改失败，请手动重启应用");
        }
    }
    
    /**
     * 更新辅助功能状态显示
     * @param statusTextView 状态文本视图
     */
    private void updateAccessibilityStatus(TextView statusTextView) {
        if (statusTextView != null && getActivity() != null) {
            String status = AccessibilityHelper.getAccessibilityStatusDescription(getActivity());
            statusTextView.setText(status);
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}