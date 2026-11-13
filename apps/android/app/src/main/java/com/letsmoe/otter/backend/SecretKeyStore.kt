package com.letsmoe.otter.backend

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "OtterSecrets"
private const val KEYSTORE = "AndroidKeyStore"
private const val WRAPPING_KEY_ALIAS = "otter-secrets"
private const val PREFERENCES = "secrets"
private const val WRAPPED_KEY = "wrapped_key"
private const val KEY_BYTES = 32
private const val GCM_TAG_BITS = 128

/**
 * The key the backend seals plugin passwords and sessions with (its
 * SECRET_KEY). It is stored wrapped by a key that never leaves the Android
 * Keystore, so a copy of the app's files alone can't open the saved logins.
 */
class SecretKeyStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** The key as hex, created on first use. */
    fun secretKeyHex(): String {
        val key = storedKey() ?: createKey()
        return key.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun storedKey(): ByteArray? {
        val wrapped = preferences.getString(WRAPPED_KEY, null) ?: return null
        return try {
            unwrap(Base64.decode(wrapped, Base64.NO_WRAP))
        } catch (error: GeneralSecurityException) {
            // The Keystore key is gone (e.g. after a device restore): saved logins have to be entered again.
            Log.w(TAG, "the stored secret key can't be opened, making a new one", error)
            null
        }
    }

    private fun createKey(): ByteArray {
        val key = ByteArray(KEY_BYTES)
        SecureRandom().nextBytes(key)
        val wrapped = wrap(key)
        preferences.edit().putString(WRAPPED_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP)).apply()
        return key
    }

    // The IV goes in front of the encrypted key.
    private fun wrap(key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        return cipher.iv + cipher.doFinal(key)
    }

    private fun unwrap(wrapped: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val ivSize = 12
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(GCM_TAG_BITS, wrapped, 0, ivSize))
        return cipher.doFinal(wrapped, ivSize, wrapped.size - ivSize)
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(WRAPPING_KEY_ALIAS, null)
        if (existing is SecretKey) {
            return existing
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val purposes = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        generator.init(
            KeyGenParameterSpec.Builder(WRAPPING_KEY_ALIAS, purposes)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
