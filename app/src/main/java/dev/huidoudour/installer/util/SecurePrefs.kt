package dev.huidoudour.installer.util

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.Properties

/**
 * 敏感项存储（GitHub PAT 等）。
 *
 * 文件为明文可编辑的 key=value 格式，位于应用专属外部目录
 * /storage/emulated/0/Android/data/<包名>/data/secure/pat.key，
 * 可用文件管理器直接修改，App 下次读取时生效；外部存储不可用时回退内部 files 目录。
 *
 * 旧版曾用 AndroidKeyStore 托管的加密格式，外部无法解密，故改为明文以支持手动编辑。
 */
object SecurePrefs {

    private const val STORE_DIR = "secure"
    private const val FILE_NAME = "pat.key"

    // 上一版明文文件（与当前格式相同）：首次读取时搬运后删除
    private const val LEGACY_PROPERTIES_FILE = "credentials.properties"

    // 旧版加密文件（第二代）：EncryptedFile，首次读取时解密迁移并删除
    private const val LEGACY_ENCRYPTED_FILE = "github_pat.enc"

    // 旧版加密偏好（第一代）：EncryptedSharedPreferences
    private const val LEGACY_PREFS_NAME = "app_secure_prefs"
    private const val LEGACY_KEY_KEYSET_PREFS = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
    private const val LEGACY_VALUE_KEYSET_PREFS = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
    // keyset 残留键的前缀：历史迁移 bug 曾把加密密钥集当作数据写入，读取时一律忽略
    private const val LEGACY_KEYSET_PREFIX = "__androidx_security_crypto_encrypted_prefs"

    @Volatile
    private var legacyChecked = false

    /** 当前存储目录：Android/data/<包名>/data/secure（外部存储不可用回退内部 files/secure） */
    private fun storeDir(context: Context): File {
        val dir = context.getExternalFilesDir(null)?.parentFile?.resolve("data/$STORE_DIR")
            ?: File(context.filesDir, STORE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 旧存储目录：Android/data/<包名>/files/secure（上一版位置） */
    private fun legacyDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, STORE_DIR)

    private fun storeFile(context: Context): File = File(storeDir(context), FILE_NAME)

    /** 把旧 files/secure 下的文件搬到 data/secure，避免目录调整导致丢数据 */
    private fun moveFromLegacyDir(context: Context, targetDir: File) {
        val legacy = legacyDir(context)
        if (legacy == targetDir || !legacy.isDirectory) return
        legacy.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val dest = File(targetDir, file.name)
            if (dest.exists()) return@forEach
            if (!file.renameTo(dest)) {
                runCatching {
                    file.copyTo(dest)
                    file.delete()
                }
            }
        }
    }

    /** 首次访问前完成：旧目录搬迁 + 各代格式迁移 */
    private fun ensureMigrated(context: Context) {
        val file = storeFile(context)
        if (file.exists() || legacyChecked) return
        synchronized(this) {
            if (file.exists() || legacyChecked) return
            moveFromLegacyDir(context, storeDir(context))
            migrateLegacy(context, file)
            legacyChecked = true
        }
    }

    /** 解析 key=value 文本，忽略空行与 # 注释 */
    private fun read(file: File): Properties {
        val props = Properties()
        if (!file.exists()) return props
        runCatching {
            file.forEachLine { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                val idx = line.indexOf('=')
                if (idx > 0) {
                    val key = line.take(idx).trim()
                    // 跳过旧迁移 bug 遗留的 keyset 密钥集条目
                    if (key.startsWith(LEGACY_KEYSET_PREFIX)) return@forEachLine
                    props.setProperty(key, line.substring(idx + 1).trim())
                }
            }
        }
        return props
    }

    /** 原子写入：先写临时文件再改名，避免与外部编辑器并发写坏文件 */
    private fun write(file: File, props: Properties) {
        val text = buildString {
            appendLine("# Installer 敏感配置：可直接编辑，保存后下次读取生效")
            props.forEach { (key, value) -> appendLine("$key=$value") }
        }
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }

    /** 创建与两代加密存储共用的 AndroidKeyStore 主密钥（默认 alias 相同） */
    @Suppress("DEPRECATION")
    private fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    /**
     * 首次访问时把两代加密存储迁移为明文文件。
     * 注意：第一代 EncryptedSharedPreferences 的键名和值在磁盘上都是密文，
     * 必须通过封装类读取；直接读普通 SharedPreferences 拿到的是加密后的键名，会导致迁移后"PAT 丢失"。
     * 只有读取成功才清理旧文件，读取失败（主密钥失效等）保留原文件，避免不可逆破坏。
     */
    @Suppress("DEPRECATION")
    private fun migrateLegacy(context: Context, target: File) {
        val masterKey = masterKey(context)
        val props = Properties()

        // 第一代：EncryptedSharedPreferences（键、值均加密）
        val legacyEntries = runCatching {
            EncryptedSharedPreferences.create(
                context.applicationContext,
                LEGACY_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
        }.getOrNull()
        legacyEntries?.forEach { (key, value) -> props.setProperty(key, value) }

        // 第二代：EncryptedFile（文件整体加密，内容为明文键值），较新故覆盖第一代同键
        val encrypted = File(storeDir(context), LEGACY_ENCRYPTED_FILE)
        if (encrypted.exists()) {
            val migrated = runCatching {
                val fromFile = Properties()
                EncryptedFile.Builder(
                    context.applicationContext,
                    encrypted,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build().openFileInput().use { fromFile.load(it) }
                props.putAll(fromFile)
                true
            }.getOrDefault(false)
            if (migrated) encrypted.delete()
        }

        // 上一版明文文件（与当前格式相同）：直接搬运，覆盖更早的加密格式
        val legacyProperties = File(storeDir(context), LEGACY_PROPERTIES_FILE)
        if (legacyProperties.exists()) {
            props.putAll(read(legacyProperties))
            legacyProperties.delete()
        }

        // 仅在第一代读取成功时清理其主文件与两个 keyset；失败则保留以便排查
        if (legacyEntries != null) {
            context.deleteSharedPreferences(LEGACY_PREFS_NAME)
            context.deleteSharedPreferences(LEGACY_KEY_KEYSET_PREFS)
            context.deleteSharedPreferences(LEGACY_VALUE_KEYSET_PREFS)
        }

        if (props.isNotEmpty()) write(target, props)
    }

    fun getString(context: Context, key: String, def: String = ""): String {
        ensureMigrated(context)
        return read(storeFile(context)).getProperty(key) ?: def
    }

    fun putString(context: Context, key: String, value: String) {
        ensureMigrated(context)
        val file = storeFile(context)
        val props = read(file)
        props.setProperty(key, value)
        write(file, props)
    }

    fun remove(context: Context, key: String) {
        ensureMigrated(context)
        val file = storeFile(context)
        val props = read(file)
        props.remove(key)
        if (props.isEmpty()) file.delete() else write(file, props)
    }
}
