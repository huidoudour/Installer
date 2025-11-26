package io.github.huidoudour.Installer.ui.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import io.github.huidoudour.Installer.MeActivity;
import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_BACKGROUND_DISPLAY = "background_display";

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
            // 方法1：重新启动Activity以应用设置更改
            if (getActivity() != null) {
                // 显示提示信息
                String message = isEnabled ? "后台显示已启用，应用将出现在最近任务列表中" : 
                                           "后台显示已禁用，应用将不会出现在最近任务列表中";
                showSnackbar(message + "（重启应用后生效）");
                
                // 延迟重启应用以应用设置
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // 重新创建Activity以应用新的后台显示设置
                        if (getActivity() != null) {
                            getActivity().recreate();
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e("SettingsFragment", "应用后台显示设置失败: " + e.getMessage());
            showSnackbar("设置更改失败，请手动重启应用");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}