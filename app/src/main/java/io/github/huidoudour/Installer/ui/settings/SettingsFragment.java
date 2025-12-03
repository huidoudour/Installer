package io.github.huidoudour.Installer.ui.settings;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import io.github.huidoudour.Installer.ui.activity.HomeActivity;
import io.github.huidoudour.Installer.ui.activity.MeActivity;
import io.github.huidoudour.Installer.NativeTestActivity;
import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.databinding.FragmentSettingsBinding;
import io.github.huidoudour.Installer.utils.LanguageManager;
import io.github.huidoudour.Installer.utils.NotificationHelper;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_NOTIFICATION_ENABLED = "notification_enabled";
    
    // 通知权限请求启动器
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 初始化SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        
        // 初始化通知权限启动器
        initNotificationPermissionLauncher();
        
        // 初始化通知渠道
        NotificationHelper.createNotificationChannels(requireContext());

        setupAboutButton();
        setupNativeTestButton();
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
    
    /**
     * 初始化通知权限请求启动器
     */
    private void initNotificationPermissionLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        showNotification("通知权限已授予");
                        updateNotificationSwitch(true);
                    } else {
                        showNotification("通知权限被拒绝，请在设置中手动开启");
                        updateNotificationSwitch(false);
                    }
                }
            );
        }
    }
    
    /**
     * 设置原生库测试按钮
     */
    private void setupNativeTestButton() {
        // 查找开发者选项或调试区域
        // 你可以在设置界面的XML布局中添加一个隐藏按钮,或者直接在这里创建
        // 临时方案: 长按"关于"按钮进入测试页面
        binding.btnAbout.setOnLongClickListener(v -> {
            Intent intent = new Intent(getActivity(), NativeTestActivity.class);
            startActivity(intent);
            showNotification("已打开原生库测试界面");
            return true; // 消费长按事件
        });
    }

    private void setupSettingsItems() {
        // 主题设置点击事件
        View themeSetting = binding.getRoot().findViewById(R.id.theme_setting_layout);
        if (themeSetting != null) {
            themeSetting.setOnClickListener(v -> showThemeSelectionDialog());
        }

        // 语言设置点击事件 - 弹出选择框
        View languageSetting = binding.getRoot().findViewById(R.id.language_setting_layout);
        if (languageSetting != null) {
            updateCurrentLanguageDisplay();
            languageSetting.setOnClickListener(v -> showLanguageSelectionDialog());
        }

        // 通知设置点击事件
        setupNotificationSettings();
    }
    
    /**
     * 更新当前语言显示
     */
    private void updateCurrentLanguageDisplay() {
        TextView tvCurrentLanguage = binding.getRoot().findViewById(R.id.tv_current_language);
        if (tvCurrentLanguage != null) {
            String currentLanguage = LanguageManager.getUserLanguage(requireContext());
            String displayName = currentLanguage.equals("zh") ? 
                getString(R.string.simplified_chinese) : getString(R.string.english);
            tvCurrentLanguage.setText(displayName);
        }
    }

    private void showLanguageSelectionDialog() {
        // 创建MD3风格的AlertDialog
        MaterialAlertDialogBuilder alertBuilder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.language_settings)
                .setSingleChoiceItems(new String[]{getString(R.string.simplified_chinese), getString(R.string.english)},
                        LanguageManager.getUserLanguage(requireContext()).equals("zh") ? 0 : 1,
                        (dialog, which) -> {
                            String[] languageCodes = {"zh", "en"};
                            String selectedLanguageCode = languageCodes[which];
                            String[] languages = {getString(R.string.simplified_chinese), getString(R.string.english)};
                            
                            try {
                                String currentLang = LanguageManager.getUserLanguage(requireContext());
                                if (currentLang.equals(selectedLanguageCode)) {
                                    dialog.dismiss();
                                    return;
                                }
                                
                                // 保存语言设置
                                LanguageManager.saveUserLanguage(requireContext(), selectedLanguageCode);
                                
                                // 更新应用语言
                                LanguageManager.applyUserLanguagePreference(requireContext());
                                
                                // 更新UI显示
                                updateCurrentLanguageDisplay();
                                
                                // 显示提示信息
                                showNotification(getString(R.string.language_changed_tip, languages[which]));
                                
                                // 重启应用以使语言设置生效
                                dialog.dismiss();
                                restartApp();
                            } catch (Exception e) {
                                Log.e("SettingsFragment", "Language selection failed: " + e.getMessage());
                                showNotification(R.string.language_change_failed);
                            }
                        })
                .setNegativeButton(R.string.cancel, null);
        
        alertBuilder.show();
    }

    private void changeLanguage(String languageCode) {
        try {
            // 保存语言设置
            LanguageManager.saveUserLanguage(requireContext(), languageCode);
            
            // 更新应用语言
            LanguageManager.applyUserLanguagePreference(requireContext());
            
            // 更新界面上显示的语言
            updateCurrentLanguageDisplay();
            
            // 重启应用以使语言设置生效
            restartApp();
        } catch (Exception e) {
            Log.e("SettingsFragment", getString(R.string.switch_language_failed, e.getMessage()));
            showNotification(R.string.language_change_failed);
        }
    }
    
    /**
     * 重启应用以使语言设置生效
     */
    private void restartApp() {
        try {
            // 延迟一小段时间后重启应用
            requireActivity().getWindow().getDecorView().postDelayed(() -> {
                Intent intent = requireActivity().getPackageManager()
                        .getLaunchIntentForPackage(requireActivity().getPackageName());
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    if (requireActivity() != null) {
                        requireActivity().finish();
                    }
                    // 杀死当前进程以确保完全重启
                    Runtime.getRuntime().exit(0);
                }
            }, 500); // 延迟500毫秒
        } catch (Exception e) {
            Log.e("SettingsFragment", getString(R.string.restart_app_failed, e.getMessage()));
        }
    }
    
    /**
     * 设置通知功能
     */
    private void setupNotificationSettings() {
        View notificationLayout = binding.getRoot().findViewById(R.id.notification_setting_layout);
        SwitchMaterial notificationSwitch = notificationLayout != null ? 
            (SwitchMaterial) ((ViewGroup)notificationLayout).getChildAt(2) : null;
        
        if (notificationLayout != null && notificationSwitch != null) {
            // 加载保存的状态
            boolean isEnabled = sharedPreferences.getBoolean(KEY_NOTIFICATION_ENABLED, true);
            notificationSwitch.setChecked(isEnabled && NotificationHelper.isNotificationPermissionGranted(requireContext()));
            
            // 设置开关状态变化监听器
            notificationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    requestNotificationPermission();
                } else {
                    sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_ENABLED, false).apply();
                    showNotification("通知已关闭");
                }
            });
            
            // 整个布局的点击事件
            notificationLayout.setOnClickListener(v -> {
                boolean currentState = notificationSwitch.isChecked();
                notificationSwitch.setChecked(!currentState);
            });
        }
    }
    
    /**
     * 请求通知权限
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), 
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_ENABLED, true).apply();
                showNotification("通知已开启");
            }
        } else {
            sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_ENABLED, true).apply();
            showNotification("通知已开启");
        }
    }
    
    /**
     * 更新通知开关状态
     */
    private void updateNotificationSwitch(boolean enabled) {
        View notificationLayout = binding.getRoot().findViewById(R.id.notification_setting_layout);
        if (notificationLayout != null) {
            SwitchMaterial notificationSwitch = (SwitchMaterial) ((ViewGroup)notificationLayout).getChildAt(2);
            notificationSwitch.setChecked(enabled);
        }
        sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply();
    }
    
    /**
     * 显示主题选择对话框
     */
    private void showThemeSelectionDialog() {
        // 创建MD3风格的AlertDialog
        MaterialAlertDialogBuilder alertBuilder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.theme_settings)
                .setSingleChoiceItems(new String[]{
                        "跟随系统", 
                        "浅色主题", 
                        "深色主题"
                    },
                    0, // 默认选中第一个
                    (dialog, which) -> {
                        showNotification("主题设置功能开发中");
                        dialog.dismiss();
                    })
                .setNegativeButton(R.string.cancel, null);
        
        alertBuilder.show();
    }
    
    /**
     * 显示通知 - 使用系统通知替代Snackbar
     */
    private void showNotification(int stringResId) {
        if (getActivity() != null) {
            NotificationHelper.showNotification(requireContext(), stringResId);
        }
    }

    private void showNotification(String message) {
        if (getActivity() != null) {
            NotificationHelper.showNotification(requireContext(), message);
        }
    }


    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}