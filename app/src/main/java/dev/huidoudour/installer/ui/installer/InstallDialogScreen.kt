package dev.huidoudour.installer.ui.installer

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import dev.huidoudour.installer.auth.Authorizer
import dev.huidoudour.installer.auth.InstallDispatcher
import dev.huidoudour.installer.auth.PrivilegeHelper
import dev.huidoudour.installer.auth.SmartAuthorizer
import dev.huidoudour.installer.install.PackageInfoHelper
import dev.huidoudour.installer.install.XapkInstaller
import dev.huidoudour.installer.util.signature.SignatureHelper
import dev.huidoudour.installer.util.signature.SignatureMatchStatus
import dev.huidoudour.installer.util.signature.SignatureSummary
import dev.huidoudour.installer.ui.theme.LocalThemeStateHolder
import dev.huidoudour.installer.util.LoaderAnimationMode
import dev.huidoudour.installer.util.LoaderAnimationPrefs
import dev.huidoudour.installer.util.SignaturePrefs
import dev.huidoudour.installer.R
import dev.huidoudour.installer.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch

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
    val signature: SignatureSummary? = null,
    val isInfoLoaded: Boolean = false,
    // 正在物化文件 / 解析安装包信息（用于展示加载动画）
    val isLoading: Boolean = true
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

    // installUri 物化到本地后的文件路径（APK 信息解析、签名校验、安装共用同一份缓存文件）
    var materializedPath by remember { mutableStateOf<String?>(null) }

    // 权限模式
    var currentPrivilegeMode by remember { mutableStateOf(PrivilegeHelper.getCurrentMode(context)) }
    var showPrivilegeDialog by remember { mutableStateOf(false) }

    // 签名校验
    val checkSignature = SignaturePrefs.isCheckEnabled(context)
    val showSignatureDetails = SignaturePrefs.isShowDetailsEnabled(context)
    var showSignatureDialog by remember { mutableStateOf(false) }

    // 从 APK 解析信息
    LaunchedEffect(installUri) {
        if (installUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    // 只物化一次文件：APK 信息解析与签名校验共用同一份缓存文件
                    val filePath = getFilePathFromUri(context, installUri)
                    if (filePath == null) {
                        Log.e("InstallDialog", "Failed to materialize install file")
                        return@withContext
                    }
                    materializedPath = filePath
                    val apkInfo = parseApkInfo(context, filePath)
                    if (apkInfo != null) {
                        // 先展示 APK 信息，完整的 apksig 签名校验在后台完成后回填
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

                    // 仅在开启“安装前校验签名”时才做完整校验（apksig 需要读取整个 APK）
                    if (checkSignature) {
                        val signature = runCatching {
                            computeDialogSignature(context, filePath, apkInfo?.packageName)
                        }.getOrNull()
                        if (signature != null) {
                            state = state.copy(signature = signature)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("InstallDialog", "Failed to parse APK", e)
                } finally {
                    // 解析结束（成功或失败）后收起加载动画
                    state = state.copy(isLoading = false)
                }
            }
        } else {
            // 无安装包来源（installUri 为空）时同样结束加载态，避免一直停在加载动画
            state = state.copy(isLoading = false)
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
                // 加载安装包信息期间：整卡切换为加载动画（参考 InstallerX 的 Preparing/Analysing 阶段）
                if (state.isLoading) {
                    PackageLoadingIndicator()
                    return@Column
                }

                // 应用信息区域 - 始终显示
                InstallInfoHeader(state = state)

                val sigSummary = if (checkSignature) state.signature else null
                if (sigSummary != null && sigSummary.applicable) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SignatureStatusRow(
                        summary = sigSummary,
                        showDetails = showSignatureDetails,
                        onClick = { showSignatureDialog = true }
                    )
                }
                
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
                                    filePath = materializedPath ?: getFilePathFromUri(context, installUri),
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

    // 签名详情对话框
    if (showSignatureDialog) {
        val sig = state.signature
        if (sig != null) {
            SignatureDetailsDialog(
                summary = sig,
                onDismiss = { showSignatureDialog = false }
            )
        }
    }
}

/**
 * 加载安装包信息的动画（物化文件 + 解析 APK 期间展示，参考 InstallerX 的 Preparing 阶段）
 * 图形模式使用包含式指示器，波浪模式使用不定量线性波浪条。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PackageLoadingIndicator() {
    val context = LocalContext.current
    val useMonet = LocalThemeStateHolder.current.state.useDynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val indicatorColor = if (useMonet) MaterialTheme.colorScheme.primary else Color(0xFF29B6F6)
    val loaderMode = LoaderAnimationPrefs.getMode(context)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (loaderMode) {
            LoaderAnimationMode.GRAPHIC -> {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(40.dp),
                    indicatorColor = indicatorColor,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }
            LoaderAnimationMode.WAVE -> {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = indicatorColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.preparing),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    rememberDrawablePainter(drawable = ContextCompat.getDrawable(
                        LocalContext.current,
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
                text = stringResource(R.string.package_name_label_colon) + (state.packageName.ifEmpty { stringResource(R.string.default_package_name) }),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 当前版本 - 14sp
            Text(
                text = stringResource(R.string.current_version_label) + state.installedVersion,
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
                text = stringResource(R.string.upgrade_version_label) + state.version,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 最低SDK - 14sp
            Text(
                text = stringResource(R.string.min_sdk_label) + state.minSdk,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            // 目标SDK - 14sp
            Text(
                text = stringResource(R.string.target_sdk_label) + state.targetSdk,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InstallingButtons(
    progress: Int,
    onCancel: () -> Unit
) {
    // 默认回退色：淡蓝 0xFF29B6F6；若启用壁纸莫奈动态色(Android 12+)，则跟随主题 primary
    val useMonet = LocalThemeStateHolder.current.state.useDynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val indicatorColor = if (useMonet) MaterialTheme.colorScheme.primary else Color(0xFF29B6F6)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    // 平滑过渡进度值（与 InstallerX 安装对话框一致）
    val animatedProgress by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "InstallProgressAnimation"
    )

    val loaderMode = LoaderAnimationPrefs.getMode(LocalContext.current)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        when (loaderMode) {
            LoaderAnimationMode.GRAPHIC -> {
                // 图形加载动画 - Material 3 包含式加载指示器（中心稳定，无漂移）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator(
                        modifier = Modifier.size(40.dp),
                        indicatorColor = indicatorColor,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }
            }
            LoaderAnimationMode.WAVE -> {
                // 安装进度 - 线性波浪进度条（Material 3 Expressive，参考 InstallerX 安装对话框）
                if (progress > 0) {
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = indicatorColor,
                        trackColor = trackColor
                    )
                } else {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = indicatorColor,
                        trackColor = trackColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
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
 *
 * @param path 已物化到本地的 APK 文件路径（由 [getFilePathFromUri] 提供）
 */
private fun parseApkInfo(context: Context, path: String): ApkInfo? {
    return try {
        val pm = context.packageManager

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
        val versionName = packageInfo.versionName ?: context.getString(R.string.unknown)
        val versionCode = packageInfo.longVersionCode
        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo.minSdkVersion.toString()
        } else context.getString(R.string.not_available)
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
        
        // 未安装时显示“全新安装”，已安装时显示“版本名 (版本号)”
        val installedVersion = installedPkg?.let {
            "${it.versionName ?: context.getString(R.string.unknown)} (${it.longVersionCode})"
        } ?: context.getString(R.string.fresh_install)
        
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
        onError(context.getString(R.string.cannot_access_install_file))
        return
    }

    val isXapk = XapkInstaller.isXapkFile(filePath)

    val logManager = LogManager.getInstance()
    val mainHandler = Handler(Looper.getMainLooper())

    // 智能授权：按安装状态选首选 + 回退列表逐个尝试
    val currentAuthorizer = if (mode == PrivilegeHelper.PrivilegeMode.DHIZUKU) {
        Authorizer.Dhizuku
    } else {
        Authorizer.Shizuku
    }
    val installed = PackageInfoHelper.isApkInstalled(context, filePath)
    val ordered: List<Authorizer> =
        SmartAuthorizer.resolveInstallPlan(context, currentAuthorizer, installed)

    if (ordered.isEmpty()) {
        onError(context.getString(R.string.install_failed, "no available authorizer"))
        return
    }

    Thread {
        var lastError: String? = null

        for (authorizer in ordered) {
            val latch = CountDownLatch(1)
            var succeeded = false
            var errorMessage: String? = null

            InstallDispatcher.install(
                context = context,
                authorizer = authorizer,
                filePath = filePath,
                isXapk = isXapk,
                replaceExisting = true,
                grantPermissions = true,
                callback = object : InstallDispatcher.Callback {
                    override fun onProgress(message: String) {
                        Log.d("InstallDialog", message)
                        logManager.addLog(message, "Dialog")
                    }

                    override fun onSuccess(message: String) {
                        Log.d("InstallDialog", message)
                        logManager.addLog(message, "Dialog")
                        succeeded = true
                        latch.countDown()
                    }

                    override fun onError(error: String) {
                        Log.e("InstallDialog", error)
                        logManager.addLog("Error: $error", "Dialog")
                        errorMessage = error
                        latch.countDown()
                    }
                }
            )

            latch.await()

            if (succeeded) {
                mainHandler.post { onSuccess() }
                return@Thread
            } else {
                lastError = errorMessage
            }
        }

        val finalError = lastError ?: context.getString(R.string.install_failed, "")
        mainHandler.post { onError(finalError) }
    }.start()
}

