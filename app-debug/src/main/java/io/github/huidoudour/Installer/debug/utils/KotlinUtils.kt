package io.github.huidoudour.Installer.debug.utils

import android.content.Context
import android.widget.Toast

/**
 * Kotlin工具类，提供一些常用的工具函数
 */
object KotlinUtils {

    /**
     * 显示Toast消息的扩展函数
     */
    fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    /**
     * 计算两个数的和
     */
    fun addNumbers(a: Int, b: Int): Int {
        return a + b
    }

    /**
     * 获取当前时间戳
     */
    fun getCurrentTimestamp(): Long {
        return System.currentTimeMillis()
    }

    /**
     * 检查字符串是否为空或空白
     */
    fun String?.isNullOrEmptyOrBlank(): Boolean {
        return this.isNullOrBlank()
    }
}