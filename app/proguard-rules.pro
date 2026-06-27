# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Dhizuku 内部通过反射访问隐藏 API
-dontwarn android.app.ActivityThread

# auto-service 注解处理器，仅编译期使用
-dontwarn javax.annotation.processing.**

# ========== Shizuku / Dhizuku ==========
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class com.rosan.dhizuku.** { *; }

# ========== 隐藏 API stubs (反射调用) ==========
# 这些类在编译期是 stub，运行时使用系统框架真类
-dontwarn android.content.pm.IPackageInstaller
-dontwarn android.content.pm.IPackageInstallerSession
-dontwarn android.content.pm.IPackageManager
-dontwarn android.content.pm.PackageInstaller
-dontwarn android.content.pm.PackageInstaller$Session
-dontwarn android.content.pm.PackageInstaller$SessionParams
-dontwarn android.content.IIntentSender
-dontwarn android.content.IIntentReceiver
-dontwarn android.os.ServiceManager

# 保留 IInterface 和 Parcelable 的 CREATOR
-keepclassmembers class * implements android.os.IInterface {
    static ** CREATOR;
}
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# 保留 DhizukuInstallHelper 中反射使用的隐藏方法和字段
-keepclassmembers class android.content.pm.PackageInstaller {
    public <init>(...);
}
-keepclassmembers class android.content.pm.PackageInstaller$SessionParams {
    int installFlags;
}
-keepclassmembers class android.content.pm.PackageInstaller$Session {
    *** mSession;
}
-keepclassmembers class android.content.IntentSender {
    public <init>(android.content.IIntentSender);
}