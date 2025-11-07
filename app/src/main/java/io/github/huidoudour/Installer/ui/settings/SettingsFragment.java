package io.github.huidoudour.Installer.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import io.github.huidoudour.Installer.MeActivity;
import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

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
    }

    private void showSnackbar(String message) {
        if (getActivity() != null && binding.getRoot() != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}