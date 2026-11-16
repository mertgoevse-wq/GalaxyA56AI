package com.galaxya56.ai.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM token storage backed by the Android Keystore.
 *
 * Only ciphertext is written to SharedPreferences. The key never leaves the
 * Keystore. Values are migrated once from the old plaintext keys.
 */
class SecureTokenStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("galaxya56_secure", Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun key(): SecretKey {
        val existing = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun get(name: String): String {
        val encoded = prefs.getString(name, null) ?: return ""
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size <= IV_BYTES) return ""
            val iv = packed.copyOfRange(0, IV_BYTES)
            val data = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.onFailure { Log.w(TAG, "Token $name konnte nicht entschlüsselt werden: ${it.message}") }
            .getOrDefault("")
    }

    fun put(name: String, value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(name).apply()
            return
        }
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val data = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = cipher.iv + data
            prefs.edit()
                .putString(name, Base64.encodeToString(packed, Base64.NO_WRAP))
                .apply()
        }.onFailure { Log.e(TAG, "Token $name konnte nicht verschlüsselt werden: ${it.message}") }
    }

    /** Migrates the old plaintext keys once and removes them afterwards. */
    fun migrateLegacy(plain: SharedPreferences) {
        val names = listOf("hfToken", "civitaiToken", "geminiKey")
        val editor = plain.edit()
        for (name in names) {
            val old = plain.getString(name, null)
            if (!old.isNullOrBlank() && get(name).isBlank()) put(name, old)
            editor.remove(name)
        }
        editor.apply()
    }

    companion object {
        private const val TAG = "A56AI-SECURE"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "galaxya56_tokens_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}
