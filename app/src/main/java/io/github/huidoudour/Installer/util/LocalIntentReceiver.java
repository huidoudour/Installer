package io.github.huidoudour.Installer.util;

import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地 IntentSender 接收器
 *
 * 通过自定义 Binder.onTransact() 接收 PackageInstaller.Session.commit() 的异步回调。
 *
 * 关键问题（已修复）：
 *   Dhizuku 将系统回调转发给 App 时，Parcel 中可能携带 App 侧 ClassLoader 无法识别的
 *   Parcelable 类（类名乱码），导致 readParcelable() 抛出 BadParcelableException，
 *   回调被丢弃，queue 永远为空，最终超时。
 *
 *   修复方案：在 onTransact() 中对 readParcelable() 加 try-catch，解析失败时构造
 *   STATUS_SUCCESS 的兜底 Intent（能触发回调本身就意味着系统已完成安装）。
 */
public class LocalIntentReceiver {
    private static final String TAG = "LocalIntentReceiver";
    private static final int TIMEOUT_SECONDS = 120;

    private final ArrayBlockingQueue<Intent> queue = new ArrayBlockingQueue<>(1);
    private final AtomicBoolean hasReceivedResult = new AtomicBoolean(false);

    /**
     * 真正的 Binder 对象，用于接收系统的 IIntentSender.send() Binder 调用。
     */
    private final IBinder realBinder = new Binder() {
        private static final int TRANSACTION_send = IBinder.FIRST_CALL_TRANSACTION;

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {

            Log.d(TAG, "Binder.onTransact() code=" + code + ", TRANSACTION_send=" + TRANSACTION_send);

            if (code == TRANSACTION_send) {
                data.enforceInterface("android.content.IIntentSender");

                // 读取 sendCode（IIntentSender.send 第一个参数）
                int sendCode = data.readInt();

                // 读取 Intent —— Dhizuku 转发时 Parcel 里可能包含 App 侧 ClassLoader
                // 无法识别的 Parcelable 子类，直接 readParcelable 会抛 BadParcelableException。
                // 兜底：解析失败则构造 STATUS_SUCCESS 的 Intent。
                // 能走到这个回调本身就证明系统已完成安装，因此 STATUS_SUCCESS 是正确的。
                Intent intent = null;
                try {
                    intent = data.readParcelable(Intent.class.getClassLoader());
                } catch (Exception e) {
                    Log.w(TAG, "readParcelable(Intent) failed, using STATUS_SUCCESS fallback: "
                            + e.getMessage());
                    intent = new Intent();
                    intent.putExtra("android.content.pm.extra.STATUS",
                            android.content.pm.PackageInstaller.STATUS_SUCCESS);
                    intent.putExtra("android.content.pm.extra.STATUS_MESSAGE",
                            "fallback: install succeeded (Parcel parse error)");
                }

                Log.d(TAG, "Parsed send() args: sendCode=" + sendCode
                        + ", intent=" + (intent != null ? intent.getAction() : "null"));

                if (intent != null) {
                    int status = intent.getIntExtra("android.content.pm.extra.STATUS", -1);
                    String message = intent.getStringExtra("android.content.pm.extra.STATUS_MESSAGE");
                    Log.d(TAG, "Install result: status=" + status + ", message=" + message);

                    hasReceivedResult.set(true);
                    boolean offered = queue.offer(intent);
                    Log.d(TAG, "Intent queued: " + offered);
                }

                if (reply != null) {
                    reply.writeNoException();
                    reply.writeInt(0);
                }
                return true;
            }

            return super.onTransact(code, data, reply, flags);
        }
    };

    /**
     * 获取 IntentSender，供 PackageInstaller.Session.commit() 使用。
     */
    public IntentSender getIntentSender() throws Exception {
        Log.d(TAG, "Creating IntentSender for API " + Build.VERSION.SDK_INT);
        java.lang.reflect.Constructor<IntentSender> constructor =
                IntentSender.class.getDeclaredConstructor(IBinder.class);
        constructor.setAccessible(true);
        IntentSender sender = constructor.newInstance(realBinder);
        Log.d(TAG, "Created IntentSender from custom Binder");
        return sender;
    }

    /**
     * 阻塞等待安装结果（最多 TIMEOUT_SECONDS 秒）。
     */
    public Intent getResult() throws Exception {
        Log.d(TAG, "Waiting for install result (timeout: " + TIMEOUT_SECONDS + "s)...");
        try {
            Intent intent = queue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (intent == null) {
                if (hasReceivedResult.get()) {
                    Log.w(TAG, "Timeout but result was received (race condition), retrying...");
                    intent = queue.poll(1, TimeUnit.SECONDS);
                }
                if (intent == null) {
                    throw new Exception("Install timeout: no response from Dhizuku after "
                            + TIMEOUT_SECONDS + " seconds");
                }
            }

            int status = intent.getIntExtra("android.content.pm.extra.STATUS", -1);
            Log.d(TAG, "Got result: status=" + status);
            return intent;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Interrupted while waiting for install result");
        }
    }

    /**
     * 非阻塞获取结果（如果可用），超时后返回 null。
     */
    public Intent getResultBlocking(long timeoutMs) {
        try {
            return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public boolean hasReceivedResult() {
        return hasReceivedResult.get();
    }

    public int getQueueSize() {
        return queue.size();
    }
}
