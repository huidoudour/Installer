package io.github.huidoudour.Installer.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.IPackageInstaller;
import android.content.pm.IPackageInstallerSession;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.ServiceManager;

import com.rosan.dhizuku.api.Dhizuku;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.List;

import io.github.huidoudour.Installer.R;

/**
 * Dhizuku APK 安装辅助类
 * 照搬 InstallerX-Revived 的实现方式
 *
 * 关键步骤：
 * 1. 通过 HiddenApiBypass 绕过 hidden API 限制
 * 2. 使用 Dhizuku.binderWrapper() 包装 IPackageManager 和 IPackageInstaller 的 binder
 * 3. 使用 LocalIntentReceiver 创建 IntentSender（关键：不需要包装 PendingIntent）
 * 4. 包装 session 的 binder
 * 5. 执行安装
 */
public class DhizukuInstallHelper {

    private static final String TAG = "DhizukuInstallHelper";

    private static final ReflectionProvider reflect = new ReflectionProvider();

    public interface InstallCallback {
        void onProgress(String message);
        void onSuccess(String message);
        void onError(String error);
    }

    private static void bypassHiddenApi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("");
        }
    }

    private static void ensureInitialized(Context context) throws Exception {
        bypassHiddenApi();

        boolean initResult = Dhizuku.init(context.getApplicationContext());
        android.util.Log.i(TAG, "Dhizuku.init() result: " + initResult);
        if (!initResult) {
            throw new Exception("Dhizuku initialization failed.");
        }
        boolean permissionGranted = Dhizuku.isPermissionGranted();
        android.util.Log.i(TAG, "Dhizuku.isPermissionGranted(): " + permissionGranted);
        if (!permissionGranted) {
            throw new Exception("Dhizuku permission not granted.");
        }
    }

    /**
     * 包装 IBinder
     */
    private static IBinder iBinderWrapper(IBinder iBinder) throws Exception {
        return Dhizuku.binderWrapper(iBinder);
    }

    /**
     * 获取包装后的 IPackageManager
     */
    private static IPackageManager getIPackageManager() throws Exception {
        IBinder binder = iBinderWrapper(ServiceManager.getService("package"));
        return IPackageManager.Stub.asInterface(binder);
    }

    /**
     * 获取包装后的 IPackageInstaller
     */
    private static IPackageInstaller getIPackageInstaller(IPackageManager iPackageManager) throws Exception {
        IBinder installerBinder = iPackageManager.getPackageInstaller().asBinder();
        IBinder wrappedBinder = iBinderWrapper(installerBinder);
        return IPackageInstaller.Stub.asInterface(wrappedBinder);
    }

    /**
     * 获取 Dhizuku 所有者包名
     */
    private static String getDhizukuComponentName() throws Exception {
        return Dhizuku.getOwnerPackageName();
    }

    /**
     * 创建 PackageInstaller 对象
     */
    private static PackageInstaller createPackageInstaller(
            Context context,
            IPackageInstaller iPackageInstaller,
            String installerPackageName,
            int userId) throws Exception {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Constructor<PackageInstaller> constructor = reflect.getDeclaredConstructor(
                PackageInstaller.class,
                IPackageInstaller.class,
                String.class,
                String.class,
                int.class
            );
            if (constructor == null) {
                throw new Exception("Failed to get PackageInstaller constructor for Android S+");
            }
            return constructor.newInstance(iPackageInstaller, installerPackageName, null, userId);
        } else {
            Constructor<PackageInstaller> constructor = reflect.getDeclaredConstructor(
                PackageInstaller.class,
                IPackageInstaller.class,
                String.class,
                int.class
            );
            if (constructor == null) {
                throw new Exception("Failed to get PackageInstaller constructor for pre-Android S");
            }
            return constructor.newInstance(iPackageInstaller, installerPackageName, userId);
        }
    }

    /**
     * 设置 Session 的 IBinder
     */
    private static void setSessionIBinder(PackageInstaller.Session session) throws Exception {
        Object iPackageInstallerSession = reflect.getFieldValue(session, "mSession", PackageInstaller.Session.class);
        if (iPackageInstallerSession == null) {
            android.util.Log.w(TAG, "mSession is null, skipping binder replacement");
            return;
        }

        IBinder originalBinder = ((IInterface) iPackageInstallerSession).asBinder();
        IBinder wrappedBinder = iBinderWrapper(originalBinder);

        reflect.setFieldValue(
            session,
            "mSession",
            PackageInstaller.Session.class,
            IPackageInstallerSession.Stub.asInterface(wrappedBinder)
        );
        android.util.Log.i(TAG, "Session IBinder replaced with Dhizuku wrapper");
    }

    /**
     * 验证安装结果
     */
    private static void installResultVerify(Context context, LocalIntentReceiver receiver) throws Exception {
        Intent intent = receiver.getResult();
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && confirmIntent != null) {
            android.util.Log.i(TAG, "Pending user action, starting confirmation activity");
            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(confirmIntent);
            installResultVerify(context, receiver);
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            android.util.Log.i(TAG, "Install successful");
            return;
        }

        int legacyStatus = intent.getIntExtra("android.content.pm.extra.LEGACY_STATUS", PackageInstaller.STATUS_FAILURE);
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        throw new Exception("Install failed: status=" + status + ", legacyStatus=" + legacyStatus + ", message=" + msg);
    }

    /**
     * 创建安装会话
     */
    private static PackageInstaller.Session createSession(
            Context context,
            PackageInstaller packageInstaller,
            boolean replaceExisting,
            boolean grantPermissions) throws Exception {

        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        );

        int installFlags = params.installFlags;
        installFlags |= 0x00000002; // INSTALL_REPLACE_EXISTING
        if (grantPermissions) {
            installFlags |= 0x00000100; // GRANT_ALL_REQUESTED_PERMISSIONS
        }
        params.installFlags = installFlags;

        int sessionId = packageInstaller.createSession(params);
        android.util.Log.i(TAG, "Created session: " + sessionId);

        PackageInstaller.Session session = packageInstaller.openSession(sessionId);
        setSessionIBinder(session);

        return session;
    }

    /**
     * 写入 APK 数据到会话
     */
    private static void writeApkToSession(PackageInstaller.Session session, File apkFile, String name) throws Exception {
        long fileSize = apkFile.length();
        android.util.Log.i(TAG, "Writing APK to session: " + name + " (" + fileSize + " bytes)");

        try (FileInputStream fis = new FileInputStream(apkFile);
             OutputStream os = session.openWrite(name, 0, fileSize)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
            session.fsync(os);
        }
        android.util.Log.i(TAG, "APK written to session successfully: " + name);
    }

    /**
     * 提交安装
     */
    private static void commitSession(Context context, PackageInstaller.Session session) throws Exception {
        LocalIntentReceiver receiver = new LocalIntentReceiver();
        IntentSender intentSender = receiver.getIntentSender();
        session.commit(intentSender);
        installResultVerify(context, receiver);
    }

    public static void installSingleApk(Context context, File apkFile, boolean replaceExisting, boolean grantPermissions, InstallCallback callback) {
        new Thread(() -> {
            PackageInstaller.Session session = null;
            try {
                callback.onProgress(context.getString(R.string.start_apk_install));
                ensureInitialized(context);

                String installerPackageName = getDhizukuComponentName();
                android.util.Log.i(TAG, "Dhizuku owner package name: " + installerPackageName);

                IPackageManager iPackageManager = getIPackageManager();
                IPackageInstaller iPackageInstaller = getIPackageInstaller(iPackageManager);

                int userId = android.os.UserHandle.myUserId();
                PackageInstaller packageInstaller = createPackageInstaller(
                    context, iPackageInstaller, installerPackageName, userId);

                callback.onProgress(context.getString(R.string.create_session, "PackageInstaller Session API"));

                session = createSession(context, packageInstaller, replaceExisting, grantPermissions);

                callback.onProgress(context.getString(R.string.write_apk_data));
                writeApkToSession(session, apkFile, "base.apk");

                callback.onProgress(context.getString(R.string.submit_install));
                commitSession(context, session);

                callback.onSuccess(context.getString(R.string.install_success_simple));

            } catch (Exception e) {
                android.util.Log.e(TAG, "Install failed: " + e.getMessage(), e);
                callback.onError(context.getString(R.string.install_exception, e.getMessage()));
            } finally {
                if (session != null) {
                    try {
                        session.close();
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    public static void installXapk(Context context, String xapkPath, boolean replaceExisting, boolean grantPermissions, InstallCallback callback) {
        new Thread(() -> {
            List<File> extractedApks = null;
            PackageInstaller.Session session = null;
            try {
                callback.onProgress(context.getString(R.string.extract_xapk));
                extractedApks = XapkInstaller.extractXapk(context, xapkPath);
                callback.onProgress(context.getString(R.string.extract_complete, extractedApks.size()));

                ensureInitialized(context);

                String installerPackageName = getDhizukuComponentName();

                IPackageManager iPackageManager = getIPackageManager();
                IPackageInstaller iPackageInstaller = getIPackageInstaller(iPackageManager);

                int userId = android.os.UserHandle.myUserId();
                PackageInstaller packageInstaller = createPackageInstaller(
                    context, iPackageInstaller, installerPackageName, userId);

                callback.onProgress(context.getString(R.string.create_session, ""));

                session = createSession(context, packageInstaller, replaceExisting, grantPermissions);

                int current = 0;
                for (File apkFile : extractedApks) {
                    current++;
                    callback.onProgress(context.getString(R.string.apk_progress, current, extractedApks.size(), apkFile.getName()));
                    writeApkToSession(session, apkFile, apkFile.getName());
                }

                callback.onProgress(context.getString(R.string.submitting_install));
                commitSession(context, session);

                callback.onSuccess(context.getString(R.string.xapk_install_success_msg, extractedApks.size()));

            } catch (Exception e) {
                android.util.Log.e(TAG, "XAPK install failed: " + e.getMessage(), e);
                callback.onError(context.getString(R.string.xapk_install_exception, e.getMessage()));
            } finally {
                if (session != null) {
                    try {
                        session.close();
                    } catch (Exception ignored) {}
                }
                if (extractedApks != null) {
                    XapkInstaller.cleanupTempFiles(extractedApks);
                }
            }
        }).start();
    }

    public static void installApk(Context context, String apkPath, boolean replaceExisting, boolean grantPermissions, InstallCallback callback) {
        try {
            File apkFile = new File(apkPath);
            if (!apkFile.exists()) {
                callback.onError(context.getString(R.string.apk_not_exist));
                return;
            }
            installSingleApk(context, apkFile, replaceExisting, grantPermissions, callback);
        } catch (Exception e) {
            callback.onError(context.getString(R.string.install_exception, e.getMessage()));
        }
    }
}
