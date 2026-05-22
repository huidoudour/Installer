package io.github.huidoudour.Installer.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import io.github.huidoudour.Installer.R
import io.github.huidoudour.Installer.util.DhizukuInstallHelper
import io.github.huidoudour.Installer.util.PrivilegeHelper
import io.github.huidoudour.Installer.util.ShizukuInstallHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 安装对话框状态
 */
data class InstallDialogState(
    val appName: String = "",
    val packageName: String = "",
    val version: String = "",
    val upgradeVersion: String = "",
    val minSdk: String = "",
    val targetSdk: String = "",
    val appIcon: Drawable? = null,
    val isUpgrade: Boolean = false,
    val installedVersion: String = "",
    val isInstalling: Boolean = false,
    val installProgress: Int = 0,
    val isComplete: Boolean = false,
    val errorMessage: String? = null,
    val isInfoLoaded: Boolean = false
)

/**
 * Compose 安装对话框
 * 保持与原Java版本相同的UI布局，仅添加动态取色功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallDialog(
    installUri: Uri?,
    onDismiss: () -> Unit,
    onInstallComplete: () -> Unit,
    onOpenApp: (String) -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        text = {
            InstallDialogContent(
                installUri = installUri,
                onDismiss = onDismiss,
                onInstallComplete = onInstallComplete,
                onOpenApp = onOpenApp
            )
        },
        confirmButton = {},
        dismissButton = {}
    )
}

/**
 * 安装对话框内容（内部实现）
 */
@Composable
private fun InstallDialogContent(
    installUri: Uri?,
    onDismiss: () -> Unit,
    onInstallComplete: () -> Unit,
    onOpenApp: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(InstallDialogState()) }

    // 权限模式
    var currentPrivilegeMode by remember { mutableStateOf(PrivilegeHelper.getCurrentMode(context)) }
    var showPrivilegeDialog by remember { mutableStateOf(false) }

    // 从 APK 解析信息
    LaunchedEffect(installUri) {
        if (installUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val apkInfo = parseApkInfo(context, installUri)
                    if (apkInfo != null) {
                        state = state.copy(
                            appName = apkInfo.appName,
                            packageName = apkInfo.packageName,
                            version = apkInfo.version,
                            upgradeVersion = apkInfo.upgradeVersion,
                            minSdk = apkInfo.minSdk,
                            targetSdk = apkInfo.targetSdk,
                            appIcon = apkInfo.appIcon,
                            isUpgrade = apkInfo.isUpgrade,
                            installedVersion = apkInfo.installedVersion,
                            isInfoLoaded = true
                        )
                    }
                } catch (e: Exception) {
                    Log.e("InstallDialog", "Failed to parse APK", e)
                }
            }
        }
    }

    // 检查安装按钮状态
    fun isInstallEnabled(): Boolean {
        if (state.isInstalling) return false
        val status = PrivilegeHelper.getStatus(context, currentPrivilegeMode)
        return status == PrivilegeHelper.PrivilegeStatus.AUTHORIZED
    }

    // 最外层容器 - 对应原XML最外层LinearLayout (paddingTop=8dp, paddingBottom=8dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        // 圆角对话框容器 - 对应 MaterialCardView (cardCornerRadius=20dp, cardElevation=4dp)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            // Card内部容器 - 对应LinearLayout (padding=16dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 应用信息区域 - 始终显示
                InstallInfoHeader(state = state)
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 根据状态显示不同的按钮区域
                when {
                    state.isComplete -> {
                        // 安装完成按钮
                        CompletionButtons(
                            onOpenApp = { onOpenApp(state.packageName) },
                            onFinish = onDismiss,
                            onBack = {
                                state = state.copy(isComplete = false)
                            }
                        )
                    }
                    state.isInstalling -> {
                        // 安装进度按钮
                        InstallingButtons(
                            progress = state.installProgress,
                            onCancel = {
                                state = state.copy(isInstalling = false)
                            }
                        )
                    }
                    else -> {
                        // 安装确认按钮
                        InstallButtons(
                            state = state,
                            isInstallEnabled = isInstallEnabled(),
                            onInstall = {
                                state = state.copy(isInstalling = true)
                                performRealInstallation(
                                    context = context,
                                    filePath = getFilePathFromUri(context, installUri),
                                    mode = currentPrivilegeMode,
                                    onProgress = { progress ->
                                        state = state.copy(installProgress = progress)
                                    },
                                    onSuccess = {
                                        state = state.copy(
                                            isInstalling = false,
                                            isComplete = true,
                                            installProgress = 100
                                        )
                                    },
                                    onError = { error ->
                                        state = state.copy(
                                            isInstalling = false,
                                            errorMessage = error
                                        )
                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            onCancel = onDismiss,
                            onPrivilege = { showPrivilegeDialog = true }
                        )
                    }
                }
            }
        }
    }

    // 权限选择对话框
    if (showPrivilegeDialog) {
        InstallPrivilegeDialog(
            context = context,
            currentMode = currentPrivilegeMode,
            onDismiss = { showPrivilegeDialog = false },
            onModeSelected = { mode ->
                currentPrivilegeMode = mode
                PrivilegeHelper.saveCurrentMode(context, mode)
                showPrivilegeDialog = false
            }
        )
    }
}

