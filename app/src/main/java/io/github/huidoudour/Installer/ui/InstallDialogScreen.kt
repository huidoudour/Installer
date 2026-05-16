package io.github.huidoudour.Installer.ui

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
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
    // 动态颜色
    val primaryColor: Color = Color(0xFF6750A4),
    val onPrimaryColor: Color = Color.White
)

/**
 * Compose 安装对话框
 * 保持与原Java版本相同的UI布局，仅添加动态取色功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallDialogScreen(
    installUri: Uri?,
    onDismiss: () -> Unit,
    onInstallComplete: () -> Unit,
    onOpenApp: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(InstallDialogState()) }
    
    // 从 APK 图标提取颜色
    LaunchedEffect(installUri) {
        if (installUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val apkInfo = parseApkInfo(context, installUri)
                    if (apkInfo != null) {
                        val colors = extractColorsFromIcon(context, apkInfo.appIcon)
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
                            primaryColor = colors.primary,
                            onPrimaryColor = colors.onPrimary
                        )
                    }
                } catch (e: Exception) {
                    Log.e("InstallDialog", "Failed to parse APK", e)
                }
            }
        }
    }
    
    // 动态颜色动画
    val animatedPrimary by animateColorAsState(
        targetValue = state.primaryColor,
        label = "primaryColor"
    )
    val animatedOnPrimary by animateColorAsState(
        targetValue = state.onPrimaryColor,
        label = "onPrimaryColor"
    )

    // 最外层容器 - 对应原XML最外层LinearLayout (paddingTop=8dp, paddingBottom=8dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        // 圆角对话框容器 - 对应 MaterialCardView
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
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    state.isComplete -> {
                        // 安装完成界面
                        CompletionContent(
                            state = state,
                            animatedPrimary = animatedPrimary,
                            onOpenApp = { onOpenApp(state.packageName) },
                            onFinish = onDismiss,
                            onBack = {
                                state = state.copy(isComplete = false)
                            }
                        )
                    }
                    state.isInstalling -> {
                        // 安装进度界面
                        InstallingContent(
                            progress = state.installProgress,
                            animatedPrimary = animatedPrimary,
                            onCancel = {
                                state = state.copy(isInstalling = false)
                            }
                        )
                    }
                    else -> {
                        // 安装信息界面 - 与原UI保持一致
                        InstallInfoContent(
                            state = state,
                            animatedPrimary = animatedPrimary,
                            onInstall = {
                                state = state.copy(isInstalling = true)
                                simulateInstallation { progress ->
                                    if (progress >= 100) {
                                        state = state.copy(
                                            isInstalling = false,
                                            isComplete = true,
                                            installProgress = 100
                                        )
                                    } else {
                                        state = state.copy(installProgress = progress)
                                    }
                                }
                            },
                            onCancel = onDismiss,
                            onPrivilege = { /* 显示权限选择 */ }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 安装信息内容 - 完全按照原XML布局实现
 */
