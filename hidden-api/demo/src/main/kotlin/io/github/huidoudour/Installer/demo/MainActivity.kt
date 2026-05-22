package io.github.huidoudour.Installer.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hidden API Demo - 展示 hidden-api 模块中所有接口的文档信息
 * 使用 Jetpack Compose 重写
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    HiddenApiDemoScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenApiDemoScreen() {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Hidden API Demo") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            val apis = getApiEntries()
            apis.forEachIndexed { index, api ->
                ApiCard(api = api)
                if (index < apis.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ApiCard(api: ApiEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Type chip + Name
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypeChip(type = api.type)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = api.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Package
            InfoRow(label = "Package", value = api.pkg)
            
            // Modifiers
            api.modifiers?.let {
                InfoRow(label = "Modifiers", value = it)
            }
            
            // Extends
            api.extends?.let {
                InfoRow(label = "Extends", value = it)
            }
            
            // Implements
            if (api.implements.isNotEmpty()) {
                InfoRow(label = "Implements", value = api.implements.joinToString(", "))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            // Description
            Text(
                text = api.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            // Inner Classes
            if (api.innerClasses.isNotEmpty()) {
                SectionTitle(title = "Inner Classes")
                api.innerClasses.forEach { ic ->
                    CodeLine(code = "  $ic")
                }
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // Methods
            if (api.methods.isNotEmpty()) {
                SectionTitle(title = "Methods")
                api.methods.forEach { method ->
                    CodeLine(code = method)
                }
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // Fields
            if (api.fields.isNotEmpty()) {
                SectionTitle(title = "Fields")
                api.fields.forEach { field ->
                    CodeLine(code = "  $field")
                }
            }
        }
    }
}

@Composable
fun TypeChip(type: ApiType) {
    val (backgroundColor, label) = when (type) {
        ApiType.AIDL -> Color(0xFF2196F3) to "AIDL"
        ApiType.INTERFACE -> Color(0xFF4CAF50) to "Interface"
        ApiType.CLASS -> Color(0xFFFF9800) to "Class"
    }
    
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: ",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
fun CodeLine(code: String) {
    Text(
        text = code,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

enum class ApiType {
    AIDL,
    INTERFACE,
    CLASS
}

data class ApiEntry(
    val name: String,
    val pkg: String,
    val type: ApiType,
    val extends: String? = null,
    val implements: List<String> = emptyList(),
    val modifiers: String? = null,
    val description: String,
    val methods: List<String> = emptyList(),
    val innerClasses: List<String> = emptyList(),
    val fields: List<String> = emptyList()
)

fun getApiEntries(): List<ApiEntry> {
    return listOf(
        ApiEntry(
            name = "IIntentReceiver",
            pkg = "android.content",
            type = ApiType.INTERFACE,
            description = "Intent 接收回调接口，用于接收异步广播结果。",
            methods = listOf(
                "performReceive(Intent, int resultCode, String data,\n  Bundle extras, boolean ordered, boolean sticky, int sendingUser)"
            )
        ),
        ApiEntry(
            name = "IIntentSender",
            pkg = "android.content",
            type = ApiType.INTERFACE,
            description = "Intent 发送者接口，用于拦截安装回调。核心隐藏 API，Dhizuku 安装流程依赖此接口。",
            methods = listOf(
                "send(int code, Intent intent, String resolvedType,\n  IBinder whitelistToken, IIntentReceiver finishedReceiver,\n  int flags, Bundle options)"
            )
        ),
        ApiEntry(
            name = "IPackageInstaller",
            pkg = "android.content.pm",
            type = ApiType.AIDL,
            description = "包安装器 AIDL 接口，提供安装/卸载会话管理。通过 IPackageManager.getPackageInstaller() 获取。",
            methods = listOf(
                "uninstall(VersionedPackage, String callerPackageName,\n  int flags, IntentSender statusReceiver, int userId)",
                "abandonSession(int sessionId)"
            ),
            innerClasses = listOf("Stub (Binder)")
        ),
        ApiEntry(
            name = "IPackageInstallerSession",
            pkg = "android.content.pm",
            type = ApiType.AIDL,
            description = "安装会话 AIDL 接口，代表一个活跃的安装操作。",
            innerClasses = listOf("Stub (Binder)")
        ),
        ApiEntry(
            name = "IPackageManager",
            pkg = "android.content.pm",
            type = ApiType.AIDL,
            description = "包管理器 AIDL 接口，Android 包管理核心服务。通过 ServiceManager 获取 IBinder 后转为接口。",
            methods = listOf("getPackageInstaller() -> IPackageInstaller"),
            innerClasses = listOf("Stub (Binder)")
        ),
        ApiEntry(
            name = "PackageInstaller",
            pkg = "android.content.pm",
            type = ApiType.CLASS,
            description = "包安装器 Java 封装类，提供 Session 管理的高层 API。",
            methods = listOf(
                "createSession(SessionParams) -> int",
                "openSession(int sessionId) -> Session"
            ),
            innerClasses = listOf(
                "SessionParams - 安装会话参数",
                "Session - 安装会话操作"
            )
        ),
        ApiEntry(
            name = "PackageInstaller.Session",
            pkg = "android.content.pm",
            type = ApiType.CLASS,
            description = "安装会话，负责写入 APK 数据并提交安装。",
            methods = listOf(
                "openWrite(String name, long offset, long length) -> int",
                "write(int handle, long offset, long length, File file)",
                "fsync(OutputStream os)",
                "commit(IntentSender statusReceiver)",
                "close()"
            )
        ),
        ApiEntry(
            name = "VersionedPackage",
            pkg = "android.content.pm",
            type = ApiType.CLASS,
            implements = listOf("Parcelable"),
            description = "版本化包名，携带包名和版本代码。用于精确指定卸载目标。",
            methods = listOf(
                "describeContents() -> int",
                "writeToParcel(Parcel dest, int flags)"
            ),
            fields = listOf(
                "packageName (String)",
                "versionCode (long)",
                "CREATOR (Creator<VersionedPackage>)"
            )
        ),
        ApiEntry(
            name = "ServiceManager",
            pkg = "android.os",
            type = ApiType.CLASS,
            modifiers = "final",
            description = "系统服务管理器，用于获取系统服务的 IBinder。隐藏 API 的入口。",
            methods = listOf("getService(String name) -> IBinder")
        ),
        ApiEntry(
            name = "UserHandle",
            pkg = "android.os",
            type = ApiType.CLASS,
            description = "用户句柄，用于标识 Android 多用户环境中的用户 ID。",
            methods = listOf("myUserId() -> int")
        )
    )
}
