package io.github.huidoudour.Installer.utils;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/**
 * 辅助功能帮助类
 * 参考GKD项目的实现思路，用于检查和管理辅助功能服务
 */
public class AccessibilityHelper {
    private static final String TAG = "AccessibilityHelper";
    
    /**
     * 检查指定包名的服务是否已启用
     * @param context 上下文
     * @param packageName 包名
     * @return true 如果服务已启用
     */
    public static boolean isAccessibilityServiceEnabled(Context context, String packageName) {
        try {
            // 获取已启用的辅助功能服务列表
            String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            
            if (!TextUtils.isEmpty(enabledServices)) {
                // 检查我们的服务是否在列表中
                TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                splitter.setString(enabledServices);
                
                while (splitter.hasNext()) {
                    String serviceComponent = splitter.next();
                    if (serviceComponent.contains(packageName)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查辅助功能服务状态失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 检查是否有其他辅助功能服务正在运行
     * 这对于实现类似GKD的隐藏功能很重要
     * @param context 上下文
     * @return true 如果有其他辅助功能服务正在运行
     */
    public static boolean hasOtherAccessibilityServices(Context context) {
        try {
            // 获取所有已启用的辅助功能服务信息
            AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            List<AccessibilityServiceInfo> services = 
                accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            
            String packageName = context.getPackageName();
            
            // 检查是否有其他应用的辅助功能服务在运行
            for (AccessibilityServiceInfo service : services) {
                if (service != null && service.getId() != null && 
                    !service.getId().contains(packageName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查其他辅助功能服务失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 获取辅助功能服务状态描述
     * @param context 上下文
     * @return 状态描述
     */
    public static String getAccessibilityStatusDescription(Context context) {
        try {
            boolean serviceEnabled = isAccessibilityServiceEnabled(context, context.getPackageName());
            boolean hasOtherServices = hasOtherAccessibilityServices(context);
            
            if (serviceEnabled && hasOtherServices) {
                return "辅助功能服务已启用，检测到其他辅助功能服务正在运行";
            } else if (serviceEnabled) {
                return "辅助功能服务已启用";
            } else if (hasOtherServices) {
                return "检测到其他辅助功能服务正在运行";
            } else {
                return "未检测到辅助功能服务";
            }
        } catch (Exception e) {
            Log.e(TAG, "获取辅助功能状态描述失败: " + e.getMessage());
            return "无法获取辅助功能状态";
        }
    }
}