@Composable
fun InstallInfoContent(
    state: InstallDialogState,
    animatedPrimary: Color,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onPrivilege: () -> Unit
) {
    val onPrimaryColor = if (isColorDark(animatedPrimary)) Color.White else Color.Black
    
    // 应用图标和名称区域 - 完全按照原XML
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 应用图标 - 48dp，scaleType=centerInside
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (state.appIcon != null) {
                Image(
                    painter = rememberDrawablePainter(drawable = state.appIcon),
                    contentDescription = stringResource(R.string.app_icon_description),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit  // 对应 centerInside
                )
            } else {
                Icon(
                    imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_terminal),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 应用名称 - weight(1f) 对应 layout_weight=1
        Text(
            text = state.appName.ifEmpty { stringResource(R.string.unknown_app) },
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,  // 原XML是22sp
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)  // 关键：对应 layout_weight=1
        )
    }
    
    // 应用信息区域 - 居中显示，完全按照原XML
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 包名 - 14sp，onSurfaceVariant
        Text(
            text = stringResource(R.string.package_name_label) + state.packageName,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // 当前版本 - 14sp，onSurface
        Text(
            text = stringResource(R.string.current_version) + state.installedVersion.ifEmpty { stringResource(R.string.not_installed) },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        // 下箭头 - 24dp，marginVertical=4dp
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = stringResource(R.string.arrow_down),
            modifier = Modifier
                .size(24.dp)
                .padding(vertical = 4.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // 升级版本 - 14sp，onSurface
        if (state.upgradeVersion.isNotEmpty()) {
            Text(
                text = stringResource(R.string.upgrade_version) + state.upgradeVersion,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // 最低SDK - 14sp，onSurfaceVariant
        Text(
            text = stringResource(R.string.min_sdk) + state.minSdk,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        // 目标SDK - 14sp，onSurfaceVariant
        Text(
            text = stringResource(R.string.target_sdk) + state.targetSdk,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
    
    // 按钮区域 - 完全按照原XML
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 安装按钮 - match_parent，48dp，14sp粗体，cornerRadius=16dp
        Button(
            onClick = onInstall,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = animatedPrimary,
                contentColor = onPrimaryColor
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp
            )
        ) {
            Text(
                text = if (state.isUpgrade) stringResource(R.string.upgrade) else stringResource(R.string.install),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 按钮行：权限和取消 - 各占一半，权限按钮marginEnd=8dp
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 权限按钮 - weight(1f)，48dp，14sp，cornerRadius=12dp
            Button(
                onClick = onPrivilege,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = animatedPrimary.copy(alpha = 0.8f),
                    contentColor = onPrimaryColor
                )
            ) {
                Text(
                    text = stringResource(R.string.privilege),
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 取消按钮 - weight(1f)，48dp，14sp，cornerRadius=12dp，OutlinedButton
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline
                )
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
 * 安装进度内容 - 完全按照原XML布局实现
 */
@Composable
fun InstallingContent(
    progress: Int,
    animatedPrimary: Color,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 线性进度条 - trackThickness=8dp, trackCornerRadius=4dp
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(8.dp),  // 对应 trackThickness=8dp
            color = animatedPrimary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp  // 对应 trackCornerRadius=4dp
        )
        
        // 取消按钮 - match_parent, 48dp, 14sp, cornerRadius=16dp, OutlinedButton
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline
            )
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
 * 安装完成内容 - 完全按照原XML布局实现
 */
@Composable
fun CompletionContent(
    state: InstallDialogState,
    animatedPrimary: Color,
    onOpenApp: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 打开应用按钮 - match_parent, 48dp, 14sp粗体, cornerRadius=16dp
        Button(
            onClick = onOpenApp,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = animatedPrimary,
                contentColor = if (isColorDark(animatedPrimary)) Color.White else Color.Black
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp
            )
        ) {
            Text(
                text = stringResource(R.string.open_app),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 按钮行：返回和完成 - 各占一半
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 返回按钮 - weight(1f), 48dp, 14sp, cornerRadius=12dp, OutlinedButton
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text = stringResource(R.string.back),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 完成按钮 - weight(1f), 48dp, 14sp, cornerRadius=12dp
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = animatedPrimary.copy(alpha = 0.8f),
                    contentColor = if (isColorDark(animatedPrimary)) Color.White else Color.Black
                )
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
 * 模拟安装过程
 */
private fun simulateInstallation(onProgress: (Int) -> Unit) {
    Thread {
        for (i in 0..100 step 5) {
            Thread.sleep(200)
            onProgress(i)
        }
    }.start()
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
 * 提取的颜色
 */
data class ExtractedColors(
    val primary: Color,
    val onPrimary: Color
)

/**
 * 解析 APK 信息
 */
private fun parseApkInfo(context: Context, uri: Uri): ApkInfo? {
    return try {
        val pm = context.packageManager
        val path = uri.path ?: return null
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
        
        val isUpgrade = installedPkg != null
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
 * 从图标提取颜色
 * 使用 Palette 库从位图提取主色调
 */
private fun extractColorsFromIcon(context: Context, drawable: Drawable?): ExtractedColors {
    if (drawable == null) {
        return defaultColors()
    }
    
    return try {
        val bitmap: Bitmap = when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            else -> {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 100
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 100
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    val canvas = android.graphics.Canvas(it)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
        }
        
        val palette = Palette.from(bitmap).generate()
        
        val dominant = palette.dominantSwatch
        val vibrant = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch
        
        // 使用主色调作为主色
        val primaryColor = dominant?.let { Color(it.rgb) } 
            ?: vibrant?.let { Color(it.rgb) }
            ?: Color(0xFF6750A4)
        
        // 计算适合的文本颜色
        val onPrimary = if (isColorDark(primaryColor)) Color.White else Color.Black
        
        ExtractedColors(
            primary = primaryColor,
            onPrimary = onPrimary
        )
    } catch (e: Exception) {
        Log.e("InstallDialog", "Failed to extract colors", e)
        defaultColors()
    }
}

/**
 * 判断颜色是否为深色
 */
private fun isColorDark(color: Color): Boolean {
    val darkness = 1 - (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return darkness >= 0.5
}

/**
 * 默认颜色
 */
private fun defaultColors() = ExtractedColors(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White
)
