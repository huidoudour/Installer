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
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

    /**
     * 从 APK 文件获取包名
     *
     * 优先用 PackageManager.getPackageArchiveInfo() 读取包名（Android 9+ 推荐，无需隐藏 API）；
     * 若失败则降级到反射 PackageParser（Android 9 及以下）。
     * 两种方式都失败时返回 null。
     */
    private static String getPackageNameFromApk(Context context, File apkFile) {
        // 方式1：PackageManager.getPackageArchiveInfo()（官方 API，推荐）
        try {
            android.content.pm.PackageInfo info =
                    context.getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (info != null && info.packageName != null && !info.packageName.isEmpty()) {
                android.util.Log.i(TAG, "Got package name via getPackageArchiveInfo: " + info.packageName);
                return info.packageName;
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "Failed to get package name via getPackageArchiveInfo: " + e.getMessage());
        }

        // 方式2：反射 PackageParser（Android 9 及以下可用）
        try {
            Class<?> packageParserClass = Class.forName("android.content.pm.PackageParser");
            java.lang.reflect.Method parseBaseApkLiteMethod = packageParserClass.getDeclaredMethod(
                "parseBaseApkLite",
                java.io.File.class,
                int.class
            );
            parseBaseApkLiteMethod.setAccessible(true);
            Object parser = packageParserClass.getConstructor().newInstance();
            Object pkg = parseBaseApkLiteMethod.invoke(parser, apkFile, 0);
            if (pkg != null) {
                java.lang.reflect.Field packageNameField = pkg.getClass().getDeclaredField("packageName");
                packageNameField.setAccessible(true);
                String name = (String) packageNameField.get(pkg);
                if (name != null && !name.isEmpty()) {
                    android.util.Log.i(TAG, "Got package name via PackageParser: " + name);
                    return name;
                }
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "Failed to get package name via PackageParser: " + e.getMessage());
        }

        android.util.Log.w(TAG, "All methods failed to get package name from APK");
        return null;
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
            Constructor<?> constructor = reflect.getDeclaredConstructor(
                PackageInstaller.class,
                IPackageInstaller.class,
                String.class,
                String.class,
                int.class
            );
            if (constructor == null) {
                throw new Exception("Failed to get PackageInstaller constructor for Android S+");
            }
            return (PackageInstaller) constructor.newInstance(iPackageInstaller, installerPackageName, null, userId);
        } else {
            Constructor<?> constructor = reflect.getDeclaredConstructor(
                PackageInstaller.class,
                IPackageInstaller.class,
                String.class,
                int.class
            );
            if (constructor == null) {
                throw new Exception("Failed to get PackageInstaller constructor for pre-Android S");
            }
            return (PackageInstaller) constructor.newInstance(iPackageInstaller, installerPackageName, userId);
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

        // 使用反射访问 installFlags 字段（隐藏API）
        try {
            java.lang.reflect.Field flagsField = PackageInstaller.SessionParams.class.getDeclaredField("installFlags");
            flagsField.setAccessible(true);
            int installFlags = flagsField.getInt(params);
            installFlags |= 0x00000002; // INSTALL_REPLACE_EXISTING
            // 注意：GRANT_ALL_REQUESTED_PERMISSIONS (0x00000100) 需要 INSTALL_GRANT_RUNTIME_PERMISSIONS 权限
            // 这个权限只有系统应用或拥有特殊权限的应用才能使用
            // 在 Dhizuku 环境下，我们不设置这个标志，由系统在安装完成后处理权限授予
            if (grantPermissions) {
                android.util.Log.w(TAG, "Grant permissions flag skipped - requires system permission");
            }
            flagsField.setInt(params, installFlags);
        } catch (Exception e) {
            android.util.Log.w(TAG, "Failed to set installFlags via reflection: " + e.getMessage());
        }

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
     * 提交安装（同步方式）
     * 使用 LocalIntentReceiver + 轮询双重机制
     */
    private static void commitSession(Context context, PackageInstaller.Session session, String packageName) throws Exception {
        LocalIntentReceiver receiver = new LocalIntentReceiver();
        final long startTime = System.currentTimeMillis();
        final long timeout = 120000; // 2分钟超时
        final long pollInterval = 500; // 500ms 轮询间隔
        final long initialWait = 2000; // 先等待 2 秒让 Dhizuku 处理

        try {
            android.util.Log.i(TAG, "Creating IntentSender from LocalIntentReceiver...");
            IntentSender intentSender = receiver.getIntentSender();
            android.util.Log.i(TAG, "IntentSender created successfully, committing session...");

            // 提交安装
            session.commit(intentSender);
            android.util.Log.i(TAG, "Session.commit() returned, starting hybrid wait mechanism...");

            // 策略1: 先等待一小段时间让 Dhizuku 处理
            Thread.sleep(initialWait);

            // 检查 IntentSender 是否收到回调
            if (receiver.hasReceivedResult()) {
                Intent result = receiver.getResultBlocking(1000); // 等待最多 1 秒获取结果
                if (result != null) {
                    android.util.Log.i(TAG, "Got result from IntentSender callback!");
                    handleInstallResult(context, result, session);
                    return;
                }
            }

            // 策略2: 轮询检查包是否已安装（作为 IntentSender 的备选）
            android.util.Log.i(TAG, "IntentSender callback not received, using poll mechanism...");
            while (System.currentTimeMillis() - startTime < timeout) {
                if (isPackageInstalled(context, packageName)) {
                    android.util.Log.i(TAG, "Package installed successfully (detected by polling)!");
                    return;
                }

                // 检查是否收到 IntentSender 回调
                if (receiver.hasReceivedResult()) {
                    Intent result = receiver.getResultBlocking(100);
                    if (result != null) {
                        android.util.Log.i(TAG, "Got result from IntentSender (delayed)!");
                        handleInstallResult(context, result, session);
                        return;
                    }
                }

                Thread.sleep(pollInterval);
            }

            // 超时，检查最终状态
            if (isPackageInstalled(context, packageName)) {
                android.util.Log.i(TAG, "Package installed (found after timeout)!");
                return;
            }

            throw new Exception("Install timeout: package not installed within " + (timeout / 1000) + " seconds");

        } catch (Exception e) {
            android.util.Log.e(TAG, "Error during commit: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 处理安装结果
     */
    private static void handleInstallResult(Context context, Intent result, PackageInstaller.Session session) throws Exception {
        int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        android.util.Log.i(TAG, "Installation result: status=" + status + ", message=" + message);

        if (status == PackageInstaller.STATUS_SUCCESS) {
            android.util.Log.i(TAG, "Install successful!");
            return;
        } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmIntent = result.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmIntent != null) {
                android.util.Log.i(TAG, "Pending user action, starting confirmation activity");
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmIntent);
                Thread.sleep(5000); // 等待用户操作
                return;
            }
        }

        int legacyStatus = result.getIntExtra("android.content.pm.extra.LEGACY_STATUS", -1);
        throw new Exception("Install failed: status=" + status + ", legacyStatus=" + legacyStatus + ", message=" + message);
    }

    /**
     * 检查包是否已安装
     */
    private static boolean isPackageInstalled(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void installSingleApk(Context context, File apkFile, boolean replaceExisting, boolean grantPermissions, InstallCallback callback) {
        new Thread(() -> {
            PackageInstaller.Session session = null;
            try {
                callback.onProgress(context.getString(R.string.start_apk_install));

                // 获取要安装的 APK 的包名
                String targetPackageName = getPackageNameFromApk(context, apkFile);
                android.util.Log.i(TAG, "Target package name: " + targetPackageName);

                ensureInitialized(context);

                String installerPackageName = getDhizukuComponentName();
                android.util.Log.i(TAG, "Dhizuku owner package name: " + installerPackageName);

                IPackageManager iPackageManager = getIPackageManager();
                IPackageInstaller iPackageInstaller = getIPackageInstaller(iPackageManager);

                int userId = getUserId();
                PackageInstaller packageInstaller = createPackageInstaller(
                    context, iPackageInstaller, installerPackageName, userId);

                callback.onProgress(context.getString(R.string.create_session, "PackageInstaller Session API"));

                session = createSession(context, packageInstaller, replaceExisting, grantPermissions);

                callback.onProgress(context.getString(R.string.write_apk_data));
                writeApkToSession(session, apkFile, "base.apk");

                callback.onProgress(context.getString(R.string.submit_install));
                commitSession(context, session, targetPackageName);

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

                // 获取主 APK 的包名（通常第一个文件是主 APK）
                String targetPackageName = null;
                if (!extractedApks.isEmpty()) {
                    targetPackageName = getPackageNameFromApk(context, extractedApks.get(0));
                }
                android.util.Log.i(TAG, "XAPK main package name: " + targetPackageName);

                ensureInitialized(context);

                String installerPackageName = getDhizukuComponentName();

                IPackageManager iPackageManager = getIPackageManager();
                IPackageInstaller iPackageInstaller = getIPackageInstaller(iPackageManager);

                int userId = getUserId();
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
                commitSession(context, session, targetPackageName);

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

    /**
     * 获取当前用户ID（兼容不同Android版本）
     */
    private static int getUserId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return android.os.Process.myUserHandle().hashCode();
        } else {
            // 对于旧版本，尝试使用反射调用 UserHandle.myUserId()
            try {
                java.lang.reflect.Method myUserIdMethod = android.os.UserHandle.class.getDeclaredMethod("myUserId");
                myUserIdMethod.setAccessible(true);
                return (int) myUserIdMethod.invoke(null);
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to get user ID via reflection, using default 0");
                return 0;
            }
        }
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
