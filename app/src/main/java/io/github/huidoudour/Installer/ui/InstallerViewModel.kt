package io.github.huidoudour.Installer.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.huidoudour.Installer.util.DhizukuInstallHelper
import io.github.huidoudour.Installer.util.LogManager
import io.github.huidoudour.Installer.util.PrivilegeHelper
import io.github.huidoudour.Installer.util.ShizukuInstallHelper
import io.github.huidoudour.Installer.util.XapkInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream

/**
 * InstallerScreen ViewModel
 */
class InstallerViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // 状态 - 使用 StateFlow 供 Compose 观察
    private val _privilegeStatus = MutableStateFlow<PrivilegeHelper.PrivilegeStatus>(PrivilegeHelper.PrivilegeStatus.NOT_RUNNING)
    val privilegeStatus: StateFlow<PrivilegeHelper.PrivilegeStatus> = _privilegeStatus.asStateFlow()

    private val _privilegeMode = MutableStateFlow(PrivilegeHelper.getCurrentMode(context))
    val privilegeMode: StateFlow<PrivilegeHelper.PrivilegeMode> = _privilegeMode.asStateFlow()

    private val _selectedFilePath = MutableStateFlow<String?>(null)
    val selectedFilePath: StateFlow<String?> = _selectedFilePath.asStateFlow()

    private val _selectedFileName = MutableStateFlow<String?>(null)
    val selectedFileName: StateFlow<String?> = _selectedFileName.asStateFlow()

    private val _fileType = MutableStateFlow<String?>(null)
    val fileType: StateFlow<String?> = _fileType.asStateFlow()

    private val _isXapkFile = MutableStateFlow(false)
    val isXapkFile: StateFlow<Boolean> = _isXapkFile.asStateFlow()

    private val _isInstallEnabled = MutableStateFlow(false)
    val isInstallEnabled: StateFlow<Boolean> = _isInstallEnabled.asStateFlow()

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling.asStateFlow()

    private val _installCompleted = MutableStateFlow(false)
    val installCompleted: StateFlow<Boolean> = _installCompleted.asStateFlow()

    private val _installProgress = MutableStateFlow(0)
    val installProgress: StateFlow<Int> = _installProgress.asStateFlow()

    private val _enableCustomPackageName = MutableStateFlow(true)
    val enableCustomPackageName: StateFlow<Boolean> = _enableCustomPackageName.asStateFlow()

    // 安装器包名选项
    data class InstallerPackageOption(
        val packageName: String,
        val displayName: String
    )

    val installerPackageOptions = listOf(
        InstallerPackageOption("io.github.huidoudour.Installer", "Installer"),
        InstallerPackageOption("me.huidoudour.core", "Huidoudour Core"),
        InstallerPackageOption("io.github.huidoudour.zjs", "ZJS")
    )

    private val _selectedInstallerPackage = MutableStateFlow("io.github.huidoudour.Installer")
    val selectedInstallerPackage: StateFlow<String> = _selectedInstallerPackage.asStateFlow()

    // replaceExisting 固定为 true，grantPermissions 固定为 false
    private val _replaceExisting = MutableStateFlow(true)
    val replaceExisting: StateFlow<Boolean> = _replaceExisting.asStateFlow()

    private val _grantPermissions = MutableStateFlow(false)
    val grantPermissions: StateFlow<Boolean> = _grantPermissions.asStateFlow()

    private val logManager = LogManager.getInstance()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshPrivilegeStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshPrivilegeStatus()
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 123) {
            val msg = if (grantResult == PackageManager.PERMISSION_GRANTED) "Shizuku permission granted" else "Shizuku permission denied"
            logManager.addLog(msg)
            refreshPrivilegeStatus()
        }
    }

    init {
        loadSwitchStates()
        refreshPrivilegeStatus()

        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            logManager.addLog("Shizuku listener registration failed: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Exception) {}
    }

    fun refreshPrivilegeStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val mode = PrivilegeHelper.getCurrentMode(context)
            val status = PrivilegeHelper.getStatus(context, mode)
            withContext(Dispatchers.Main) {
                _privilegeMode.value = mode
                _privilegeStatus.value = status
                updateInstallButtonState()
            }
        }
    }

    fun switchPrivilegeMode() {
        viewModelScope.launch(Dispatchers.IO) {
            val newMode = PrivilegeHelper.switchMode(context)
            val status = PrivilegeHelper.getStatus(context, newMode)
            withContext(Dispatchers.Main) {
                _privilegeMode.value = newMode
                _privilegeStatus.value = status
                logManager.addLog("Switched to ${PrivilegeHelper.getModeName(newMode)}")
            }
        }
    }

    fun requestPrivilegePermission() {
        viewModelScope.launch(Dispatchers.IO) {
            when (_privilegeStatus.value) {
                PrivilegeHelper.PrivilegeStatus.NOT_INSTALLED -> {
                    PrivilegeHelper.openGithubPage(context, _privilegeMode.value)
                    logManager.addLog("${PrivilegeHelper.getModeName(_privilegeMode.value)} not installed")
                }
                PrivilegeHelper.PrivilegeStatus.NOT_RUNNING -> {
                    PrivilegeHelper.openPrivilegeApp(context, _privilegeMode.value)
                    logManager.addLog("Opening ${PrivilegeHelper.getModeName(_privilegeMode.value)}")
                }
                PrivilegeHelper.PrivilegeStatus.NOT_AUTHORIZED,
                PrivilegeHelper.PrivilegeStatus.VERSION_TOO_LOW -> {
                    when (_privilegeMode.value) {
                        PrivilegeHelper.PrivilegeMode.SHIZUKU -> {
                            try {
                                if (Shizuku.pingBinder()) {
                                    Shizuku.requestPermission(123)
                                }
                            } catch (e: Exception) {
                                logManager.addLog("Shizuku error: ${e.message}")
                            }
                        }
                        PrivilegeHelper.PrivilegeMode.DHIZUKU -> {
                            PrivilegeHelper.requestDhizukuPermission(context)
                        }
                    }
                }
                else -> {}
            }
        }
    }

    fun onFileSelected(uri: Uri?) {
        if (uri == null) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = getFileNameFromUri(uri)
                withContext(Dispatchers.Main) {
                    _selectedFileName.value = fileName
                    _installCompleted.value = false
                }

                val path = getFilePathFromUri(uri)
                if (path != null) {
                    val isXapk = XapkInstaller.isXapkFile(path)
                    val type = XapkInstaller.getFileTypeDescription(path)

                    withContext(Dispatchers.Main) {
                        _selectedFilePath.value = path
                        _selectedFileName.value = fileName
                        _isXapkFile.value = isXapk
                        _fileType.value = type
                        updateInstallButtonState()
                    }

                    logManager.addLog("File selected: $path")
                }
            } catch (e: Exception) {
                logManager.addLog("Error selecting file: ${e.message}")
            }
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            if (uri.scheme == "file") {
                return uri.path
            }

            val cacheFile = File(context.cacheDir, getFileNameFromUri(uri) ?: "file.apk")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile.absolutePath
        } catch (e: Exception) {
            logManager.addLog("Error getting file path: ${e.message}")
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else null
            } ?: uri.lastPathSegment
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    fun refreshFileInfo() {
        val path = _selectedFilePath.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val isXapk = XapkInstaller.isXapkFile(path)
            val type = XapkInstaller.getFileTypeDescription(path)

            withContext(Dispatchers.Main) {
                _isXapkFile.value = isXapk
                _fileType.value = type
            }

            logManager.addLog("File info refreshed")
        }
    }

    fun install() {
        val path = _selectedFilePath.value ?: return

        if (_privilegeStatus.value != PrivilegeHelper.PrivilegeStatus.AUTHORIZED) {
            logManager.addLog("Privilege not authorized")
            return
        }

        _isInstalling.value = true
        _installProgress.value = 0

        viewModelScope.launch(Dispatchers.IO) {
            val mode = _privilegeMode.value
            when (mode) {
                PrivilegeHelper.PrivilegeMode.DHIZUKU -> {
                    val callback = object : DhizukuInstallHelper.InstallCallback {
                        override fun onProgress(message: String) { logManager.addLog(message) }
                        override fun onSuccess(message: String) {
                            logManager.addLog(message)
                            viewModelScope.launch(Dispatchers.Main) {
                                _isInstalling.value = false
                                _installCompleted.value = true
                                clearSelection()
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                        override fun onError(error: String) {
                            logManager.addLog("Error: $error")
                            viewModelScope.launch(Dispatchers.Main) {
                                _isInstalling.value = false
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    if (_isXapkFile.value) {
                        DhizukuInstallHelper.installXapk(context, path, _replaceExisting.value, _grantPermissions.value, callback)
                    } else {
                        DhizukuInstallHelper.installSingleApk(context, java.io.File(path), _replaceExisting.value, _grantPermissions.value, callback)
                    }
                }
                PrivilegeHelper.PrivilegeMode.SHIZUKU -> {
                    val callback = object : ShizukuInstallHelper.InstallCallback {
                        override fun onProgress(message: String) { logManager.addLog(message) }
                        override fun onSuccess(message: String) {
                            logManager.addLog(message)
                            viewModelScope.launch(Dispatchers.Main) {
                                _isInstalling.value = false
                                _installCompleted.value = true
                                clearSelection()
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                        override fun onError(error: String) {
                            logManager.addLog("Error: $error")
                            viewModelScope.launch(Dispatchers.Main) {
                                _isInstalling.value = false
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    if (_isXapkFile.value) {
                        ShizukuInstallHelper.installXapk(context, path, _replaceExisting.value, _grantPermissions.value, callback)
                    } else {
                        ShizukuInstallHelper.installSingleApk(context, java.io.File(path), _replaceExisting.value, _grantPermissions.value, callback)
                    }
                }
            }
        }
    }

    fun clearSelection() {
        _selectedFilePath.value = null
        _selectedFileName.value = null
        _fileType.value = null
        _isXapkFile.value = false
        updateInstallButtonState()
    }

    private fun updateInstallButtonState() {
        val path = _selectedFilePath.value
        val fileSelected = !path.isNullOrEmpty()
        val privilegeReady = _privilegeStatus.value == PrivilegeHelper.PrivilegeStatus.AUTHORIZED
        _isInstallEnabled.value = privilegeReady && fileSelected && !_isInstalling.value
    }

    private fun loadSwitchStates() {
        _enableCustomPackageName.value = prefs.getBoolean("enable_custom_package_name", true)
        // replaceExisting 和 grantPermissions 固定值，不从 prefs 加载
        _replaceExisting.value = true
        _grantPermissions.value = false
        // 加载安装器包名选择
        val savedPackage = prefs.getString("installer_package", "")?.ifEmpty { "io.github.huidoudour.Installer" } ?: "io.github.huidoudour.Installer"
        _selectedInstallerPackage.value = savedPackage
    }

    fun saveSwitchStates() {
        prefs.edit()
            .putBoolean("enable_custom_package_name", _enableCustomPackageName.value)
            .putString("installer_package", _selectedInstallerPackage.value)
            .apply()
    }

    fun setEnableCustomPackageName(value: Boolean) {
        _enableCustomPackageName.value = value
        if (!value) {
            // 关闭时固定为 com.android.shell（install helper 中已处理）
        }
        saveSwitchStates()
    }

    fun setSelectedInstallerPackage(packageName: String) {
        _selectedInstallerPackage.value = packageName
        saveSwitchStates()
    }
}
