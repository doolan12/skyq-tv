package com.skyqlike.iptv.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Locks private text (passwords, the parental PIN, Stremio keys) before it is saved on the device.
 *
 * The lock key lives inside the Android Keystore, so it never leaves the device's secure hardware or
 * secure software store, and other apps can't read it.
 *
 * Old saved text that was never locked is still read fine, and gets locked the next time it is saved.
 * Very old devices (Android 5.1 and older) don't have the Keystore feature we need,
 * so on those the text is saved as before.
 */
object Secure {

    private const val KEY_ALIAS = "skyqlike_secure_v1"
    private const val PREFIX = "enc1:"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    /** Returns [plain] locked, ready to save. Empty text stays empty. */
    fun lock(plain: String): String {
        if (plain.isEmpty() || !supported) return plain
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(iv + data, Base64.NO_WRAP)
        } catch (e: Exception) {
            // If the Keystore misbehaves on an odd device, keep the app working rather than lose the login.
            plain
        }
    }

    /** Reads text saved by [lock]. Text that was never locked comes back unchanged. */
    fun unlock(saved: String): String {
        if (!saved.startsWith(PREFIX)) return saved
        return try {
            val raw = Base64.decode(saved.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val data = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            // The key was lost (for example after a device restore). Treat it as not saved.
            ""
        }
    }

    /** True if [saved] is still plain text that should be locked on the next save. */
    fun needsLocking(saved: String): Boolean = supported && saved.isNotEmpty() && !saved.startsWith(PREFIX)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
