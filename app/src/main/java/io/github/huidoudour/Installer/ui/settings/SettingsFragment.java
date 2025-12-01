package io.github.huidoudour.Installer.ui.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import io.github.huidoudour.Installer.HomeActivity;
import io.github.huidoudour.Installer.MeActivity;
import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.databinding.FragmentSettingsBinding;
import io.github.huidoudour.Installer.utils.LanguageManager;

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
            themeSetting.setOnClickListener(v -> showSnackbar(R.string.feature_developing));
        }

        // 语言设置点击事件 - 弹出选择框
        View languageSetting = binding.getRoot().findViewById(R.id.language_setting_layout);
        if (languageSetting != null) {
            updateCurrentLanguageDisplay();
            languageSetting.setOnClickListener(v -> showLanguageSelectionDialog());
        }

        // 通知设置点击事件
        View notificationSetting = binding.getRoot().findViewById(R.id.notification_setting_layout);
        if (notificationSetting != null) {
            notificationSetting.setOnClickListener(v -> showSnackbar(R.string.notification_settings_updated));
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
                                showSnackbar(getString(R.string.language_changed_tip, languages[which]));
                                
                                // 重启应用以使语言设置生效
                                dialog.dismiss();
                                restartApp();
                            } catch (Exception e) {
                                Log.e("SettingsFragment", "Language selection failed: " + e.getMessage());
                                showSnackbar(R.string.language_change_failed);
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
            showSnackbar(R.string.language_change_failed);
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
    
    private void showSnackbar(int stringResId) {
        if (getActivity() != null && binding.getRoot() != null) {
            Snackbar.make(binding.getRoot(), stringResId, Snackbar.LENGTH_SHORT).show();
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
                int messageResId = isEnabled ? R.string.background_display_enabled : R.string.background_display_disabled;
                showSnackbar(messageResId);
                
                // 保存设置到SharedPreferences
                sharedPreferences.edit().putBoolean(KEY_BACKGROUND_DISPLAY, isEnabled).apply();
                
                // 完全重启应用以应用设置更改
                // 通过启动新的Activity实例并关闭当前实例
                Intent intent = new Intent(getActivity(), HomeActivity.class);
                
                // 添加清除任务栈的标志，确保重新开始
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                
                // 根据设置状态添加相应的标志
                if (!isEnabled) {
                    // 如果禁用后台显示，设置任务属性以排除从最近任务列表
                    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                } else {
                    // 如果启用后台显示，确保不添加排除标志
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
            Log.e("SettingsFragment", getString(R.string.apply_background_display_setting_failed, e.getMessage()));
            showSnackbar(R.string.setting_change_failed);
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}