package com.vrc.friendtracker.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the VRChat session cookies encrypted with an AES-GCM key kept in the
 * Android Keystore. Plaintext cookies never touch disk.
 */
class TokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("vrc_session", Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    var authToken: String?
        get() = readDecrypted(KEY_AUTH_TOKEN)
        set(value) = writeEncrypted(KEY_AUTH_TOKEN, value)

    var twoFactorToken: String?
        get() = readDecrypted(KEY_TWO_FACTOR)
        set(value) = writeEncrypted(KEY_TWO_FACTOR, value)

    /** Pending auth cookie issued during the 2FA challenge; only valid for finishing login. */
    var pendingAuthToken: String?
        get() = readDecrypted(KEY_PENDING_AUTH)
        set(value) = writeEncrypted(KEY_PENDING_AUTH, value)

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_USERNAME).apply()
            else prefs.edit().putString(KEY_USERNAME, value).apply()
        }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun readDecrypted(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        return try {
            decrypt(encoded)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeEncrypted(key: String, value: String?) {
        val editor = prefs.edit()
        if (value == null) {
            editor.remove(key)
        } else {
            editor.putString(key, encrypt(value))
        }
        editor.apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val combined = iv + cipherText
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_SIZE)
        val cipherText = combined.copyOfRange(IV_SIZE, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    companion object {
        private const val KEY_ALIAS = "vrc_session_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
        private const val KEY_AUTH_TOKEN = "auth_token_enc"
        private const val KEY_TWO_FACTOR = "two_factor_token_enc"
        private const val KEY_PENDING_AUTH = "pending_auth_token_enc"
        private const val KEY_USERNAME = "username"
    }
}