package io.github.huidoudour.Installer.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射工具类
 */
public class ReflectionProvider {
    private final ConcurrentHashMap<String, Field> fieldCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Method> methodCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Constructor<?>> constructorCache = new ConcurrentHashMap<>();

    public Constructor<?> getDeclaredConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        String key = "decl:" + clazz.getName() + getParamTypesKey(parameterTypes);
        return constructorCache.computeIfAbsent(key, k -> {
            try {
                Constructor<?> c = clazz.getDeclaredConstructor(parameterTypes);
                c.setAccessible(true);
                return c;
            } catch (NoSuchMethodException e) {
                android.util.Log.w("ReflectionProvider", "Declared Constructor not found: " + clazz.getName());
                return null;
            }
        });
    }

    public Field getDeclaredField(String name, Class<?> clazz) {
        String key = "decl:" + clazz.getName() + "#" + name;
        return fieldCache.computeIfAbsent(key, k -> {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                android.util.Log.w("ReflectionProvider", "Declared Field not found: " + name + " in " + clazz.getName());
                return null;
            }
        });
    }

    public Method getDeclaredMethod(String name, Class<?> clazz, Class<?>... parameterTypes) {
        String key = "decl:" + clazz.getName() + "#" + name + getParamTypesKey(parameterTypes);
        return methodCache.computeIfAbsent(key, k -> {
            try {
                Method m = clazz.getDeclaredMethod(name, parameterTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                android.util.Log.w("ReflectionProvider", "Declared Method not found: " + name + " in " + clazz.getName());
                return null;
            }
        });
    }

    public Object getFieldValue(Object obj, String name, Class<?> clazz) {
        Field field = getDeclaredField(name, clazz);
        if (field != null) {
            try {
                return field.get(obj);
            } catch (IllegalAccessException e) {
                android.util.Log.e("ReflectionProvider", "Failed to get field: " + name, e);
            }
        }
        return null;
    }

    public void setFieldValue(Object obj, String name, Class<?> clazz, Object value) {
        Field field = getDeclaredField(name, clazz);
        if (field != null) {
            try {
                field.set(obj, value);
            } catch (IllegalAccessException e) {
                android.util.Log.e("ReflectionProvider", "Failed to set field: " + name, e);
            }
        }
    }

    public Object invokeMethod(Object obj, String name, Class<?> clazz, Class<?>[] parameterTypes, Object... args) {
        Method method = getDeclaredMethod(name, clazz, parameterTypes);
        if (method != null) {
            try {
                return method.invoke(obj, args);
            } catch (Exception e) {
                android.util.Log.e("ReflectionProvider", "Failed to invoke method: " + name, e);
            }
        }
        return null;
    }

    public Object invokeMethod(Object obj, String name, Class<?> clazz, Object... args) {
        return invokeMethod(obj, name, clazz, new Class<?>[0], args);
    }

    private String getParamTypesKey(Class<?>[] types) {
        StringBuilder sb = new StringBuilder("(");
        if (types != null) {
            for (Class<?> t : types) {
                sb.append(t != null ? t.getName() : "null").append(",");
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