/**
 * 快速解析对话框待安装文件的签名摘要（XAPK/APKS 标记为不适用）。
 */
private fun computeDialogSignature(
    context: Context,
    filePath: String,
    packageName: String?
): SignatureSummary {
    if (XapkInstaller.isXapkFile(filePath)) {
        return SignatureSummary(
            applicable = false,
            status = SignatureMatchStatus.NOT_INSTALLED,
            packageName = null,
            apkSha256 = null,
            apkSignature = null,
            installedSignature = null,
        )
    }

    val result = SignatureHelper.match(context, File(filePath), packageName)
    return SignatureSummary(
        applicable = true,
        status = result.status,
        packageName = packageName,
        apkSha256 = result.apkSha256,
        apkSignature = result.apkSignature,
        installedSignature = result.installedSignature,
    )
}

/**
 * 从 URI 获取文件路径。
 *
 * content:// 等非 file 协议会复制到缓存目录，并**保留原始扩展名**——
 * 扩展名参与 XAPK/APKS 判定，丢失后会被误当作普通 APK 处理。
 */
private fun getFilePathFromUri(context: Context, uri: Uri?): String? {
    if (uri == null) return null
    return try {
        if (uri.scheme == "file") return uri.path

        clearStaleInstallTempFiles(context)

        val suffix = queryDisplayName(context, uri)
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?.let { ".${it.lowercase()}" }
            ?: ".apk"
        val cacheFile = File(context.cacheDir, "temp_install_${System.currentTimeMillis()}$suffix")

        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
            true
        } ?: false

        if (!copied) {
            cacheFile.delete()
            Log.e("InstallDialog", "Failed to open install file: $uri")
            return null
        }
        cacheFile.absolutePath
    } catch (e: Exception) {
        Log.e("InstallDialog", "Failed to get file path from URI", e)
        null
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
} catch (e: Exception) {
    null
} ?: uri.lastPathSegment

