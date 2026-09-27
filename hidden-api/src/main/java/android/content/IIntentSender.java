package android.content;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

/**
 * Hidden API interface for IIntentSender.
 * Used by LocalIntentReceiver to intercept PackageInstaller session commit callbacks.
 */
// 隐藏 API 桩，app 模块通过 compileOnly 引用（含反射），IDE 误报未使用
@SuppressWarnings("unused")
public interface IIntentSender extends IInterface {
    void send(int code, Intent intent, String resolvedType, IBinder whitelistToken,
             IIntentReceiver finishedReceiver, String requiredPermission, Bundle options)
             throws android.os.RemoteException;

    abstract class Stub extends Binder implements IIntentSender {
        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        private static final String DESCRIPTOR = "android.content.IIntentSender";

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }

        public static IIntentSender asInterface(android.os.IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
