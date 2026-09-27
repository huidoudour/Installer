package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

// 隐藏 API 桩，app 模块通过 compileOnly 引用（含反射），IDE 误报未使用
@SuppressWarnings("unused")
public interface IPackageManager extends IInterface {
    IPackageInstaller getPackageInstaller() throws RemoteException;

    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
