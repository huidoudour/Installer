package android.content;

import android.content.Intent;
import android.content.IIntentReceiver;
import android.os.Bundle;
import android.os.IBinder;

oneway interface IIntentSender {
    void send(int code, in Intent intent, String resolvedType, IBinder whitelistToken,
              IIntentReceiver finishedReceiver, String requiredPermission, in Bundle options);
}
