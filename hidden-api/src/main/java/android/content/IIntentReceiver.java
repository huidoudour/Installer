package android.content;

import android.os.Bundle;
import android.os.IInterface;

// 隐藏 API 桩，app 模块通过 compileOnly 引用（含反射），IDE 误报未使用
@SuppressWarnings("unused")
public interface IIntentReceiver extends IInterface {
    void performReceive(Intent intent, int resultCode, String data,
                        Bundle extras, boolean ordered, boolean sticky, int sendingUser);
}
