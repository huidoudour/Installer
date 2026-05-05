package io.github.huidoudour.Installer.util;

import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 本地 IntentSender 接收器
 * 创建一个本地的 IIntentSender 来接收安装结果回调
 */
public class LocalIntentReceiver {
    private final ArrayBlockingQueue<Intent> queue = new ArrayBlockingQueue<>(1);

    private final IIntentSender localSender = new IIntentSender() {
        private final IBinder binder = new Binder() {
            @Override
            protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws RemoteException {
                return false;
            }
        };

        @Override
        public IBinder asBinder() {
            return binder;
        }

        @Override
        public int send(int code, Intent intent, String resolvedType, IBinder whitelistToken,
                      IIntentReceiver finishedReceiver, int flags, Bundle options) throws RemoteException {
            if (intent != null) {
                queue.offer(intent);
            }
            return 0;
        }
    };

    public IntentSender getIntentSender() throws Exception {
        ReflectionProvider reflect = new ReflectionProvider();
        java.lang.reflect.Constructor<?> constructor;

        // API 31+ uses IIntentSender, API 30 and below uses IBinder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            constructor = reflect.getDeclaredConstructor(IntentSender.class, IIntentSender.class);
            if (constructor != null) {
                return (IntentSender) constructor.newInstance(localSender);
            }
        }

        // Fallback to IBinder constructor for older APIs
        constructor = reflect.getDeclaredConstructor(IntentSender.class, IBinder.class);
        if (constructor == null) {
            throw new Exception("Failed to get IntentSender constructor");
        }
        return (IntentSender) constructor.newInstance(localSender.asBinder());
    }

    public Intent getResult() throws Exception {
        Intent intent = queue.poll(5, TimeUnit.MINUTES);
        if (intent == null) {
            throw new Exception("Timeout waiting for install result");
        }
        return intent;
    }
}
