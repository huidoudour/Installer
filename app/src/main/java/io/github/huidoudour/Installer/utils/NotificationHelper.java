package io.github.huidoudour.Installer.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import io.github.huidoudour.Installer.R;

/**
 * 系统通知工具类
 * 用于发送系统级通知,替代Snackbar
 */
public class NotificationHelper {
    
    private static final String CHANNEL_ID_GENERAL = "installer_general";
    private static final String CHANNEL_ID_INSTALL = "installer_install";
    private static final String CHANNEL_NAME_GENERAL = "通用通知";
    private static final String CHANNEL_NAME_INSTALL = "安装通知";
    private static final int NOTIFICATION_ID_GENERAL = 1001;
    
    /**
     * 创建通知渠道 (Android 8.0+)
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            
            // 通用通知渠道
            NotificationChannel generalChannel = new NotificationChannel(
                CHANNEL_ID_GENERAL,
                CHANNEL_NAME_GENERAL,
                NotificationManager.IMPORTANCE_DEFAULT
            );
            generalChannel.setDescription("应用通用消息通知");
            generalChannel.enableVibration(true);
            notificationManager.createNotificationChannel(generalChannel);
            
            // 安装通知渠道
            NotificationChannel installChannel = new NotificationChannel(
                CHANNEL_ID_INSTALL,
                CHANNEL_NAME_INSTALL,
                NotificationManager.IMPORTANCE_HIGH
            );
            installChannel.setDescription("应用安装进度和结果通知");
            installChannel.enableVibration(true);
            notificationManager.createNotificationChannel(installChannel);
        }
    }
    
    /**
     * 显示通用通知
     * @param context 上下文
     * @param title 标题
     * @param message 消息内容
     */
    public static void showNotification(Context context, String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_notifications_24dp)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message));
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(NOTIFICATION_ID_GENERAL, builder.build());
    }
    
    /**
     * 显示通用通知(仅消息内容)
     * @param context 上下文
     * @param message 消息内容
     */
    public static void showNotification(Context context, String message) {
        showNotification(context, "Installer", message);
    }
    
    /**
     * 显示通用通知(使用字符串资源)
     * @param context 上下文
     * @param messageResId 消息字符串资源ID
     */
    public static void showNotification(Context context, int messageResId) {
        showNotification(context, context.getString(messageResId));
    }
    
    /**
     * 显示安装相关通知
     * @param context 上下文
     * @param title 标题
     * @param message 消息内容
     * @param isSuccess 是否为成功消息
     */
    public static void showInstallNotification(Context context, String title, String message, boolean isSuccess) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_INSTALL)
            .setSmallIcon(isSuccess ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message));
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(NOTIFICATION_ID_GENERAL + 1, builder.build());
    }
    
    /**
     * 检查通知权限是否已授予
     * @param context 上下文
     * @return 是否有通知权限
     */
    public static boolean isNotificationPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            return notificationManager.areNotificationsEnabled();
        }
        return true; // Android 13以下默认已授权
    }
    
    /**
     * 取消所有通知
     * @param context 上下文
     */
    public static void cancelAllNotifications(Context context) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancelAll();
    }
}
