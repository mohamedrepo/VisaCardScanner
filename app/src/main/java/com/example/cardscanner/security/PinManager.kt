package com.example.cardscanner.security

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Administrator PIN lock (spec §5).
 *
 * The PIN itself is never stored: only a PBKDF2-HMAC-SHA256 hash with a random
 * 16-byte salt, kept in app-private SharedPreferences.
 *
 * Note: because the hash is one-way, a forgotten PIN means reinstalling the
 * app (which wipes data) — there is deliberately no recovery backdoor.
 */
class PinManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("admin_auth", Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.getString(KEY_HASH, null) != null

    /** Sets/changes the administrator PIN. Returns false on invalid input. */
    fun setPin(pin: CharArray): Boolean {
        if (!isValidPin(pin)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash.toHex())
            .apply()
        pin.fill('0')
        return true
    }

    /** Verifies a PIN attempt in constant time against the stored hash. */
    fun verify(pin: CharArray): Boolean {
        val saltHex = prefs.getString(KEY_SALT, null) ?: return false
        val storedHex = prefs.getString(KEY_HASH, null) ?: return false
        val salt = saltHex.fromHex()
        val expected = storedHex.fromHex()
        val actual = pbkdf2(pin, salt)
        val ok = constantTimeEquals(expected, actual)
        pin.fill('0')
        return ok
    }

    private fun isValidPin(pin: CharArray): Boolean =
        pin.size in 4..12 && pin.all { it.isDigit() }

    private fun pbkdf2(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.also { spec.clearPassword() }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val ITERATIONS = 120_000
    }
}
