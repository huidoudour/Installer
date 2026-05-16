package io.github.huidoudour.Installer.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/**
 * 本地意图接收器
 */
object LocalIntentReceiver {

    private const val TAG = "LocalIntentReceiver"

    /**
     * 注册本地广播接收器
     */
    fun register(
        context: Context,
        action: String,
        onReceive: (Intent) -> Unit
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let { onReceive(it) }
            }
        }

        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        return receiver
    }

    /**
     * 发送本地广播
     */
    fun sendBroadcast(context: Context, action: String, extras: Map<String, Any>? = null) {
        val intent = Intent(action)
        extras?.forEach { (key, value) ->
            when (value) {
                is String -> intent.putExtra(key, value)
                is Int -> intent.putExtra(key, value)
                is Boolean -> intent.putExtra(key, value)
                is Long -> intent.putExtra(key, value)
                is Float -> intent.putExtra(key, value)
                is Double -> intent.putExtra(key, value)
            }
        }
        context.sendBroadcast(intent)
    }

    /**
     * 取消注册广播接收器
     */
    fun unregister(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver: ${e.message}")
        }
    }

    // 预定义的动作
    object Actions {
        const val INSTALL_STARTED = "io.github.huidoudour.Installer.INSTALL_STARTED"
        const val INSTALL_PROGRESS = "io.github.huidoudour.Installer.INSTALL_PROGRESS"
        const val INSTALL_COMPLETED = "io.github.huidoudour.Installer.INSTALL_COMPLETED"
        const val INSTALL_FAILED = "io.github.huidoudour.Installer.INSTALL_FAILED"
        const val PRIVILEGE_STATUS_CHANGED = "io.github.huidoudour.Installer.PRIVILEGE_STATUS_CHANGED"
        const val THEME_CHANGED = "io.github.huidoudour.Installer.THEME_CHANGED"
    }

    // 预定义的额外参数
    object Extras {
        const val PACKAGE_NAME = "package_name"
        const val APP_NAME = "app_name"
        const val PROGRESS = "progress"
        const val ERROR_MESSAGE = "error_message"
        const val STATUS = "status"
    }
}
