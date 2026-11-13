package com.unplug.app

import android.content.Context
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password for emergency unlock. Until the user sets their own, the default is
 * [DEFAULT]. A custom password is stored only as a salted PBKDF2 hash.
 */
object UnlockPassword {
    const val DEFAULT = "unplug@#2026"

    private const val PREFS = "unlock"
    private const val KEY_HASH = "hash"
    private const val KEY_SALT = "salt"
    private const val ITERATIONS = 20_000

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDefault(c: Context): Boolean = prefs(c).getString(KEY_HASH, null) == null

    fun verify(c: Context, input: String): Boolean {
        val p = prefs(c)
        val hash = p.getString(KEY_HASH, null) ?: return input == DEFAULT
        val salt = p.getString(KEY_SALT, null)?.hexToBytes() ?: return false
        return derive(input, salt).toHex() == hash
    }

    /** Changes the password if [current] is right. Returns false otherwise. */
    fun change(c: Context, current: String, new: String): Boolean {
        if (!verify(c, current) || new.length < 4) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(c).edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, derive(new, salt).toHex())
            .apply()
        return true
    }

    /** Back to the default password (needs the current one). */
    fun reset(c: Context, current: String): Boolean {
        if (!verify(c, current)) return false
        prefs(c).edit().clear().apply()
        return true
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