/**
 * 安装信息头部 - 完全匹配 dialog_install.xml 布局
 */
@Composable
fun InstallInfoHeader(state: InstallDialogState) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 应用图标和名称区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 应用图标 - 48dp，与原XML一致
            Image(
                painter = if (state.appIcon != null) {
                    rememberDrawablePainter(drawable = state.appIcon)
                } else {
                    rememberDrawablePainter(drawable = androidx.core.content.ContextCompat.getDrawable(
                        androidx.compose.ui.platform.LocalContext.current,
                        android.R.drawable.sym_def_app_icon
                    ))
                },
                contentDescription = stringResource(R.string.app_icon_description),
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 16.dp),
                contentScale = ContentScale.Fit
            )
            
            // 应用名称 - 22sp, bold
            Text(
                text = state.appName.ifEmpty { stringResource(R.string.unknown_app) },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        
        // 应用信息区域 - 居中显示
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 包名 - 14sp
            Text(
                text = "包名：${state.packageName.ifEmpty { "com.example.app" }}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 当前版本 - 14sp
            Text(
                text = "当前版本：${state.installedVersion.ifEmpty { "1.0.0" }}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            // 下箭头
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.arrow_down),
                modifier = Modifier
                    .size(24.dp)
                    .padding(vertical = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 升级版本 - 14sp
            Text(
                text = "升级版本：${state.version.ifEmpty { "1.1.0" }}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 最低SDK - 14sp
            Text(
                text = "最低SDK：${state.minSdk.ifEmpty { "21" }}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            // 目标SDK - 14sp
            Text(
                text = "目标SDK：${state.targetSdk.ifEmpty { "34" }}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 安装确认按钮区域 - 完全匹配 dialog_install.xml 布局
 */
@Composable
fun InstallButtons(
    state: InstallDialogState,
    isInstallEnabled: Boolean = true,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onPrivilege: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 安装按钮 - 48dp高度，圆角16dp
        Button(
            onClick = onInstall,
            enabled = isInstallEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),  // button_secondary green
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = stringResource(R.string.install),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 按钮行：权限和取消 - 等高48dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            // 权限按钮 - 圆角12dp
            Button(
                onClick = onPrivilege,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3),  // button_primary blue
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = stringResource(R.string.privilege),
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 取消按钮 - OutlinedButton，圆角12dp，边框2dp
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * 安装进度按钮区域 - 完全匹配 dialog_install.xml 布局
 */
@Composable
fun InstallingButtons(
    progress: Int,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 线性进度条 - trackThickness=8dp, trackCornerRadius=4dp
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp
        )
        
        // 取消按钮 - OutlinedButton，圆角16dp，边框2dp，高度48dp
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = stringResource(R.string.cancel),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 安装完成按钮区域 - 完全匹配 dialog_install.xml 布局
 */
@Composable
fun CompletionButtons(
    onOpenApp: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 打开应用按钮 - 48dp高度，圆角16dp
        Button(
            onClick = onOpenApp,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2196F3),  // button_primary_tint blue
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = stringResource(R.string.open_app),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 按钮行：返回和完成 - 等高48dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            // 返回按钮 - OutlinedButton，圆角12dp，边框2dp
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = stringResource(R.string.back),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 完成按钮 - 圆角12dp
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),  // button_secondary_tint green
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = stringResource(R.string.finish),
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * APK 信息数据类
 */
data class ApkInfo(
    val appName: String,
    val packageName: String,
    val version: String,
    val upgradeVersion: String,
    val minSdk: String,
    val targetSdk: String,
    val appIcon: Drawable?,
    val isUpgrade: Boolean,
    val installedVersion: String
)

/**
 * 解析 APK 信息
 */
private fun parseApkInfo(context: Context, uri: Uri): ApkInfo? {
    return try {
        val pm = context.packageManager
        
        // 将 URI 转换为实际文件路径
        val path = when (uri.scheme) {
            "file" -> uri.path
            "content" -> {
                // 对于 content:// URI，需要复制到缓存目录
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return null
                val cacheFile = java.io.File(context.cacheDir, "temp_apk.apk")
                inputStream.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                cacheFile.absolutePath
            }
            else -> uri.path
        } ?: return null
        
        val packageInfo = pm.getPackageArchiveInfo(
            path,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS
        ) ?: return null
        
        packageInfo.applicationInfo?.apply {
            sourceDir = path
            publicSourceDir = path
        }
        
        val appInfo = packageInfo.applicationInfo ?: return null
        val appName = appInfo.loadLabel(pm).toString()
        val versionName = packageInfo.versionName ?: "Unknown"
        val versionCode = packageInfo.longVersionCode
        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo.minSdkVersion.toString()
        } else "N/A"
        val targetSdk = appInfo.targetSdkVersion.toString()
        val icon = try {
            appInfo.loadIcon(pm)
        } catch (e: Exception) {
            null
        }
        
        // 检查是否已安装
        val installedPkg = try {
            pm.getPackageInfo(packageInfo.packageName, PackageManager.GET_ACTIVITIES)
        } catch (e: Exception) {
            null
        }
        
        // 根据 VersionCode 对比决定是否显示升级
        val isUpgrade = if (installedPkg != null) {
            val installedVersionCode = installedPkg.longVersionCode
            // 只有当 APK 的 VersionCode 大于已安装版本时，才显示升级
            versionCode > installedVersionCode
        } else {
            false
        }
        
        val installedVersion = installedPkg?.let {
            "${it.versionName ?: "Unknown"} (${it.longVersionCode})"
        } ?: ""
        
        ApkInfo(
            appName = appName,
            packageName = packageInfo.packageName,
            version = "$versionName ($versionCode)",
            upgradeVersion = if (isUpgrade) "$versionName ($versionCode)" else "",
            minSdk = minSdk,
            targetSdk = targetSdk,
            appIcon = icon,
            isUpgrade = isUpgrade,
            installedVersion = installedVersion
        )
    } catch (e: Exception) {
        Log.e("InstallDialog", "Failed to parse APK info", e)
        null
    }
}

/**
 * 执行真实安装
 */
private fun performRealInstallation(
    context: Context,
    filePath: String?,
    mode: PrivilegeHelper.PrivilegeMode,
    onProgress: (Int) -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (filePath == null) {
        onError("Cannot access install file")
        return
    }

    val isXapk = io.github.huidoudour.Installer.util.XapkInstaller.isXapkFile(filePath)

    when (mode) {
        PrivilegeHelper.PrivilegeMode.SHIZUKU -> {
            val callback = object : ShizukuInstallHelper.InstallCallback {
                override fun onProgress(message: String) {
                    Log.d("InstallDialog", message)
                }
                override fun onSuccess(message: String) {
                    Log.d("InstallDialog", message)
                    onSuccess()
                }
                override fun onError(error: String) {
                    Log.e("InstallDialog", error)
                    onError(error)
                }
            }
            if (isXapk) {
                ShizukuInstallHelper.installXapk(context, filePath, true, true, callback)
            } else {
                ShizukuInstallHelper.installApk(context, filePath, true, true, callback)
            }
        }
        PrivilegeHelper.PrivilegeMode.DHIZUKU -> {
            val callback = object : DhizukuInstallHelper.InstallCallback {
                override fun onProgress(message: String) {
                    Log.d("InstallDialog", message)
                }
                override fun onSuccess(message: String) {
                    Log.d("InstallDialog", message)
                    onSuccess()
                }
                override fun onError(error: String) {
                    Log.e("InstallDialog", error)
                    onError(error)
                }
            }
            if (isXapk) {
                DhizukuInstallHelper.installXapk(context, filePath, true, true, callback)
            } else {
                DhizukuInstallHelper.installSingleApk(context, java.io.File(filePath), true, true, callback)
            }
        }
    }
}

/**
 * 从 URI 获取文件路径
 */
private fun getFilePathFromUri(context: Context, uri: Uri?): String? {
    if (uri == null) return null
    return try {
        if (uri.scheme == "file") {
            uri.path
        } else {
            val cacheFile = java.io.File(context.cacheDir, "temp_install_${System.currentTimeMillis()}.apk")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile.absolutePath
        }
    } catch (e: Exception) {
        Log.e("InstallDialog", "Failed to get file path from URI", e)
        null
    }
}

/**
 * 安装对话框内的权限选择对话框
 * 匹配源项目 dialog_privilege.xml 布局
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallPrivilegeDialog(
    context: Context,
    currentMode: PrivilegeHelper.PrivilegeMode,
    onDismiss: () -> Unit,
    onModeSelected: (PrivilegeHelper.PrivilegeMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentMode) }

    // 检查两个权限状态和获取图标
    var shizukuStatus by remember { mutableStateOf<PrivilegeHelper.PrivilegeStatus?>(null) }
    var dhizukuStatus by remember { mutableStateOf<PrivilegeHelper.PrivilegeStatus?>(null) }
    var shizukuIcon by remember { mutableStateOf<Drawable?>(null) }
    var dhizukuIcon by remember { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            shizukuStatus = PrivilegeHelper.checkShizukuStatus()
            dhizukuStatus = PrivilegeHelper.checkDhizukuStatus(context)
            
            // 动态获取已安装授权器的图标
            try {
                val pm = context.packageManager
                val shizukuInfo = pm.getPackageInfo("moe.shizuku.privileged.api", 0)
                shizukuIcon = shizukuInfo.applicationInfo?.loadIcon(pm)
            } catch (e: Exception) {
                shizukuIcon = null
            }
            
            try {
                val pm = context.packageManager
                val dhizukuInfo = pm.getPackageInfo("com.rosan.dhizuku", 0)
                dhizukuIcon = dhizukuInfo.applicationInfo?.loadIcon(pm)
            } catch (e: Exception) {
                dhizukuIcon = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        text = {
            // 最外层容器 - 与InstallDialogContent一致
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp)
            ) {
                // 圆角对话框容器 - 与InstallDialogContent一致
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    // Card内部容器 - 与InstallDialogContent一致
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // 标题
                        Text(
                            text = stringResource(R.string.select_privilege_mode),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // Shizuku 卡片
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMode = PrivilegeHelper.PrivilegeMode.SHIZUKU },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedMode == PrivilegeHelper.PrivilegeMode.SHIZUKU)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (selectedMode == PrivilegeHelper.PrivilegeMode.SHIZUKU)
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            else null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 动态显示图标或使用默认图标
                                Image(
                                    painter = if (shizukuIcon != null) {
                                        rememberDrawablePainter(drawable = shizukuIcon!!)
                                    } else {
                                        rememberDrawablePainter(drawable = androidx.core.content.ContextCompat.getDrawable(
                                            context,
                                            R.drawable.ic_warning
                                        ))
                                    },
                                    contentDescription = "Shizuku",
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Shizuku",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = getPrivilegeStatusText(shizukuStatus),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Dhizuku 卡片
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMode = PrivilegeHelper.PrivilegeMode.DHIZUKU },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedMode == PrivilegeHelper.PrivilegeMode.DHIZUKU)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (selectedMode == PrivilegeHelper.PrivilegeMode.DHIZUKU)
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            else null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 动态显示图标或使用默认图标
                                Image(
                                    painter = if (dhizukuIcon != null) {
                                        rememberDrawablePainter(drawable = dhizukuIcon!!)
                                    } else {
                                        rememberDrawablePainter(drawable = androidx.core.content.ContextCompat.getDrawable(
                                            context,
                                            R.drawable.ic_warning
                                        ))
                                    },
                                    contentDescription = "Dhizuku",
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Dhizuku",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = getPrivilegeStatusText(dhizukuStatus),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 按钮行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onModeSelected(selectedMode) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2196F3),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(stringResource(R.string.next_step))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

private fun getPrivilegeStatusText(status: PrivilegeHelper.PrivilegeStatus?): String {
    return when (status) {
        PrivilegeHelper.PrivilegeStatus.AUTHORIZED -> "Authorized"
        PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED -> "Not Authorized"
        PrivilegeHelper.PrivilegeStatus.NOT_INSTALLED -> "Not Installed"
        PrivilegeHelper.PrivilegeStatus.NOT_RUNNING -> "Not Running"
        PrivilegeHelper.PrivilegeStatus.VERSION_TOO_LOW -> "Version Too Low"
        null -> "Checking..."
    }
}
