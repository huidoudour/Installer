package io.github.huidoudour.Installer.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Text
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
    val isInfoLoaded: Boolean = false, // 标记信息是否已加载完成
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
                            onPrimaryColor = colors.onPrimary,
                            isInfoLoaded = true // 标记信息已加载
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
            .padding(top = 12.dp, bottom = 12.dp)
    ) {
        // 圆角对话框容器 - 对应 MaterialCardView
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            // Card内部容器 - 对应LinearLayout (padding=16dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
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
                            animatedPrimary = animatedPrimary,
                            onCancel = {
                                state = state.copy(isInstalling = false)
                            }
                        )
                    }
                    else -> {
                        // 安装确认按钮
                        InstallButtons(
                            state = state,
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
 * 安装信息头部 - 图标、名称、版本等信息（始终显示）
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
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 应用图标 - 56dp，scaleType=centerInside
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                if (state.appIcon != null) {
                    Image(
                        painter = rememberDrawablePainter(drawable = state.appIcon),
                        contentDescription = stringResource(R.string.app_icon_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_package),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            // 应用名称
            Text(
                text = state.appName.ifEmpty { stringResource(R.string.unknown_app) },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        
        // 应用信息区域 - 加载完成后展开显示
        AnimatedVisibility(
            visible = state.isInfoLoaded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 300),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 300),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 包名
                Text(
                    text = stringResource(R.string.package_name_label) + state.packageName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                
                // 当前版本
                Text(
                    text = stringResource(R.string.current_version) + state.installedVersion.ifEmpty { stringResource(R.string.not_installed) },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                // 下箭头
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.arrow_down),
                    modifier = Modifier
                        .size(28.dp)
                        .padding(vertical = 6.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 升级版本
                Text(
                    text = stringResource(R.string.upgrade_version) + state.version,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                
                // 最低SDK
                Text(
                    text = stringResource(R.string.min_sdk) + state.minSdk,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                // 目标SDK
                Text(
                    text = stringResource(R.string.target_sdk) + state.targetSdk,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 安装确认按钮区域
 */
@Composable
fun InstallButtons(
    state: InstallDialogState,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onPrivilege: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 安装按钮 - 单个按钮全宽显示，使用绿色（button_secondary），圆角16dp，高度52dp
        Button(
            onClick = onInstall,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),  // button_secondary 绿色
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = if (state.isUpgrade) stringResource(R.string.upgrade) else stringResource(R.string.install),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // 按钮行：权限和取消 - 成对按钮，等高显示，高度52dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            // 权限按钮 - 使用蓝色（button_primary），圆角12dp
            Button(
                onClick = onPrivilege,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3),  // button_primary 蓝色
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.privilege),
                    fontSize = 15.sp
                )
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            // 取消按钮 - OutlinedButton，圆角12dp，边框2dp
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline
                ),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * 安装进度按钮区域
 */
@Composable
fun InstallingButtons(
    progress: Int,
    animatedPrimary: Color,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 线性进度条 - indeterminate模式，trackThickness=8dp, trackCornerRadius=4dp
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            color = animatedPrimary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp
        )
        
        // 取消按钮 - OutlinedButton，圆角16dp，边框2dp，高度52dp
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline
            ),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = stringResource(R.string.cancel),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 安装完成按钮区域
 */
@Composable
fun CompletionButtons(
    onOpenApp: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 打开应用按钮 - 单个按钮全宽显示，使用蓝色（button_primary），圆角16dp，高度52dp
        Button(
            onClick = onOpenApp,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2196F3),  // button_primary 蓝色
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = stringResource(R.string.open_app),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // 按钮行：返回和完成 - 成对按钮，等高显示，高度52dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            // 返回按钮 - OutlinedButton，圆角12dp，边框2dp
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline
                ),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.back),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            // 完成按钮 - 使用绿色（button_secondary），圆角12dp
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),  // button_secondary 绿色
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.finish),
                    fontSize = 15.sp
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
