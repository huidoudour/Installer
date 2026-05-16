package io.github.huidoudour.Installer.util

import java.lang.reflect.Method

/**
 * 反射提供者工具类
 */
object ReflectionProvider {

    /**
     * 查找类中的方法
     */
    fun findMethod(
        className: String,
        methodName: String,
        vararg parameterTypes: Class<*>
    ): Method? {
        return try {
            val clazz = Class.forName(className)
            clazz.getDeclaredMethod(methodName, *parameterTypes).apply {
                isAccessible = true
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 查找类中的所有方法
     */
    fun findMethods(className: String, methodName: String): List<Method> {
        return try {
            val clazz = Class.forName(className)
            clazz.declaredMethods.filter { it.name == methodName }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 调用方法
     */
    fun invokeMethod(method: Method, instance: Any?, vararg args: Any?): Any? {
        return try {
            method.invoke(instance, *args)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取 Shizuku 版本
     */
    fun getShizukuVersion(): Int {
        return try {
            val method = findMethod("rikka.shizuku.Shizuku", "getVersion")
            invokeMethod(method!!, null) as? Int ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 检查 Shizuku 是否为 Pre V11
     */
    fun isShizukuPreV11(): Boolean {
        return try {
            val method = findMethod("rikka.shizuku.Shizuku", "isPreV11")
            invokeMethod(method!!, null) as? Boolean ?: true
        } catch (e: Exception) {
            true
        }
    }
}
