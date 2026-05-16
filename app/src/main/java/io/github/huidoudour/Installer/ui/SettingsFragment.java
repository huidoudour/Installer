package io.github.huidoudour.Installer.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.databinding.FragmentSettingsBinding;
import io.github.huidoudour.Installer.util.LanguageManager;
import io.github.huidoudour.Installer.util.NotificationHelper;
import io.github.huidoudour.Installer.util.PrivilegeHelper;
import io.github.huidoudour.Installer.util.PrivilegeHelper.PrivilegeMode;
import io.github.huidoudour.Installer.util.PrivilegeHelper.PrivilegeStatus;
import io.github.huidoudour.Installer.util.ThemeManager;
import rikka.shizuku.Shizuku;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_NOTIFICATION_ENABLED = "notification_enabled";
    private static final String KEY_INSTALLER_PACKAGE = "installer_package"; // 安装请求者包名
    
    // 彩蛋相关
    private int versionClickCount = 0;
    private long lastClickTime = 0;
    private static final int EASTER_EGG_CLICK_COUNT = 6; // 菜单点击次数
    private static final long CLICK_TIME_WINDOW = 2000; // 2秒内完成点击
    
    // 预制的安装请求者包名列表
    private static final String CURRENT_APP_PACKAGE = "io.github.huidoudour.Installer"; // 当前应用包名（独立定义）
    private static final int REQUEST_CODE_SHIZUKU_PERMISSION = 1001;
    
    // Shizuku 权限监听器
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener = 
        (requestCode, grantResult) -> {
            if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
                showNotification("Shizuku " + getString(R.string.permission_granted_miao) + (grantResult == PackageManager.PERMISSION_GRANTED ? getString(R.string.success) : getString(R.string.failed)));
                // 更新权限状态显示
                updatePrivilegeStatus();
            }
        };
    
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
        setupInstallerPackageSetting(); // 初始化安装请求者设置

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
        
        // 初始化版本号点击事件（只在 onCreate 时设置一次）
        View versionInfoLayout = binding.getRoot().findViewById(R.id.version_info_layout);
        if (versionInfoLayout != null) {
            versionInfoLayout.setOnClickListener(v -> handleVersionClick());
        }
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
                        showNotification(getString(R.string.notification_permission_granted));
                        updateNotificationSwitch(true);
                    } else {
                        showNotification(getString(R.string.notification_permission_denied));
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
            // 使用 Toast 显示通知
            showNotification(getString(R.string.native_test_opened));
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

        // 通知设置按钮
        setupNotificationSettings();
        
        // 权限授权设置
        setupPrivilegeSettings();
        
        // 注册 Shizuku 权限监听器
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 更新当前语言显示
     */
    private void updateCurrentLanguageDisplay() {
        TextView tvCurrentLanguage = binding.getRoot().findViewById(R.id.tv_current_language);
        
        if (tvCurrentLanguage != null) {
            String currentLang = LanguageManager.getUserLanguage(requireContext());
            String langName = LanguageManager.getLanguageDisplayName(requireContext(), currentLang);
            tvCurrentLanguage.setText(langName);
            Log.d("SettingsFragment", "Updated language display to: " + langName);
        }
    }

    private void showLanguageSelectionDialog() {
        // 使用自定义Dialog布局
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_language, null);
        
        // 获取所有语言选项卡
        com.google.android.material.card.MaterialCardView cardSystem = dialogView.findViewById(R.id.language_system);
        com.google.android.material.card.MaterialCardView cardSimplified = dialogView.findViewById(R.id.language_chinese_simplified);
        com.google.android.material.card.MaterialCardView cardTraditional = dialogView.findViewById(R.id.language_chinese_traditional);
        com.google.android.material.card.MaterialCardView cardEnglish = dialogView.findViewById(R.id.language_english);
        com.google.android.material.card.MaterialCardView cardRussian = dialogView.findViewById(R.id.language_russian);
        com.google.android.material.card.MaterialCardView cardJapanese = dialogView.findViewById(R.id.language_japanese);
        com.google.android.material.card.MaterialCardView cardMeow = dialogView.findViewById(R.id.language_meow);
        
        // 获取所有RadioButton (不使用RadioGroup,直接手动管理单选状态)
        android.widget.RadioButton radioSystem = dialogView.findViewById(R.id.radio_system);
        android.widget.RadioButton radioSimplified = dialogView.findViewById(R.id.radio_chinese_simplified);
        android.widget.RadioButton radioTraditional = dialogView.findViewById(R.id.radio_chinese_traditional);
        android.widget.RadioButton radioEnglish = dialogView.findViewById(R.id.radio_english);
        android.widget.RadioButton radioRussian = dialogView.findViewById(R.id.radio_russian);
        android.widget.RadioButton radioJapanese = dialogView.findViewById(R.id.radio_japanese);
        android.widget.RadioButton radioMeow = dialogView.findViewById(R.id.radio_meow);
        
        // 获取当前语言并设置选中状态
        String currentLang = LanguageManager.getUserLanguage(requireContext());
        switch (currentLang) {
            case "system":
                radioSystem.setChecked(true);
                break;
            case "zh":
                radioSimplified.setChecked(true);
                break;
            case "zh-TW":
                radioTraditional.setChecked(true);
                break;
            case "en":
                radioEnglish.setChecked(true);
                break;
            case "ru":
                radioRussian.setChecked(true);
                break;
            case "ja":
                radioJapanese.setChecked(true);
                break;
            case "zh-HK":
                radioMeow.setChecked(true);
                break;
            default:
                // 未知语言，默认跟随系统
                radioSystem.setChecked(true);
                break;
        }
        
        // 禁用所有RadioButton的点击和焦点，让CardView完全控制
        radioSystem.setClickable(false);
        radioSystem.setFocusable(false);
        radioSimplified.setClickable(false);
        radioSimplified.setFocusable(false);
        radioTraditional.setClickable(false);
        radioTraditional.setFocusable(false);
        radioEnglish.setClickable(false);
        radioEnglish.setFocusable(false);
        radioRussian.setClickable(false);
        radioRussian.setFocusable(false);
        radioJapanese.setClickable(false);
        radioJapanese.setFocusable(false);
        radioMeow.setClickable(false);
        radioMeow.setFocusable(false);
        
        // 为每个卡片设置点击事件 (手动实现单选逻辑)
        final String[] selectedLanguage = {currentLang};
        
        View.OnClickListener languageClickListener = v -> {
            Log.d("SettingsFragment", "Card clicked: " + v.getId());
            
            // 清除所有RadioButton的选中状态
            radioSystem.setChecked(false);
            radioSimplified.setChecked(false);
            radioTraditional.setChecked(false);
            radioEnglish.setChecked(false);
            radioRussian.setChecked(false);
            radioJapanese.setChecked(false);
            radioMeow.setChecked(false);
            
            // 根据点击的卡片设置对应的RadioButton
            if (v == cardSystem) {
                radioSystem.setChecked(true);
                selectedLanguage[0] = "system";
                Log.d("SettingsFragment", "Language selected: system");
            } else if (v == cardSimplified) {
                radioSimplified.setChecked(true);
                selectedLanguage[0] = "zh";
                Log.d("SettingsFragment", "Language selected: zh");
            } else if (v == cardTraditional) {
                radioTraditional.setChecked(true);
                selectedLanguage[0] = "zh-TW";
                Log.d("SettingsFragment", "Language selected: zh-TW");
            } else if (v == cardEnglish) {
                radioEnglish.setChecked(true);
                selectedLanguage[0] = "en";
                Log.d("SettingsFragment", "Language selected: en");
            } else if (v == cardRussian) {
                radioRussian.setChecked(true);
                selectedLanguage[0] = "ru";
                Log.d("SettingsFragment", "Language selected: ru");
            } else if (v == cardJapanese) {
                radioJapanese.setChecked(true);
                selectedLanguage[0] = "ja";
                Log.d("SettingsFragment", "Language selected: ja");
            } else if (v == cardMeow) {
                radioMeow.setChecked(true);
                selectedLanguage[0] = "zh-HK";
                Log.d("SettingsFragment", "Language selected: zh-HK");
            }
            
            Log.d("SettingsFragment", "selectedLanguage updated to: " + selectedLanguage[0]);
        };
        
        cardSystem.setOnClickListener(languageClickListener);
        cardSimplified.setOnClickListener(languageClickListener);
        cardTraditional.setOnClickListener(languageClickListener);
        cardEnglish.setOnClickListener(languageClickListener);
        cardRussian.setOnClickListener(languageClickListener);
        cardJapanese.setOnClickListener(languageClickListener);
        cardMeow.setOnClickListener(languageClickListener);
        
        // 创建Material Dialog - 透明背景以显示圆角
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setBackgroundInsetStart(0)
                .setBackgroundInsetEnd(0)
                .setBackgroundInsetTop(0)
                .setBackgroundInsetBottom(0);
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setCanceledOnTouchOutside(true); // 点击外部区域关闭
        dialog.show();
        
        // 设置按钮点击事件
        dialogView.findViewById(R.id.btn_language_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_language_confirm).setOnClickListener(v -> {
            try {
                String langCode = selectedLanguage[0];
                Log.d("SettingsFragment", "=== Confirm Button Clicked ===");
                Log.d("SettingsFragment", "selectedLanguage[0]: " + langCode);
                Log.d("SettingsFragment", "currentLang (initial): " + currentLang);
                Log.d("SettingsFragment", "Language confirm clicked, selected: " + langCode + ", current: " + currentLang);
                
                // 保存语言设置
                LanguageManager.saveUserLanguage(requireContext(), langCode);
                Log.d("SettingsFragment", "Language saved: " + langCode);
                
                // 更新应用语言（无需重启）
                LanguageManager.applyUserLanguagePreference(requireContext());
                Log.d("SettingsFragment", "Language applied");
                
                // 立即更新界面上的语言显示（在 recreate 之前）
                updateCurrentLanguageDisplay();
                Log.d("SettingsFragment", "Language display updated before recreate");
                
                // 显示提示信息
                String langName = LanguageManager.getLanguageDisplayName(requireContext(), langCode);
                showNotification(getString(R.string.language_changed_tip, langName));
                Log.d("SettingsFragment", "Showing notification for language change to: " + langName);
                
                // 重新创建Activity以应用语言更改（比完全重启更轻量）
                dialog.dismiss();
                Log.d("SettingsFragment", "Recreating activity...");
                requireActivity().recreate();
            } catch (Exception e) {
                Log.e("SettingsFragment", "Language selection failed: " + e.getMessage(), e);
                showNotification(R.string.language_change_failed);
            }
        });
    }

    private void changeLanguage(String languageCode) {
        try {
            // 保存语言设置
            LanguageManager.saveUserLanguage(requireContext(), languageCode);
            
            // 更新应用语言
            LanguageManager.applyUserLanguagePreference(requireContext());
            
            // 更新界面上显示的语言
            updateCurrentLanguageDisplay();
            
            // 重新创建Activity以应用语言更改（比完全重启更轻量）
            requireActivity().recreate();
        } catch (Exception e) {
            Log.e("SettingsFragment", getString(R.string.switch_language_failed, e.getMessage()));
            showNotification(R.string.language_change_failed);
        }
    }
    
    /**
     * 设置通知功能
     */
    private void setupNotificationSettings() {
        View notificationSetting = binding.getRoot().findViewById(R.id.notification_setting_layout);
        
        if (notificationSetting != null) {
            // 点击卡片打开对话框
            notificationSetting.setOnClickListener(v -> showNotificationPermissionDialog());
        }
    }
    
    /**
     * 显示通知权限对话框
     */
    private void showNotificationPermissionDialog() {
        boolean isGranted = NotificationHelper.isNotificationPermissionGranted(requireContext());
        
        MaterialAlertDialogBuilder alertBuilder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.notification_settings);
        
        if (isGranted) {
            // 已授权，显示锁定状态
            alertBuilder.setMessage(R.string.notification_permission_granted)
                    .setPositiveButton(R.string.ok, null);
        } else {
            // 未授权，显示请求权限按钮
            alertBuilder.setMessage(R.string.notification_permission_required)
                    .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
                        requestNotificationPermission();
                    })
                    .setNegativeButton(R.string.cancel, null);
        }
        
        androidx.appcompat.app.AlertDialog dialog = alertBuilder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }
    
    /**
     * 设置权限授权功能
     */
    private void setupPrivilegeSettings() {
        View privilegeSetting = binding.getRoot().findViewById(R.id.privilege_setting_layout);
        
        if (privilegeSetting != null) {
            // 更新权限状态显示
            updatePrivilegeStatus();
            
            // 点击卡片打开选择对话框
            privilegeSetting.setOnClickListener(v -> showPrivilegeSettingsDialog());
        }
    }
    
    /**
     * 更新权限状态显示
     */
    private void updatePrivilegeStatus() {
        TextView tvPrivilegeStatus = binding.getRoot().findViewById(R.id.tv_privilege_status);
        
        if (tvPrivilegeStatus != null) {
            PrivilegeMode currentMode = PrivilegeHelper.getCurrentMode(requireContext());
            PrivilegeStatus status = PrivilegeHelper.getStatus(requireContext(), currentMode);
            String modeName = PrivilegeHelper.getModeName(currentMode);
            
            // 更新状态文本
            tvPrivilegeStatus.setText(modeName + ": " + getStatusText(status));
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
                showNotification(getString(R.string.notification_opened));
            }
        } else {
            sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_ENABLED, true).apply();
            showNotification(getString(R.string.notification_opened));
        }
    }
    
    /**
     * 更新通知开关状态
     */
    private void updateNotificationSwitch(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply();
    }
    
    /**
     * 显示主题选择对话框
     */
    private void showThemeSelectionDialog() {
        // 使用自定义Dialog布局
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_theme, null);
            
        // 获取所有选项卡
        com.google.android.material.card.MaterialCardView cardSystem = dialogView.findViewById(R.id.theme_system);
        com.google.android.material.card.MaterialCardView cardLight = dialogView.findViewById(R.id.theme_light);
        com.google.android.material.card.MaterialCardView cardDark = dialogView.findViewById(R.id.theme_dark);
            
        // 获取RadioButton
        android.widget.RadioButton radioSystem = dialogView.findViewById(R.id.radio_system);
        android.widget.RadioButton radioLight = dialogView.findViewById(R.id.radio_light);
        android.widget.RadioButton radioDark = dialogView.findViewById(R.id.radio_dark);
            
        // 获取当前主题并设置选中状态
        int currentTheme = ThemeManager.getUserTheme(requireContext());
        switch (currentTheme) {
            case ThemeManager.THEME_LIGHT:
                radioLight.setChecked(true);
                break;
            case ThemeManager.THEME_DARK:
                radioDark.setChecked(true);
                break;
            case ThemeManager.THEME_FOLLOW_SYSTEM:
            default:
                radioSystem.setChecked(true);
                break;
        }
            
        // 为每个卡片设置点击事件
        final int[] selectedTheme = {currentTheme};
            
        View.OnClickListener themeClickListener = v -> {
            // 清除所有RadioButton的选中状态
            radioSystem.setChecked(false);
            radioLight.setChecked(false);
            radioDark.setChecked(false);
                
            // 根据点击的卡片设置对应的RadioButton
            if (v == cardSystem) {
                radioSystem.setChecked(true);
                selectedTheme[0] = ThemeManager.THEME_FOLLOW_SYSTEM;
            } else if (v == cardLight) {
                radioLight.setChecked(true);
                selectedTheme[0] = ThemeManager.THEME_LIGHT;
            } else if (v == cardDark) {
                radioDark.setChecked(true);
                selectedTheme[0] = ThemeManager.THEME_DARK;
            }
        };
            
        cardSystem.setOnClickListener(themeClickListener);
        cardLight.setOnClickListener(themeClickListener);
        cardDark.setOnClickListener(themeClickListener);
            
        // 创建Material Dialog
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setBackgroundInsetStart(0)
                .setBackgroundInsetEnd(0)
                .setBackgroundInsetTop(0)
                .setBackgroundInsetBottom(0);
            
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setCanceledOnTouchOutside(true); // 点击外部区域关闭
        dialog.show();
            
        // 设置按钮点击事件
        dialogView.findViewById(R.id.btn_theme_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_theme_confirm).setOnClickListener(v -> {
            try {
                int themeMode = selectedTheme[0];
                Log.d("SettingsFragment", "Theme confirm clicked, selected: " + themeMode + ", current: " + currentTheme);
                    
                // 如果选择的与当前相同，直接关闭
                if (themeMode == currentTheme) {
                    Log.d("SettingsFragment", "Theme unchanged, dismissing dialog");
                    dialog.dismiss();
                    return;
                }
                    
                // 保存主题设置
                ThemeManager.saveUserTheme(requireContext(), themeMode);
                Log.d("SettingsFragment", "Theme saved: " + themeMode);
                    
                // 应用主题（无需重启）
                ThemeManager.applyTheme(themeMode);
                Log.d("SettingsFragment", "Theme applied");
                    
                // 显示提示信息
                String themeName = ThemeManager.getThemeDisplayName(requireContext(), themeMode);
                showNotification(getString(R.string.theme_changed_tip, themeName));
                Log.d("SettingsFragment", "Showing notification for theme change to: " + themeName);
                    
                dialog.dismiss();
            } catch (Exception e) {
                Log.e("SettingsFragment", "Theme selection failed: " + e.getMessage(), e);
                showNotification(R.string.feature_developing);
            }
        });
    }
    
    /**
     * 显示通知 - 使用 Toast 替代系统通知
     */
    private void showNotification(int stringResId) {
        if (getActivity() != null) {
            Toast.makeText(getActivity(), stringResId, Toast.LENGTH_SHORT).show();
        }
    }

    private void showNotification(String message) {
        if (getActivity() != null) {
            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示权限授权设置对话框
     */
    private void showPrivilegeSettingsDialog() {
        // 获取当前授权器
        PrivilegeMode currentMode = PrivilegeHelper.getCurrentMode(requireContext());
        
        // 获取 Shizuku 和 Dhizuku 状态
        PrivilegeStatus shizukuStatus = PrivilegeHelper.getStatus(requireContext(), PrivilegeMode.SHIZUKU);
        PrivilegeStatus dhizukuStatus = PrivilegeHelper.getStatus(requireContext(), PrivilegeMode.DHIZUKU);
        
        String shizukuDesc = "Shizuku (" + getStatusText(shizukuStatus) + ")";
        String dhizukuDesc = "Dhizuku (" + getStatusText(dhizukuStatus) + ")";
        
        String[] options = {shizukuDesc, dhizukuDesc};
        
        // 确定当前选中的项
        int currentIndex = (currentMode == PrivilegeMode.DHIZUKU) ? 1 : 0;
        
        // 创建MD3风格的AlertDialog - 使用单选列表
        MaterialAlertDialogBuilder alertBuilder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.privilege_settings)
                .setSingleChoiceItems(options, currentIndex, (dialog, which) -> {
                    // 根据选择切换授权器
                    PrivilegeMode selectedMode = (which == 0) ? PrivilegeMode.SHIZUKU : PrivilegeMode.DHIZUKU;
                    if (selectedMode != currentMode) {
                        PrivilegeHelper.saveCurrentMode(requireContext(), selectedMode);
                        updatePrivilegeStatus();
                        String modeName = PrivilegeHelper.getModeName(selectedMode);
                        showNotification(getString(R.string.switched_to_privilege, modeName));
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null);
        
        androidx.appcompat.app.AlertDialog dialog = alertBuilder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }
    
    /**
     * 获取状态文本
     */
    private String getStatusText(PrivilegeStatus status) {
        switch (status) {
            case NOT_INSTALLED:
                return getString(R.string.privilege_not_installed);
            case NOT_RUNNING:
                return getString(R.string.privilege_not_running);
            case NOT_AUTHORIZED:
                return getString(R.string.privilege_not_authorized);
            case AUTHORIZED:
                return getString(R.string.privilege_authorized);
            case VERSION_TOO_LOW:
                return getString(R.string.privilege_version_too_low);
            default:
                return getString(R.string.status_unknown);
        }
    }
    
    /**
     * 处理权限授权操作
     */
    private void handlePrivilegeAction(PrivilegeMode mode) {
        PrivilegeStatus status = PrivilegeHelper.getStatus(requireContext(), mode);
        String modeName = PrivilegeHelper.getModeName(mode);
        
        switch (status) {
            case NOT_INSTALLED:
                // 未安装，跳转到 GitHub 下载页面
                PrivilegeHelper.openGithubPage(requireContext(), mode);
                showNotification(getString(R.string.privilege_not_installed_notification, modeName));
                break;
            case NOT_RUNNING:
                // 未运行，打开应用
                PrivilegeHelper.openPrivilegeApp(requireContext(), mode);
                showNotification(getString(R.string.privilege_app_opening_notification, modeName));
                break;
            case NOT_AUTHORIZED:
            case VERSION_TOO_LOW:
                // 未授权或版本过低，请求授权
                if (mode == PrivilegeMode.SHIZUKU) {
                    PrivilegeHelper.requestShizukuPermission(REQUEST_CODE_SHIZUKU_PERMISSION);
                    showNotification(getString(R.string.requesting_shizuku_auth));
                } else if (mode == PrivilegeMode.DHIZUKU) {
                    // 使用带 context 的方法，确保 Dhizuku.init() 正确初始化
                    PrivilegeHelper.requestDhizukuPermission(requireContext());
                }
                
                // 延迟更新状态
                if (getView() != null) {
                    getView().postDelayed(this::updatePrivilegeStatus, 2000);
                }
                break;
            case AUTHORIZED:
                showNotification(getString(R.string.privilege_already_authorized, modeName));
                break;
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 页面恢复时更新状态
        updatePrivilegeStatus();
        updateVersionInfo();
    }
    
    /**
     * 更新应用版本信息
     */
    private void updateVersionInfo() {
        TextView tvAppVersion = binding.getRoot().findViewById(R.id.tv_app_version);
        if (tvAppVersion != null) {
            try {
                android.content.pm.PackageInfo packageInfo = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
                String versionName = packageInfo.versionName;
                int versionCode = packageInfo.versionCode;
                tvAppVersion.setText("v" + versionName + " (" + versionCode + ")");
            } catch (Exception e) {
                Log.e("SettingsFragment", "Error getting version info: " + e.getMessage());
                // 出错时显示一个默认值
                tvAppVersion.setText("v1.0.0 (1)");
            }
        }
    }
    
    /**
     * 处理版本号点击事件
     */
    private void handleVersionClick() {
        long currentTime = System.currentTimeMillis();
        
        // 检查是否在时间窗口内
        if (currentTime - lastClickTime > CLICK_TIME_WINDOW) {
            // 超出时间窗口，重置计数
            versionClickCount = 1;
        } else {
            // 在时间窗口内，增加计数
            versionClickCount++;
        }
        
        // 更新最后点击时间
        lastClickTime = currentTime;
        
        // 检查是否达到触发条件
        if (versionClickCount >= EASTER_EGG_CLICK_COUNT) {
            // 触发彩蛋
            showNotification("啥也没有呀");
            // 重置计数
            versionClickCount = 0;
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 移除 Shizuku 权限监听器
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
        binding = null;
    }
    
    /**
     * 设置安装请求者包名
     */
    private void setupInstallerPackageSetting() {
        View installerPackageSetting = binding.getRoot().findViewById(R.id.installer_package_setting_layout);
        
        if (installerPackageSetting != null) {
            // 点击卡片打开选择对话框
            installerPackageSetting.setOnClickListener(v -> showInstallerPackageSelectionDialog());
        }
    }
    
    /**
     * 更新当前安装请求者的显示
     */
    private void updateCurrentInstallerPackageDisplay(TextView textView) {
        // 已简化UI，不再在卡片上显示当前包名
    }
    
    /**
     * 获取当前设置的安装请求者包名
     */
    private String getInstallerPackage() {
        return sharedPreferences.getString(KEY_INSTALLER_PACKAGE, "");
    }
    
    /**
     * 显示安装请求者选择对话框（静态方法，可从其他 Fragment 调用）
     */
    public static void showInstallerPackageSelectionDialog(Context context, Activity activity) {
        android.content.SharedPreferences sharedPreferences = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        String currentPackage = sharedPreferences.getString(KEY_INSTALLER_PACKAGE, "");
        int currentIndex = -1;
        
        // 使用独立定义的包名，而不是动态获取
        String[] options = {
            CURRENT_APP_PACKAGE + " (" + context.getString(R.string.current_app) + ")",
            "me.huidoudour.core",
            "io.github.huidoudour.zjs"
        };
        
        // 确定当前选中的项
        if (currentPackage.isEmpty() || currentPackage.equals(context.getPackageName())) {
            currentIndex = 0; // 默认使用当前应用包名
        } else if (currentPackage.equals("me.huidoudour.core")) {
            currentIndex = 1;
        } else if (currentPackage.equals("io.github.huidoudour.zjs")) {
            currentIndex = 2;
        } else if (currentPackage.equals(CURRENT_APP_PACKAGE)) {
            currentIndex = 0; // 如果保存的是独立定义的包名，也选中第一项
        }
        
        // 创建 MD3 风格的 AlertDialog
        androidx.appcompat.app.AlertDialog.Builder alertBuilder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.installer_package_settings)
                .setSingleChoiceItems(options, currentIndex, (dialog, which) -> {
                    String selectedPackage;
                    
                    switch (which) {
                        case 0:
                            // 使用独立定义的当前应用包名
                            selectedPackage = CURRENT_APP_PACKAGE;
                            break;
                        case 1:
                            selectedPackage = "me.huidoudour.core";
                            break;
                        case 2:
                            selectedPackage = "io.github.huidoudour.zjs";
                            break;
                        default:
                            selectedPackage = CURRENT_APP_PACKAGE;
                    }
                    
                    // 保存选择
                    sharedPreferences.edit().putString(KEY_INSTALLER_PACKAGE, selectedPackage).apply();
                    
                    // 显示提示信息
                    android.widget.Toast.makeText(context, context.getString(R.string.installer_package_changed), android.widget.Toast.LENGTH_SHORT).show();
                    
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null);
        
        if (activity != null && !activity.isFinishing()) {
            androidx.appcompat.app.AlertDialog dialog = alertBuilder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        }
    }
    
    /**
     * 显示安装请求者选择对话框
     */
    private void showInstallerPackageSelectionDialog() {
        String currentPackage = getInstallerPackage();
        int currentIndex = -1;
        
        // 使用独立定义的包名，而不是动态获取
        String[] options = {
            CURRENT_APP_PACKAGE + " (" + getString(R.string.current_app) + ")",
            "me.huidoudour.core",
            "io.github.huidoudour.zjs"
        };
        
        // 确定当前选中的项
        if (currentPackage.isEmpty() || currentPackage.equals(requireContext().getPackageName())) {
            currentIndex = 0; // 默认使用当前应用包名
        } else if (currentPackage.equals("me.huidoudour.core")) {
            currentIndex = 1;
        } else if (currentPackage.equals("io.github.huidoudour.zjs")) {
            currentIndex = 2;
        } else if (currentPackage.equals(CURRENT_APP_PACKAGE)) {
            currentIndex = 0; // 如果保存的是独立定义的包名，也选中第一项
        }
        
        // 创建 MD3 风格的 AlertDialog
        MaterialAlertDialogBuilder alertBuilder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.installer_package_settings)
                .setSingleChoiceItems(options, currentIndex, (dialog, which) -> {
                    String selectedPackage;
                    
                    switch (which) {
                        case 0:
                            // 使用独立定义的当前应用包名
                            selectedPackage = CURRENT_APP_PACKAGE;
                            break;
                        case 1:
                            selectedPackage = "me.huidoudour.core";
                            break;
                        case 2:
                            selectedPackage = "io.github.huidoudour.zjs";
                            break;
                        default:
                            selectedPackage = CURRENT_APP_PACKAGE;
                    }
                    
                    // 保存选择
                    sharedPreferences.edit().putString(KEY_INSTALLER_PACKAGE, selectedPackage).apply();
                    
                    // 更新显示
                    // 无需更新UI显示，对话框关闭后会自动刷新
                    
                    // 显示提示信息
                    showNotification(getString(R.string.installer_package_changed));
                    
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null);
        
        androidx.appcompat.app.AlertDialog dialog = alertBuilder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }
}
