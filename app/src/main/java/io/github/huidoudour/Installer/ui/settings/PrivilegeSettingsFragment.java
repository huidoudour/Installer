package io.github.huidoudour.Installer.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.utils.NotificationHelper;
import io.github.huidoudour.Installer.utils.PrivilegeHelper;
import io.github.huidoudour.Installer.utils.PrivilegeHelper.PrivilegeMode;
import io.github.huidoudour.Installer.utils.PrivilegeHelper.PrivilegeStatus;
import rikka.shizuku.Shizuku;

/**
 * 权限授权设置页面
 */
public class PrivilegeSettingsFragment extends Fragment {
    
    private static final int REQUEST_CODE_SHIZUKU_PERMISSION = 1001;
    
    private TextView tvShizukuStatus;
    private TextView tvDhizukuStatus;
    private MaterialButton btnShizukuAction;
    private MaterialButton btnDhizukuAction;
    
    // Shizuku 权限监听器
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener = 
        (requestCode, grantResult) -> {
            if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
                updatePrivilegeStatus();
            }
        };
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_privilege_settings, container, false);
        
        // 初始化视图
        tvShizukuStatus = root.findViewById(R.id.tv_shizuku_status);
        tvDhizukuStatus = root.findViewById(R.id.tv_dhizuku_status);
        btnShizukuAction = root.findViewById(R.id.btn_shizuku_action);
        btnDhizukuAction = root.findViewById(R.id.btn_dhizuku_action);
        
        // 注册 Shizuku 权限监听器
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 设置点击事件
        if (btnShizukuAction != null) {
            btnShizukuAction.setOnClickListener(v -> handlePrivilegeAction(PrivilegeMode.SHIZUKU));
        }
        
        if (btnDhizukuAction != null) {
            btnDhizukuAction.setOnClickListener(v -> handlePrivilegeAction(PrivilegeMode.DHIZUKU));
        }
        
        // 初始更新状态
        updatePrivilegeStatus();
        
        return root;
    }
    
    /**
     * 更新权限授权状态
     */
    private void updatePrivilegeStatus() {
        if (getActivity() == null) return;
        
        // 更新 Shizuku 状态
        if (tvShizukuStatus != null && btnShizukuAction != null) {
            PrivilegeStatus shizukuStatus = PrivilegeHelper.getStatus(requireContext(), PrivilegeMode.SHIZUKU);
            updateStatusUI(tvShizukuStatus, btnShizukuAction, shizukuStatus, PrivilegeMode.SHIZUKU);
        }
        
        // 更新 Dhizuku 状态
        if (tvDhizukuStatus != null && btnDhizukuAction != null) {
            PrivilegeStatus dhizukuStatus = PrivilegeHelper.getStatus(requireContext(), PrivilegeMode.DHIZUKU);
            updateStatusUI(tvDhizukuStatus, btnDhizukuAction, dhizukuStatus, PrivilegeMode.DHIZUKU);
        }
    }
    
    /**
     * 更新状态UI
     */
    private void updateStatusUI(TextView statusView, MaterialButton actionButton, 
                                PrivilegeStatus status, PrivilegeMode mode) {
        String statusText = getString(R.string.privilege_status, 
            getStatusString(status));
        statusView.setText(statusText);
        
        // 根据状态设置按钮文本和颜色
        switch (status) {
            case NOT_INSTALLED:
                actionButton.setText(R.string.download_from_github);
                actionButton.setEnabled(true);
                statusView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                break;
            case NOT_RUNNING:
                actionButton.setText(R.string.open_app);
                actionButton.setEnabled(true);
                statusView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                break;
            case NOT_AUTHORIZED:
                actionButton.setText(R.string.grant_permission);
                actionButton.setEnabled(true);
                statusView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                break;
            case AUTHORIZED:
                actionButton.setText(R.string.privilege_authorized);
                actionButton.setEnabled(false);
                statusView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                break;
            case VERSION_TOO_LOW:
                actionButton.setText(R.string.download_from_github);
                actionButton.setEnabled(true);
                statusView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                break;
        }
    }
    
    /**
     * 获取状态字符串
     */
    private String getStatusString(PrivilegeStatus status) {
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
        String modeName = mode == PrivilegeMode.SHIZUKU ? "Shizuku" : "Dhizuku";
        
        switch (status) {
            case NOT_INSTALLED:
                // 未安装，跳转到 GitHub 下载页面
                PrivilegeHelper.openGithubPage(requireContext(), mode);
                showNotification(modeName + " 未安装，正在打开 GitHub 下载页面...");
                break;
            case NOT_RUNNING:
                // 未运行，打开应用
                PrivilegeHelper.openPrivilegeApp(requireContext(), mode);
                showNotification("正在打开 " + modeName + " 应用...");
                break;
            case NOT_AUTHORIZED:
            case VERSION_TOO_LOW:
                // 未授权或版本过低，请求授权
                if (mode == PrivilegeMode.SHIZUKU) {
                    PrivilegeHelper.requestShizukuPermission(REQUEST_CODE_SHIZUKU_PERMISSION);
                    showNotification("正在请求 Shizuku 授权...");
                } else {
                    PrivilegeHelper.requestDhizukuPermission();
                    showNotification("正在请求 Dhizuku 授权...");
                }
                // 延迟更新状态
                if (getView() != null) {
                    getView().postDelayed(this::updatePrivilegeStatus, 1000);
                }
                break;
            case AUTHORIZED:
                showNotification(modeName + " 已授权");
                break;
        }
    }
    
    /**
     * 显示通知
     */
    private void showNotification(String message) {
        if (getActivity() != null) {
            NotificationHelper.showNotification(requireContext(), message);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 页面恢复时更新状态
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
    }
}
