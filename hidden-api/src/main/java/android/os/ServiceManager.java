package android.os;

/**
 * Hidden API stub for ServiceManager
 */
// 隐藏 API 桩，app 模块通过 compileOnly 引用（含反射），IDE 误报未使用
@SuppressWarnings("unused")
public final class ServiceManager {
    private ServiceManager() {}

    public static IBinder getService(String name) {
        throw new UnsupportedOperationException("Stub class, not implemented");
    }
}
