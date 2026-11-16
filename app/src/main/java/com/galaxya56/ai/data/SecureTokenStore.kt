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
 * Keystore. Migration from the old plaintext keys is deliberately conservative:
 * a plaintext value is removed only after the encrypted copy has been read back
 * successfully. If the Keystore fails, the old value stays where it is.
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

    /** Returns the decrypted value, or null when absent or unreadable. */
    fun read(name: String): String? {
        val encoded = prefs.getString(name, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size <= IV_BYTES) return null
            val iv = packed.copyOfRange(0, IV_BYTES)
            val data = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.onFailure {
            Log.w(TAG, "Token $name konnte nicht entschlüsselt werden: ${it.message}")
        }.getOrNull()
    }

    /** Compatibility wrapper used by the settings model. */
    fun get(name: String): String = read(name).orEmpty()

    /**
     * Encrypts and commits synchronously. Returns true only when the value can
     * be read back byte-for-byte. A blank value removes the entry.
     */
    fun put(name: String, value: String): Boolean {
        if (value.isBlank()) {
            val removed = prefs.edit().remove(name).commit()
            if (!removed) Log.e(TAG, "Token $name konnte nicht entfernt werden")
            return removed
        }
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val data = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = cipher.iv + data
            val written = prefs.edit()
                .putString(name, Base64.encodeToString(packed, Base64.NO_WRAP))
                .commit()
            if (!written) {
                Log.e(TAG, "Token $name konnte nicht gespeichert werden")
                return false
            }
            val verified = read(name)
            if (verified != value) {
                Log.e(TAG, "Token $name konnte nach dem Schreiben nicht verifiziert werden")
                prefs.edit().remove(name).commit()
                return false
            }
            true
        }.onFailure {
            Log.e(TAG, "Token $name konnte nicht verschlüsselt werden: ${it.message}")
        }.getOrDefault(false)
    }

    /**
     * Moves the legacy plaintext keys into the encrypted store. The plaintext
     * key is only removed when the encrypted value is verified. A failure on
     * one token never blocks or deletes the others.
     */
    fun migrateLegacy(plain: SharedPreferences) {
        val names = listOf("hfToken", "civitaiToken", "geminiKey")
        var migrated = 0
        var kept = 0
        for (name in names) {
            val old = plain.getString(name, null)
            if (old.isNullOrBlank()) {
                // Remove an empty leftover, but leave an unreadable ciphertext alone.
                if (old != null && prefs.getString(name, null) == null) {
                    plain.edit().remove(name).commit()
                }
                continue
            }

            val current = read(name)
            if (current == old) {
                val removed = plain.edit().remove(name).commit()
                if (removed) migrated++ else kept++
                continue
            }

            // Either no encrypted value or an unreadable/stale one: replace it
            // with a fresh encryption and verify before touching the plaintext.
            if (put(name, old) && read(name) == old) {
                val removed = plain.edit().remove(name).commit()
                if (removed) {
                    migrated++
                    Log.i(TAG, "Token $name verschlüsselt migriert")
                } else {
                    kept++
                    Log.w(TAG, "Token $name verschlüsselt, Klartext konnte nicht entfernt werden")
                }
            } else {
                kept++
                Log.w(TAG, "Token $name bleibt als Klartext erhalten; Keystore-Schreibvorgang fehlgeschlagen")
            }
        }
        if (migrated > 0 || kept > 0) {
            Log.i(TAG, "Token-Migration: migriert=$migrated, Klartext behalten=$kept")
        }
    }

    companion object {
        private const val TAG = "A56AI-SECURE"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "galaxya56_tokens_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}
