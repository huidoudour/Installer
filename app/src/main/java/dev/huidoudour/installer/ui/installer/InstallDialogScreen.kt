package dev.huidoudour.installer.ui.installer

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import dev.huidoudour.installer.R
import dev.huidoudour.installer.auth.Authorizer
import dev.huidoudour.installer.auth.InstallDispatcher
import dev.huidoudour.installer.auth.PrivilegeHelper
import dev.huidoudour.installer.auth.SmartAuthorizer
import dev.huidoudour.installer.install.InstallCacheCleaner
import dev.huidoudour.installer.install.InstallFailureAnalysis
import dev.huidoudour.installer.install.InstallFailureAnalyzer
import dev.huidoudour.installer.install.InstallFailureHints
import dev.huidoudour.installer.install.MultiApkZip
import dev.huidoudour.installer.install.PackageInfoHelper
import dev.huidoudour.installer.install.XapkInstaller
import dev.huidoudour.installer.ui.theme.LocalThemeStateHolder
import dev.huidoudour.installer.util.LogManager
import dev.huidoudour.installer.util.SignaturePrefs
import dev.huidoudour.installer.util.signature.SignatureHelper
import dev.huidoudour.installer.util.signature.SignatureMatchStatus
import dev.huidoudour.installer.util.signature.SignatureSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 待安装包与已安装包之间的版本关系，用于决定安装按钮文案。
 */
enum class VersionRelation {
    /** 尚未安装：全新安装 */
    FRESH_INSTALL,

    /** 待安装版本号高于已安装版本：升级 */
    UPGRADE,

    /** 版本号相同：重装 */
    REINSTALL,

    /** 待安装版本号低于已安装版本：降级（仅尝试，能否成功取决于系统） */
    DOWNGRADE
}

/**
 * 安装对话框状态
 */
data class InstallDialogState(
    val appName: String = "",
    val packageName: String = "",
    val version: String = "",
    val minSdk: String = "",
    val targetSdk: String = "",
    val appIcon: Drawable? = null,
    val versionRelation: VersionRelation = VersionRelation.FRESH_INSTALL,
    val installedVersion: String = "",
    val isInstalling: Boolean = false,
    val installProgress: Int = 0,
    val isComplete: Boolean = false,
    val errorMessage: String? = null,
    val failureAnalysis: InstallFailureAnalysis? = null,
    val signature: SignatureSummary? = null,
    val isInfoLoaded: Boolean = false,
    // 正在物化文件 / 解析安装包信息（用于展示加载动画）
    val isLoading: Boolean = true,
    // ZIP 内多个独立 APK 时：压缩包文件名与逐个安装的条目
    val fileName: String = "",
    val multiApkItems: List<MultiApkItem> = emptyList()
)

/** 多 APK 压缩包内单个 APK 的安装状态 */
enum class MultiApkStatus {
    PENDING,
    INSTALLING,
    SUCCESS,
    FAILED
}

