package dev.huidoudour.installer.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 加密偏好存储（AndroidKeyStore 托管主密钥）。
 * 仅用于敏感项（如 GitHub PAT），与普通 [SharedPreferences] 分离，键/值均加密。
 * 首次访问时才创建文件并生成密钥，之后复用同一实例。
 */
object SecurePrefs {

    private const val FILE_NAME = "app_secure_prefs"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context.applicationContext,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { cached = it }
        }
    }

    fun getString(context: Context, key: String, def: String = ""): String =
        prefs(context).getString(key, def).orEmpty()

    fun putString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun remove(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }
}
