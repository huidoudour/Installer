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
import android.widget.Toast;

import io.github.huidoudour.Installer.ui.activity.HomeActivity;
import io.github.huidoudour.Installer.ui.activity.MeActivity;
import io.github.huidoudour.Installer.NativeTestActivity;
import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.databinding.FragmentSettingsBinding;
import io.github.huidoudour.Installer.utils.LanguageManager;
import io.github.huidoudour.Installer.utils.NotificationHelper;
import io.github.huidoudour.Installer.utils.PrivilegeHelper;
import io.github.huidoudour.Installer.utils.PrivilegeHelper.PrivilegeMode;
import io.github.huidoudour.Installer.utils.PrivilegeHelper.PrivilegeStatus;
import rikka.shizuku.Shizuku;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_NOTIFICATION_ENABLED = "notification_enabled";
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
        TextView tvNotificationStatus = binding.getRoot().findViewById(R.id.tv_notification_status);
        MaterialButton btnRequestNotification = binding.getRoot().findViewById(R.id.btn_request_notification);
        
        if (tvNotificationStatus != null && btnRequestNotification != null) {
            // 更新通知状态显示
            updateNotificationStatus();
            
            // 设置按钮点击事件
            btnRequestNotification.setOnClickListener(v -> {
                if (NotificationHelper.isNotificationPermissionGranted(requireContext())) {
                    showNotification(getString(R.string.notification_enabled));
                } else {
                    requestNotificationPermission();
                }
            });
        }
    }
    
    /**
     * 更新通知状态显示
     */
    private void updateNotificationStatus() {
        TextView tvNotificationStatus = binding.getRoot().findViewById(R.id.tv_notification_status);
        MaterialButton btnRequestNotification = binding.getRoot().findViewById(R.id.btn_request_notification);
        
        if (tvNotificationStatus != null && btnRequestNotification != null) {
            boolean isGranted = NotificationHelper.isNotificationPermissionGranted(requireContext());
            if (isGranted) {
                tvNotificationStatus.setText(R.string.notification_enabled);
                btnRequestNotification.setText(R.string.privilege_authorized_button);
                btnRequestNotification.setEnabled(false);
            } else {
                tvNotificationStatus.setText(R.string.notification_disabled);
                btnRequestNotification.setText(R.string.grant_permission);
                btnRequestNotification.setEnabled(true);
            }
        }
    }
    
    /**
     * 设置权限授权功能
     */
    private void setupPrivilegeSettings() {
        View privilegeSetting = binding.getRoot().findViewById(R.id.privilege_setting_layout);
        MaterialButton btnRequestPrivilege = binding.getRoot().findViewById(R.id.btn_request_privilege);
        
        if (privilegeSetting != null) {
            // 更新权限状态显示
            updatePrivilegeStatus();
            
            // 点击卡片打开选择对话框
            privilegeSetting.setOnClickListener(v -> showPrivilegeSettingsDialog());
        }
        
        if (btnRequestPrivilege != null) {
            // 点击按钮请求当前授权器权限
            btnRequestPrivilege.setOnClickListener(v -> {
                PrivilegeMode currentMode = PrivilegeHelper.getCurrentMode(requireContext());
                handlePrivilegeAction(currentMode);
            });
        }
    }
    
    /**
     * 更新权限状态显示
     */
    private void updatePrivilegeStatus() {
        TextView tvPrivilegeStatus = binding.getRoot().findViewById(R.id.tv_privilege_status);
        MaterialButton btnRequestPrivilege = binding.getRoot().findViewById(R.id.btn_request_privilege);
        
        if (tvPrivilegeStatus != null && btnRequestPrivilege != null) {
            PrivilegeMode currentMode = PrivilegeHelper.getCurrentMode(requireContext());
            PrivilegeStatus status = PrivilegeHelper.getStatus(requireContext(), currentMode);
            String modeName = PrivilegeHelper.getModeName(currentMode);
            
            // 更新状态文本
            tvPrivilegeStatus.setText(modeName + ": " + getStatusText(status));
            
            // 更新按钮文本和状态
            switch (status) {
                case NOT_INSTALLED:
                    btnRequestPrivilege.setText(getString(R.string.privilege_download_button, modeName));
                    btnRequestPrivilege.setEnabled(true);
                    break;
                case NOT_RUNNING:
                    btnRequestPrivilege.setText(getString(R.string.privilege_open_button, modeName));
                    btnRequestPrivilege.setEnabled(true);
                    break;
                case NOT_AUTHORIZED:
                case VERSION_TOO_LOW:
                    btnRequestPrivilege.setText(R.string.grant_permission);
                    btnRequestPrivilege.setEnabled(true);
                    break;
                case AUTHORIZED:
                    btnRequestPrivilege.setText(R.string.privilege_authorized_button);
                    btnRequestPrivilege.setEnabled(false);
                    break;
            }
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
                updateNotificationStatus();
                showNotification(getString(R.string.notification_opened));
            }
        } else {
            sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_ENABLED, true).apply();
            updateNotificationStatus();
            showNotification(getString(R.string.notification_opened));
        }
    }
    
    /**
     * 更新通知开关状态
     */
    private void updateNotificationSwitch(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply();
        updateNotificationStatus();
    }
    
    /**
     * 显示主题选择对话框
     */
    private void showThemeSelectionDialog() {
        // 创建MD3风格的AlertDialog
        MaterialAlertDialogBuilder alertBuilder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.theme_settings)
                .setSingleChoiceItems(new String[]{
                        "Follow System",  // getString(R.string.follow_system)
                        "Light Theme",  // getString(R.string.light_theme)
                        "Dark Theme"    // getString(R.string.dark_theme)
                    },
                    0, // 默认选中第一个
                    (dialog, which) -> {
                        showNotification(getString(R.string.theme_settings_developing));
                        dialog.dismiss();
                    })
                .setNegativeButton(R.string.cancel, null);
        
        alertBuilder.show();
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
        int currentIndex = currentMode == PrivilegeMode.SHIZUKU ? 0 : 1;
        
        // 获取两个授权器的状态
        PrivilegeStatus shizukuStatus = PrivilegeHelper.getStatus(requireContext(), PrivilegeMode.SHIZUKU);
        PrivilegeStatus dhizukuStatus = PrivilegeHelper.getStatus(requireContext(), PrivilegeMode.DHIZUKU);
        
        String shizukuDesc = "Shizuku (" + getStatusText(shizukuStatus) + ")";
        String dhizukuDesc = "Dhizuku (" + getStatusText(dhizukuStatus) + ")";
        
        String[] options = {shizukuDesc, dhizukuDesc};
        
        // 创建MD3风格的AlertDialog - 使用单选列表
        MaterialAlertDialogBuilder alertBuilder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.privilege_settings)
                .setSingleChoiceItems(options, currentIndex, (dialog, which) -> {
                    // 切换授权器
                    PrivilegeMode newMode = which == 0 ? PrivilegeMode.SHIZUKU : PrivilegeMode.DHIZUKU;
                    if (newMode != currentMode) {
                        PrivilegeHelper.saveCurrentMode(requireContext(), newMode);
                        showNotification(getString(R.string.switched_to_privilege, PrivilegeHelper.getModeName(newMode)));
                        // 更新权限状态显示
                        updatePrivilegeStatus();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null);
        
        alertBuilder.show();
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
                } else {
                    PrivilegeHelper.requestDhizukuPermission(requireContext());
                    showNotification(getString(R.string.requesting_dhizuku_auth_notification));
                }
                // 延迟更新状态
                if (getView() != null) {
                    getView().postDelayed(this::updatePrivilegeStatus, 1000);
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
        updateNotificationStatus();
        updatePrivilegeStatus();
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
}