/** 多 APK 压缩包内单个 APK 的展示与安装状态 */
data class MultiApkItem(
    val entry: MultiApkZip.Entry,
    val status: MultiApkStatus = MultiApkStatus.PENDING,
    val error: String? = null
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
    onOpenApp: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val logManager = LogManager.getInstance()
    fun logUiAction(action: String) = logManager.addLog(action, "External install UI")
    var state by remember { mutableStateOf(InstallDialogState()) }

    // 多 APK 逐个安装的取消标记：取消后不再开始下一个 APK（已开始的会话无法中断）
    val multiApkCancelFlag = remember { AtomicBoolean(false) }

    // installUri 物化到本地后的文件路径（APK 信息解析、签名校验、安装共用同一份缓存文件）
    var materializedPath by remember { mutableStateOf<String?>(null) }

    // 权限模式
    var currentPrivilegeMode by remember { mutableStateOf(PrivilegeHelper.getCurrentMode(context)) }
    var showPrivilegeDialog by remember { mutableStateOf(false) }

    // 当前授权模式的开关：关闭时不使用该特权授权器，隐藏权限选择入口
    val usePrivilegedAuthorizers = remember(currentPrivilegeMode) {
        PrivilegeHelper.isModeEnabled(context, currentPrivilegeMode)
    }

    // 签名校验
    val checkSignature = SignaturePrefs.isCheckEnabled(context)
    val showSignatureDetails = SignaturePrefs.isShowDetailsEnabled(context)
    var showSignatureDialog by remember { mutableStateOf(false) }

    // 从 APK 解析信息
    LaunchedEffect(installUri) {
        if (installUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    logManager.addLog("Preparing external install file: $installUri", "External install")
                    // 只物化一次文件：APK 信息解析与签名校验共用同一份缓存文件
                    val filePath = getFilePathFromUri(context, installUri)
                    if (filePath == null) {
                        Log.e("InstallDialog", "Failed to materialize install file")
                        logManager.addLog("Failed to prepare external install file", "External install")
                        return@withContext
                    }
                    materializedPath = filePath
                    logManager.addLog("Install file ready: $filePath", "External install")

                    // 内容校验：既不是 APK，也不是含 APK 的容器时直接拒绝，
                    // 避免把普通 ZIP 当成安装包继续走下去
                    if (!XapkInstaller.isXapkFile(filePath) && !XapkInstaller.isApkFile(filePath)) {
                        logManager.addLog(
                            "Rejected file is neither APK nor package archive: ${File(filePath).name}",
                            "External install"
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.unsupported_file_type),
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }
                        return@withContext
                    }

                    // ZIP 内多个“独立 APK”：解压后逐个安装，不再按分包容器处理
                    val multiApkEntries = MultiApkZip.extract(context, filePath)
                    if (multiApkEntries != null) {
                        state = state.copy(
                            fileName = File(filePath).name,
                            multiApkItems = multiApkEntries.map { MultiApkItem(it) },
                            isInfoLoaded = true
                        )
                        logManager.addLog(
                            "Multi-APK archive ready: ${multiApkEntries.size} independent APKs",
                            "External install"
                        )
                        return@withContext
                    }

                    val apkInfo = parseApkInfo(context, filePath)
                    if (apkInfo != null) {
                        // 先展示 APK 信息，完整的 apksig 签名校验在后台完成后回填
                        state = state.copy(
                            appName = apkInfo.appName,
                            packageName = apkInfo.packageName,
                            version = apkInfo.version,
                            minSdk = apkInfo.minSdk,
                            targetSdk = apkInfo.targetSdk,
                            appIcon = apkInfo.appIcon,
                            versionRelation = apkInfo.versionRelation,
                            installedVersion = apkInfo.installedVersion,
                            isInfoLoaded = true
                        )
                        logManager.addLog(
                            "Package parsed: ${apkInfo.packageName} ${apkInfo.version}",
                            "External install"
                        )
                    } else {
                        logManager.addLog("Could not parse package information", "External install")
                    }

                    // 仅在开启“安装前校验签名”时才做完整校验（apksig 需要读取整个 APK）
                    if (checkSignature) {
                        val signature = runCatching {
                            computeDialogSignature(context, filePath, apkInfo?.packageName)
                        }.getOrNull()
                        if (signature != null) {
                            state = state.copy(signature = signature)
                            logManager.addLog("Package signature check completed", "External install")
                        } else {
                            logManager.addLog("Package signature check could not be completed", "External install")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("InstallDialog", "Failed to parse APK", e)
                    logManager.addLog("Package preparation failed: ${e.message}", "External install")
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
        // 当前授权模式的开关关闭时不使用特权授权器，无需等待 Shizuku/Dhizuku 授权
        if (!PrivilegeHelper.isModeEnabled(context, currentPrivilegeMode)) return true
        val status = PrivilegeHelper.getStatus(context, currentPrivilegeMode)
        return status == PrivilegeHelper.PrivilegeStatus.AUTHORIZED
    }

    /**
     * 逐个安装多 APK 压缩包内的每个 APK：
     * 每个 APK 独立构建安装会话，前一个结束（成功或失败）后再安装下一个，
     * 单个失败不会中断后续 APK；失败后重试时跳过已安装成功的条目。
     */
    fun startMultiApkInstallation() {
        val items = state.multiApkItems
        // 首次安装时全部为 PENDING；重试时只重新安装未成功的条目
        val targetIndexes = items.indices
            .filter { items[it].status != MultiApkStatus.SUCCESS }
            .ifEmpty { items.indices.toList() }
        multiApkCancelFlag.set(false)
        state = state.copy(
            isInstalling = true,
            installProgress = 0,
            errorMessage = null,
            failureAnalysis = null,
            multiApkItems = items.mapIndexed { index, item ->
                if (targetIndexes.contains(index)) {
                    item.copy(status = MultiApkStatus.PENDING, error = null)
                } else {
                    item
                }
            }
        )
        performMultiApkInstallation(
            context = context,
            items = items,
            targetIndexes = targetIndexes,
            mode = currentPrivilegeMode,
            isCancelled = { multiApkCancelFlag.get() },
            onItemStatus = { index, status, error ->
                state = state.copy(
                    multiApkItems = state.multiApkItems.mapIndexed { i, item ->
                        if (i == index) item.copy(status = status, error = error) else item
                    }
                )
            },
            onItemFinished = { completed ->
                state = state.copy(installProgress = completed * 100 / targetIndexes.size)
            },
            onComplete = { successCount, errors, cancelled ->
                if (cancelled) {
                    state = state.copy(isInstalling = false)
                    logManager.addLog(
                        "Multi-APK installation cancelled: $successCount/${targetIndexes.size} succeeded",
                        "External install"
                    )
                    return@performMultiApkInstallation
                }
                if (errors.isEmpty()) {
                    state = state.copy(
                        isInstalling = false,
                        isComplete = true,
                        installProgress = 100
                    )
                } else {
                    val analysis = InstallFailureAnalyzer.analyze(
                        context = context,
                        errors = errors,
                        hints = InstallFailureHints()
                    )
                    state = state.copy(
                        isInstalling = false,
                        errorMessage = analysis.technicalDetail,
                        failureAnalysis = analysis
                    )
                    logManager.addLog(
                        "Multi-APK installation failure analyzed: ${analysis.category}" +
                            " | reason: ${analysis.reason}",
                        "External install"
                    )
                    Toast.makeText(context, analysis.reason, Toast.LENGTH_LONG).show()
                }
                logManager.addLog(
                    "Multi-APK installation finished: $successCount/${targetIndexes.size} succeeded",
                    "External install"
                )
            }
        )
    }

    /**
     * 启动一次安装尝试：收集安装前已知的事实作为失败分析的依据，
     * 失败时由 performRealInstallation 汇总所有授权方式的错误后给出分析结果。
     */
    fun startInstallation() {
        // ZIP 内多个独立 APK 需要逐个安装，不能进入分包会话流程
        if (state.multiApkItems.isNotEmpty()) {
            startMultiApkInstallation()
            return
        }

        val isDowngrade = state.versionRelation == VersionRelation.DOWNGRADE
        if (isDowngrade) {
            Toast.makeText(
                context,
                context.getString(R.string.downgrade_attempt_hint),
                Toast.LENGTH_LONG
            ).show()
        }
        val installFilePath = materializedPath
        if (installFilePath == null) {
            Toast.makeText(
                context,
                context.getString(R.string.cannot_access_install_file),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val hints = InstallFailureHints(
            packageName = state.packageName.takeIf { it.isNotBlank() },
            isDowngradeAttempt = isDowngrade,
            signatureMismatch = checkSignature &&
                state.signature?.status == SignatureMatchStatus.MISMATCH,
        )
        state = state.copy(
            isInstalling = true,
            errorMessage = null,
            failureAnalysis = null
        )
        performRealInstallation(
            context = context,
            filePath = installFilePath,
            installedPackageName = state.packageName,
            mode = currentPrivilegeMode,
            allowDowngrade = isDowngrade,
            hints = hints,
            onSuccess = {
                state = state.copy(
                    isInstalling = false,
                    isComplete = true,
                    installProgress = 100
                )
            },
            onError = { analysis ->
                state = state.copy(
                    isInstalling = false,
                    errorMessage = analysis.technicalDetail,
                    failureAnalysis = analysis
                )
                logManager.addLog(
                    "Installation failure analyzed: ${analysis.category}" +
                        " | reason: ${analysis.reason}",
                    "External install"
                )
                Toast.makeText(context, analysis.reason, Toast.LENGTH_LONG).show()
            }
        )
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
                    // 失败分析卡会让内容变高，允许滚动以保证原因和建议始终可见
                    .verticalScroll(rememberScrollState())
            ) {
                // 加载安装包信息期间：整卡切换为加载动画（参考 InstallerX 的 Preparing/Analysing 阶段）
                if (state.isLoading) {
                    PackageLoadingIndicator()
                    return@Column
                }

                // 应用信息区域：多 APK 压缩包展示逐个安装清单，单 APK 显示原有信息头
                if (state.multiApkItems.isNotEmpty()) {
                    MultiApkHeader(
                        fileName = state.fileName,
                        count = state.multiApkItems.size
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MultiApkList(items = state.multiApkItems)
                } else {
                    InstallInfoHeader(state = state)
                }

                val sigSummary = if (checkSignature) state.signature else null
                if (sigSummary != null && sigSummary.applicable) {
                    Spacer(modifier = Modifier.height(2.dp))
                    SignatureStatusRow(
                        summary = sigSummary,
                        showDetails = showSignatureDetails,
                        onClick = {
                            logUiAction("Signature details opened")
                            showSignatureDialog = true
                        }
                    )
                }
                
                val failure = state.failureAnalysis
                if (failure != null && !state.isInstalling && !state.isComplete) {
                    Spacer(modifier = Modifier.height(4.dp))
                    InstallFailureCard(analysis = failure)
                }

                // 多 APK 逐个安装时，进度条下方展示当前正在安装的条目
                val installingIndex = state.multiApkItems.indexOfFirst {
                    it.status == MultiApkStatus.INSTALLING
                }
                val multiApkProgressLabel = if (installingIndex >= 0) {
                    val entry = state.multiApkItems[installingIndex].entry
                    stringResource(
                        R.string.multi_apk_install_progress,
                        installingIndex + 1,
                        state.multiApkItems.size,
                        entry.label ?: entry.entryName
                    )
                } else null

                Spacer(modifier = Modifier.height(8.dp))
                
                // 根据状态显示不同的按钮区域
                when {
                    state.isComplete -> {
                        // 安装完成按钮：多 APK 压缩包没有唯一的“打开应用”目标
                        CompletionButtons(
                            showOpenApp = state.multiApkItems.isEmpty(),
                            onOpenApp = {
                                logUiAction("Open installed app pressed: ${state.packageName}")
                                onOpenApp(state.packageName)
                            },
                            onFinish = {
                                logUiAction("Finish pressed after installation")
                                onDismiss()
                            }
                        )
                    }
                    state.isInstalling -> {
                        // 安装进度按钮
                        InstallingButtons(
                            progress = state.installProgress,
                            label = multiApkProgressLabel,
                            onCancel = {
                                logUiAction("Cancel pressed while installation is in progress")
                                // 多 APK 时让后台线程在下一个 APK 前退出（当前会话无法中断）
                                multiApkCancelFlag.set(true)
                                state = state.copy(isInstalling = false)
                            }
                        )
                    }
                    else -> {
                        // 安装确认按钮
                        InstallButtons(
                            state = state,
                            multiApkCount = state.multiApkItems.size,
                            isInstallEnabled = isInstallEnabled(),
                            showPrivilege = usePrivilegedAuthorizers,
                            onInstall = {
                                logUiAction("Install confirmation pressed")
                                startInstallation()
                            },
                            onCancel = {
                                logUiAction("External installation cancelled before start")
                                onDismiss()
                            },
                            onPrivilege = {
                                logUiAction("Privilege selector opened")
                                showPrivilegeDialog = true
                            }
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
            onDismiss = {
                logUiAction("Privilege selector dismissed")
                showPrivilegeDialog = false
            },
            onModeSelected = { mode ->
                currentPrivilegeMode = mode
                PrivilegeHelper.saveCurrentMode(context, mode)
                logUiAction("Privilege mode confirmed: ${PrivilegeHelper.getModeName(mode)}")
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
                onDismiss = {
                    logUiAction("Signature details dismissed")
                    showSignatureDialog = false
                }
            )
        }
    }
}

/**
 * 加载安装包信息的动画（物化文件 + 解析 APK 期间展示，参考 InstallerX 的 Preparing 阶段）
 * 该阶段没有可量化进度，使用 Material 3 图形变形加载动画。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PackageLoadingIndicator() {
    val useMonet = LocalThemeStateHolder.current.state.useDynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val indicatorColor = if (useMonet) MaterialTheme.colorScheme.primary else Color(0xFF29B6F6)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContainedLoadingIndicator(
            modifier = Modifier
            // Material 3 图形变形动画尺寸
            .size(56.dp),
            indicatorColor = indicatorColor,
            containerColor = indicatorColor.copy(alpha = 0.12f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.preparing),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 多 APK 压缩包头部：标题与压缩包文件名
 */
@Composable
private fun MultiApkHeader(fileName: String, count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.multi_apk_archive_title, count),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (fileName.isNotBlank()) {
            Text(
                text = fileName,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * 多 APK 清单：逐个展示压缩包内每个 APK 的信息与安装状态。
 * 外层已负责滚动，这里不再嵌套滚动容器。
 */
@Composable
private fun MultiApkList(items: List<MultiApkItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                MultiApkRow(item = item)
            }
        }
    }
}

/**
 * 多 APK 清单中的单行：图标、应用名、包名/版本/大小与安装状态
 */
@Composable
private fun MultiApkRow(item: MultiApkItem) {
    val context = LocalContext.current
    val entry = item.entry
    val unknown = stringResource(R.string.unknown)
    val packageText = entry.packageName ?: unknown
    val versionText = entry.versionName?.takeIf { it.isNotBlank() } ?: unknown
    val sizeText = Formatter.formatShortFileSize(context, entry.size)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = if (entry.icon != null) {
                rememberDrawablePainter(drawable = entry.icon)
            } else {
                rememberDrawablePainter(
                    drawable = ContextCompat.getDrawable(
                        context,
                        android.R.drawable.sym_def_app_icon
                    )
                )
            },
            contentDescription = stringResource(R.string.app_icon_description),
            modifier = Modifier
                .size(36.dp)
                .padding(end = 10.dp),
            contentScale = ContentScale.Fit
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label ?: entry.entryName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$packageText · v$versionText · $sizeText",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            // 失败时直接给出该 APK 的错误，无需展开继续排查
            val itemError = item.error
            if (item.status == MultiApkStatus.FAILED && !itemError.isNullOrBlank()) {
                Text(
                    text = itemError,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        MultiApkStatusLabel(status = item.status)
    }
}

/**
 * 多 APK 条目的安装状态标签
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MultiApkStatusLabel(status: MultiApkStatus) {
    val (textRes, color) = when (status) {
        MultiApkStatus.PENDING ->
            R.string.multi_apk_status_pending to MaterialTheme.colorScheme.onSurfaceVariant
        MultiApkStatus.INSTALLING ->
            R.string.multi_apk_status_installing to MaterialTheme.colorScheme.primary
        MultiApkStatus.SUCCESS ->
            R.string.multi_apk_status_success to Color(0xFF4CAF50)
        MultiApkStatus.FAILED ->
            R.string.multi_apk_status_failed to MaterialTheme.colorScheme.error
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 状态区与安装中动画互斥：待安装/已安装/失败显示文字，安装中只显示动画
        if (status == MultiApkStatus.INSTALLING) {
            ContainedLoadingIndicator(
                modifier = Modifier.size(18.dp),
                indicatorColor = color,
                containerColor = color.copy(alpha = 0.12f)
            )
        } else {
            Text(
                text = stringResource(textRes),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
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
                .padding(bottom = 4.dp),
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
 * 安装失败分析卡片：展示归类后的失败原因、修复建议，以及可展开的原始错误。
 * 原始错误在多种授权方式连续失败时会包含每一次的报错，便于定位真正原因。
 */
@Composable
fun InstallFailureCard(
    analysis: InstallFailureAnalysis,
    modifier: Modifier = Modifier
) {
    var showDetail by remember(analysis) { mutableStateOf(false) }
    val contentColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.install_failure_analysis_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.install_failure_reason_label),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = analysis.reason,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = contentColor,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.install_failure_suggestions_label),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = analysis.suggestions,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = contentColor,
                modifier = Modifier.padding(top = 2.dp)
            )

            val detail = analysis.technicalDetail
            if (!detail.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDetail = !showDetail }
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(
                            if (showDetail) R.string.close
                            else R.string.install_failure_detail_label
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (showDetail) 180f else 0f)
                    )
                }
                if (showDetail) {
                    Text(
                        text = detail,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * 安装确认按钮区域 - 完全匹配 dialog_install.xml 布局
 */
@Composable
fun InstallButtons(
    state: InstallDialogState,
    multiApkCount: Int = 0,
    isInstallEnabled: Boolean = true,
    showPrivilege: Boolean = true,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onPrivilege: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 依据与已安装版本的版本关系决定按钮文案：升级 / 重装 / 降级 / 安装；
        // 多 APK 压缩包突出待安装数量；上一次安装失败后按钮改为“重试”。
        val installText = if (state.failureAnalysis != null) {
            stringResource(R.string.retry_install)
        } else if (multiApkCount > 0) {
            stringResource(R.string.multi_apk_install_button, multiApkCount)
        } else when (state.versionRelation) {
            VersionRelation.UPGRADE -> stringResource(R.string.upgrade)
            VersionRelation.REINSTALL -> stringResource(R.string.reinstall)
            VersionRelation.DOWNGRADE -> stringResource(R.string.downgrade)
            VersionRelation.FRESH_INSTALL -> stringResource(R.string.install)
        }

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
                text = installText,
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
            // 权限按钮 - 圆角12dp（全局开关关闭时不展示）
            if (showPrivilege) {
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
            }

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
    label: String? = null,
    onCancel: () -> Unit
) {
    // 默认回退色：淡蓝 0xFF29B6F6；若启用壁纸莫奈动态色(Android 12+)，则跟随主题 primary
    val useMonet = LocalThemeStateHolder.current.state.useDynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val indicatorColor = if (useMonet) MaterialTheme.colorScheme.primary else Color(0xFF29B6F6)
    // 平滑过渡进度值，与参考项目的安装进度条一致。
    val animatedProgress by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "InstallProgressAnimation"
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 安装过程始终采用参考 InstallerX 的线性波浪进度条，不受普通加载动画设置影响。
        // 尚未获得可量化进度时，以线性动画&Material 3 图形变形动画表示 PackageInstaller 正在校验和提交。
        if (progress > 0) {
            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = indicatorColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                amplitude = 0f,
                color = indicatorColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            /*
            // Material 3 图形变形动画
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(56.dp),
                    indicatorColor = indicatorColor,
                    containerColor = indicatorColor.copy(alpha = 0.12f)
                )
            }
             */
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 多 APK 逐个安装时展示当前正在安装的条目
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
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
    showOpenApp: Boolean = true,
    onOpenApp: () -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 打开应用按钮 - 48dp高度，圆角16dp；多 APK 压缩包没有唯一的启动目标时隐藏
        if (showOpenApp) {
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
        }
        
        // 完成按钮 - 48dp高度，圆角12dp，占满整行
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
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

/**
 * APK 信息数据类
 */
data class ApkInfo(
    val appName: String,
    val packageName: String,
    val version: String,
    val minSdk: String,
    val targetSdk: String,
    val appIcon: Drawable?,
    val versionRelation: VersionRelation,
    val installedVersion: String
)

/**
 * 解析 APK 信息
 *
 * @param path 已物化到本地的安装文件路径（由 [getFilePathFromUri] 提供），
 *             可以是普通 APK，也可以是 XAPK/APKS/APKM/含 APK 的 ZIP 容器
 */
private fun parseApkInfo(context: Context, path: String): ApkInfo? {
    val isContainer = XapkInstaller.isXapkFile(path)
    var infoApkPath = path

    if (isContainer) {
        // 容器本身不是 APK，先取出基准 APK（base/base-master）再读取应用信息
        val baseApk = XapkInstaller.extractBaseApkForInfo(context, path)
        if (baseApk == null) {
            Log.e("InstallDialog", "No APK found in package container: $path")
            LogManager.getInstance().addLog(
                "No APK found in package container: ${File(path).name}",
                "External install"
            )
            return null
        }
        infoApkPath = baseApk.absolutePath
    }

    return try {
        parseApkInfoFromApk(context, infoApkPath)
    } finally {
        if (isContainer) {
            File(infoApkPath).delete()
        }
    }
}

/**
 * 从单个 APK 文件解析应用信息（容器已提前取出基准 APK）。
 */
private fun parseApkInfoFromApk(context: Context, path: String): ApkInfo? {
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
        } catch (_: Exception) {
            null
        }
        
        // 检查是否已安装
        val installedPkg = try {
            pm.getPackageInfo(packageInfo.packageName, PackageManager.GET_ACTIVITIES)
        } catch (_: Exception) {
            null
        }
        
        // 根据 VersionCode 与已安装版本对比，确定版本关系：升级 / 重装 / 降级 / 全新安装
        val versionRelation = if (installedPkg != null) {
            val installedVersionCode = installedPkg.longVersionCode
            when {
                versionCode > installedVersionCode -> VersionRelation.UPGRADE
                versionCode == installedVersionCode -> VersionRelation.REINSTALL
                else -> VersionRelation.DOWNGRADE
            }
        } else {
            VersionRelation.FRESH_INSTALL
        }
        
        // 未安装时显示“全新安装”，已安装时显示“版本名 (版本号)”
        val installedVersion = installedPkg?.let {
            "${it.versionName ?: context.getString(R.string.unknown)} (${it.longVersionCode})"
        } ?: context.getString(R.string.fresh_install)
        
        ApkInfo(
            appName = appName,
            packageName = packageInfo.packageName,
            version = "$versionName ($versionCode)",
            minSdk = minSdk,
            targetSdk = targetSdk,
            appIcon = icon,
            versionRelation = versionRelation,
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
    installedPackageName: String?,
    mode: PrivilegeHelper.PrivilegeMode,
    allowDowngrade: Boolean,
    hints: InstallFailureHints,
    onSuccess: () -> Unit,
    onError: (InstallFailureAnalysis) -> Unit
) {
    if (filePath == null) {
        onError(
            InstallFailureAnalyzer.analyze(
                context = context,
                errors = listOf(context.getString(R.string.cannot_access_install_file)),
                hints = hints
            )
        )
        return
    }

    val logManager = LogManager.getInstance()
    val mainHandler = Handler(Looper.getMainLooper())

    // 智能授权：全新安装优先 Dhizuku、已安装跟随所选授权方式，失败后按回退列表逐个尝试
    val currentAuthorizer = if (mode == PrivilegeHelper.PrivilegeMode.DHIZUKU) {
        Authorizer.Dhizuku
    } else {
        Authorizer.Shizuku
    }
    // 优先使用对话框已解析出的包名判断安装状态，避免在 UI 线程解压容器
    Thread {
        val isXapk = XapkInstaller.isXapkFile(filePath)
        val installed = if (!installedPackageName.isNullOrBlank()) {
            PackageInfoHelper.isAppInstalled(context, installedPackageName)
        } else {
            PackageInfoHelper.isApkInstalled(context, filePath)
        }
        val ordered: List<Authorizer> =
            SmartAuthorizer.resolveInstallPlan(context, currentAuthorizer, installed)

        if (ordered.isEmpty()) {
            val analysis = InstallFailureAnalyzer.analyze(
                context = context,
                errors = listOf(context.getString(R.string.no_available_authorizer)),
                hints = hints.copy(isContainer = isXapk)
            )
            logManager.addLog(
                "No authorized installation method is available | reason: ${analysis.reason}",
                "External install"
            )
            mainHandler.post { onError(analysis) }
            return@Thread
        }

        // 只使用对话框中选择的授权方式：失败后直接给出原因与建议，不再自动回退/拉起其他安装方式。
        // 所选方式被设置禁用（或被规划剔除）时，才使用解析出的首选项。
        val authorizer = if (currentAuthorizer in ordered) currentAuthorizer else ordered.first()

        // 暂不自动拉起系统安装器：没有可用的特权授权方式时，先给出失败原因与建议
        if (authorizer == Authorizer.None) {
            val analysis = InstallFailureAnalyzer.analyze(
                context = context,
                errors = listOf(context.getString(R.string.no_available_authorizer)),
                hints = hints.copy(isContainer = isXapk)
            )
            logManager.addLog(
                "System installer launch skipped | category: ${analysis.category}" +
                    " | reason: ${analysis.reason} | suggestions: ${analysis.suggestions}",
                "External install"
            )
            mainHandler.post { onError(analysis) }
            return@Thread
        }

        logManager.addLog(
            "Trying installation method: ${context.getString(authorizer.displayNameRes)}",
            "External install"
        )
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
            allowDowngrade = allowDowngrade,
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
            logManager.addLog("External installation completed", "External install")
            mainHandler.post { onSuccess() }
            return@Thread
        }
        logManager.addLog("Installation method failed: $errorMessage", "External install")

        val analysis = InstallFailureAnalyzer.analyze(
            context = context,
            errors = listOfNotNull(errorMessage).ifEmpty {
                listOf(context.getString(R.string.install_failed, ""))
            },
            hints = hints.copy(
                isContainer = isXapk,
                isDowngradeAttempt = hints.isDowngradeAttempt || allowDowngrade
            )
        )
        logManager.addLog(
            "External installation failed | category: ${analysis.category}" +
                " | reason: ${analysis.reason} | suggestions: ${analysis.suggestions}" +
                " | detail: ${analysis.technicalDetail}",
            "External install"
        )
        mainHandler.post { onError(analysis) }
    }.start()
}

/**
 * 逐个安装多 APK 压缩包内的每个 APK。
 *
 * 每个 APK 都是可直接安装的基准包，必须各自走一次完整的安装会话；
 * 系统安装器无法等待单个会话的结果，因此这里要求 Shizuku / Dhizuku 授权。
 * 单个 APK 失败不会中断后续 APK，全部结束后一次性上报成功数与错误。
 *
 * @param targetIndexes 本次要安装的条目在 [items] 中的下标（重试时跳过已成功项）
 * @param isCancelled 取消检查：返回 true 时不再开始下一个 APK
 * @param onItemStatus 单个条目状态变化（主线程回调，参数为条目在 [items] 中的下标）
 * @param onItemFinished 已完成条目数（主线程回调）
 * @param onComplete 全部结束：成功数、失败错误列表与是否被取消（主线程回调）
 */
private fun performMultiApkInstallation(
    context: Context,
    items: List<MultiApkItem>,
    targetIndexes: List<Int>,
    mode: PrivilegeHelper.PrivilegeMode,
    isCancelled: () -> Boolean,
    onItemStatus: (index: Int, status: MultiApkStatus, error: String?) -> Unit,
    onItemFinished: (completed: Int) -> Unit,
    onComplete: (successCount: Int, errors: List<String>, cancelled: Boolean) -> Unit
) {
    val logManager = LogManager.getInstance()
    val mainHandler = Handler(Looper.getMainLooper())

    val currentAuthorizer = if (mode == PrivilegeHelper.PrivilegeMode.DHIZUKU) {
        Authorizer.Dhizuku
    } else {
        Authorizer.Shizuku
    }

    Thread {
        // 逐个安装必须等前一个会话结束，系统安装器（None）无法满足，
        // 因此只接受可用的特权授权器，否则直接上报缺少权限
        val ordered = SmartAuthorizer.resolveInstallPlan(
            context = context,
            current = currentAuthorizer,
            installed = true
        )
        val authorizer = if (currentAuthorizer in ordered) {
            currentAuthorizer
        } else {
            ordered.firstOrNull { it != Authorizer.None }
        }
        if (authorizer == null || !SmartAuthorizer.isAvailable(context, authorizer)) {
            logManager.addLog(
                "Multi-APK installation requires Shizuku or Dhizuku",
                "External install"
            )
            mainHandler.post {
                onComplete(0, listOf(context.getString(R.string.multi_apk_needs_privilege)), false)
            }
            return@Thread
        }

        logManager.addLog(
            "Installing ${targetIndexes.size} APKs one by one with " +
                context.getString(authorizer.displayNameRes),
            "External install"
        )

        var successCount = 0
        var cancelled = false
        val errors = mutableListOf<String>()

        for ((position, index) in targetIndexes.withIndex()) {
            // 用户取消后不再开始新的 APK，已进入的会话仍会等待结果
            if (isCancelled()) {
                cancelled = true
                break
            }
            val item = items[index]
            mainHandler.post { onItemStatus(index, MultiApkStatus.INSTALLING, null) }

            // 已安装且待装版本更低时允许降级尝试，与单 APK 流程保持一致
            val installedVersionCode = item.entry.packageName?.let { packageName ->
                runCatching {
                    context.packageManager.getPackageInfo(packageName, 0).longVersionCode
                }.getOrNull()
            }
            val allowDowngrade = installedVersionCode != null &&
                item.entry.versionCode < installedVersionCode

            val latch = CountDownLatch(1)
            var succeeded = false
            var errorMessage: String? = null

            InstallDispatcher.install(
                context = context,
                authorizer = authorizer,
                filePath = item.entry.file.absolutePath,
                isXapk = false,
                replaceExisting = true,
                grantPermissions = true,
                allowDowngrade = allowDowngrade,
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

            // 会话长时间无结果时按超时处理，避免整个安装队列卡死
            if (!latch.await(10, TimeUnit.MINUTES)) {
                errorMessage = context.getString(R.string.install_failure_timeout_reason)
            }

            if (succeeded) {
                successCount++
                mainHandler.post { onItemStatus(index, MultiApkStatus.SUCCESS, null) }
            } else {
                val detail = errorMessage ?: context.getString(R.string.install_failed, "")
                errors += detail
                mainHandler.post { onItemStatus(index, MultiApkStatus.FAILED, detail) }
            }
            mainHandler.post { onItemFinished(position + 1) }
        }

        mainHandler.post { onComplete(successCount, errors, cancelled) }
    }.start()
}

/**
 * 快速解析对话框待安装文件的签名摘要（XAPK/APKS/APKM/ZIP 容器标记为不适用）。
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
    if (uri == null) {
        LogManager.getInstance().addLog("Cannot prepare file: URI is null", "External install")
        return null
    }
    return try {
        if (uri.scheme == "file") {
            LogManager.getInstance().addLog("Using installation file directly: ${uri.path}", "External install")
            return uri.path
        }

        InstallCacheCleaner.deleteStaleInstallFiles(context)

        val suffix = queryDisplayName(context, uri)
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?.let { ".${it.lowercase()}" }
            ?: mimeTypeSuffix(context, uri)
            ?: ".apk"
        val cacheFile = File(
            InstallCacheCleaner.installCacheDir(context),
            "temp_install_${System.currentTimeMillis()}$suffix"
        )

        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
            true
        } ?: false

        if (!copied) {
            cacheFile.delete()
            Log.e("InstallDialog", "Failed to open install file: $uri")
            LogManager.getInstance().addLog("Could not copy installation file from $uri", "External install")
            return null
        }
        LogManager.getInstance().addLog("Copied installation file to ${cacheFile.absolutePath}", "External install")
        cacheFile.absolutePath
    } catch (e: Exception) {
        Log.e("InstallDialog", "Failed to get file path from URI", e)
        LogManager.getInstance().addLog("Could not prepare installation file: ${e.message}", "External install")
        null
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
} catch (_: Exception) {
    null
} ?: uri.lastPathSegment

/**
 * 显示名没有扩展名时（例如 FileProvider 只给出 `file`），按 MIME 推断后缀，
 * 避免容器文件被当成 APK 处理。
 */
private fun mimeTypeSuffix(context: Context, uri: Uri): String? {
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()?.lowercase()
        ?: return null
    return when (mime) {
        "application/vnd.android.package-archive" -> ".apk"
        "application/zip", "application/x-zip-compressed" -> ".zip"
        "application/vnd.apks" -> ".apks"
        "application/vnd.apkm", "application/x-apkm" -> ".apkm"
        "application/x-xapk", "application/vnd.xapk" -> ".xapk"
        else -> null
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
            shizukuStatus = PrivilegeHelper.checkShizukuStatus(context)
            dhizukuStatus = PrivilegeHelper.checkDhizukuStatus(context)

            // 动态获取已安装授权器的图标
            try {
                val pm = context.packageManager
                val shizukuInfo = pm.getPackageInfo("moe.shizuku.privileged.api", 0)
                shizukuIcon = shizukuInfo.applicationInfo?.loadIcon(pm)
            } catch (_: Exception) {
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
            } catch (_: Exception) {
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
                                .clickable {
                                    selectedMode = PrivilegeHelper.PrivilegeMode.SHIZUKU
                                    LogManager.getInstance().addLog(
                                        "Privilege mode selected: Shizuku",
                                        "External install UI"
                                    )
                                },
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
                                .clickable {
                                    selectedMode = PrivilegeHelper.PrivilegeMode.DHIZUKU
                                    LogManager.getInstance().addLog(
                                        "Privilege mode selected: Dhizuku",
                                        "External install UI"
                                    )
                                },
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
                            TextButton(onClick = {
                                LogManager.getInstance().addLog(
                                    "Privilege selector cancel pressed",
                                    "External install UI"
                                )
                                onDismiss()
                            }) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    LogManager.getInstance().addLog(
                                        "Privilege selector next step pressed",
                                        "External install UI"
                                    )
                                    onModeSelected(selectedMode)
                                },
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