/**
 * 清理上一次选择遗留的安装临时文件，避免缓存目录被反复复制的安装包占满。
 */
private fun clearStaleInstallTempFiles(context: Context) {
    runCatching {
        context.cacheDir.listFiles { file ->
            file.isFile && (file.name.startsWith("temp_install_") || file.name == "temp_apk.apk")
        }?.forEach { it.delete() }
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
                val dhizukuPackage = PrivilegeHelper.getInstalledDhizukuPackage(context)
                if (dhizukuPackage != null) {
                    val dhizukuInfo = pm.getPackageInfo(dhizukuPackage, 0)
                    dhizukuIcon = dhizukuInfo.applicationInfo?.loadIcon(pm)
                } else {
                    dhizukuIcon = null
                }
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
                                        rememberDrawablePainter(drawable = ContextCompat.getDrawable(
                                            context,
                                            R.drawable.ic_warning
                                        ))
                                    },
                                    contentDescription = stringResource(R.string.shizuku),
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.shizuku),
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
                                        rememberDrawablePainter(drawable = ContextCompat.getDrawable(
                                            context,
                                            R.drawable.ic_warning
                                        ))
                                    },
                                    contentDescription = stringResource(R.string.dhizuku),
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.dhizuku),
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

@Composable
private fun getPrivilegeStatusText(status: PrivilegeHelper.PrivilegeStatus?): String {
    return when (status) {
        PrivilegeHelper.PrivilegeStatus.AUTHORIZED -> stringResource(R.string.privilege_status_authorized)
        PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED -> stringResource(R.string.privilege_status_not_authorized)
        PrivilegeHelper.PrivilegeStatus.NOT_INSTALLED -> stringResource(R.string.privilege_status_not_installed)
        PrivilegeHelper.PrivilegeStatus.NOT_RUNNING -> stringResource(R.string.privilege_status_not_running)
        PrivilegeHelper.PrivilegeStatus.VERSION_TOO_LOW -> stringResource(R.string.privilege_status_version_too_low)
        null -> stringResource(R.string.checking)
    }
}
