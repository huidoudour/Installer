package dev.huidoudour.installer.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.huidoudour.installer.R
import java.io.File

/**
 * 统一安装分发：按 [Authorizer] 选择具体的安装实现。
 *
 * - [Authorizer.Shizuku] → [ShizukuInstallHelper]
 * - [Authorizer.Dhizuku] → [DhizukuInstallHelper]
 * - [Authorizer.None]    → 系统安装器（FileProvider + ACTION_INSTALL_PACKAGE / ACTION_VIEW）
 *
 * 供安装页（[dev.huidoudour.installer.ui.installer.InstallerViewModel]）与
 * 独立安装对话框复用，避免重复代码。
 */
object InstallDispatcher {

    interface Callback {
        fun onProgress(message: String)
        fun onSuccess(message: String)
        fun onError(error: String)
    }

    /**
     * 使用指定授权方式安装单个文件。
     *
     * @param filePath 待安装文件路径（APK 或 XAPK）
     * @param isXapk   是否为 XAPK 容器
     */
    fun install(
        context: Context,
        authorizer: Authorizer,
        filePath: String,
        isXapk: Boolean,
        replaceExisting: Boolean,
        grantPermissions: Boolean,
        callback: Callback,
    ) {
        when (authorizer) {
            Authorizer.Shizuku -> {
                val cb = object : ShizukuInstallHelper.InstallCallback {
                    override fun onProgress(message: String) = callback.onProgress(message)
                    override fun onSuccess(message: String) = callback.onSuccess(message)
                    override fun onError(error: String) = callback.onError(error)
                }
                if (isXapk) {
                    ShizukuInstallHelper.installXapk(
                        context, filePath, replaceExisting, grantPermissions, cb
                    )
                } else {
                    ShizukuInstallHelper.installSingleApk(
                        context, File(filePath), replaceExisting, grantPermissions, cb
                    )
                }
            }

            Authorizer.Dhizuku -> {
                val cb = object : DhizukuInstallHelper.InstallCallback {
                    override fun onProgress(message: String) = callback.onProgress(message)
                    override fun onSuccess(message: String) = callback.onSuccess(message)
                    override fun onError(error: String) = callback.onError(error)
                }
                if (isXapk) {
                    DhizukuInstallHelper.installXapk(
                        context, filePath, replaceExisting, grantPermissions, cb
                    )
                } else {
                    DhizukuInstallHelper.installSingleApk(
                        context, File(filePath), replaceExisting, grantPermissions, cb
                    )
                }
            }

            Authorizer.None -> installViaSystemInstaller(context, filePath, callback)
        }
    }

    /**
     * 系统安装器兜底：将文件复制到缓存目录后，经 FileProvider 生成 content Uri，
     * 拉起系统安装界面（ACTION_INSTALL_PACKAGE 优先，失败回退 ACTION_VIEW）。
     */
    private fun installViaSystemInstaller(
        context: Context,
        filePath: String,
        callback: Callback,
    ) {
        try {
            val source = File(filePath)
            if (!source.exists()) {
                callback.onError(context.getString(R.string.file_not_found, filePath))
                return
            }

            // 复制到 cacheDir，确保被 file_paths.xml 的 cache-path 覆盖，可被 FileProvider 共享
            val shared = File(context.cacheDir, source.name)
            if (source.absolutePath != shared.absolutePath) {
                source.copyTo(shared, overwrite = true)
            }

            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, shared)
            val mime = mimeTypeOf(shared.name)

            callback.onProgress("Launching system installer...")

            @Suppress("DEPRECATION")
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(installIntent)
                callback.onSuccess(context.getString(R.string.system_installer_launched))
            } catch (e: ActivityNotFoundException) {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(viewIntent)
                callback.onSuccess(context.getString(R.string.system_installer_launched))
            }
        } catch (e: Exception) {
            callback.onError(context.getString(R.string.system_installer_failed, e.message))
        }
    }

    private fun mimeTypeOf(fileName: String): String = when {
        fileName.endsWith(".xapk", ignoreCase = true) -> "application/xapk-package-archive"
        fileName.endsWith(".apks", ignoreCase = true) ->
            "application/vnd.android.package-archive"
        else -> "application/vnd.android.package-archive"
    }
}
