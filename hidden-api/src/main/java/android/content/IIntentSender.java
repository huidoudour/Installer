package android.content;

import android.os.IInterface;
import android.os.IBinder;
import android.os.Bundle;

/**
 * Hidden API interface for IIntentSender.
 * This interface is used to intercept installation callbacks.
 */
public interface IIntentSender extends IInterface {
    int send(int code, Intent intent, String resolvedType, IBinder whitelistToken,
             IIntentReceiver finishedReceiver, int flags, Bundle options) throws android.os.RemoteException;
}
