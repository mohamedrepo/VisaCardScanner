package com.example.cardscanner.export

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-protected export (spec §18 Mode A).
 *
 * Excel's native password encryption (RC4/Agile) is not feasible on Android
 * without POI, so instead of pretending: the generated .xlsx is wrapped in a
 * single AES-256-GCM encrypted container (`.xlsxenc`) whose key is derived
 * from the administrator passphrase with PBKDF2-HMAC-SHA256 (210k
 * iterations, 16-byte random salt). The decrypted payload is the plain .xlsx.
 *
 * Security properties:
 *  - the export passphrase is never persisted anywhere;
 *  - the container authenticates (GCM tag) — wrong passphrase or tampering fails cleanly;
 *  - the file is useless without the password, so it is safe to share/store.
 *
 * A small companion decryptor script (Python) is documented in the README so
 * the administrator can open the file on a desktop.
 */
object EncryptedExport {

    private const val MAGIC = "FCE1" // Fleet Card Export, format 1
    private const val PBKDF2_ITERATIONS = 210_000
    private const val GCM_TAG_BITS = 128

    /** Encrypts [xlsxBytes] with [passphrase]; output = MAGIC | salt | iv | ciphertext. */
    fun encrypt(xlsxBytes: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.isNotEmpty()) { "export passphrase required" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(salt) // bind salt to ciphertext
        val encrypted = cipher.doFinal(xlsxBytes)
        return MAGIC.toByteArray() + salt + iv + encrypted
    }

    /** Decrypts a container produced by [encrypt]. Throws on wrong passphrase. */
    fun decrypt(container: ByteArray, passphrase: CharArray): ByteArray {
        require(container.size > 4 + 16 + 12) { "not a valid export container" }
        val magic = String(container, 0, 4, Charsets.US_ASCII)
        require(magic == MAGIC) { "not a valid export container" }
        val salt = container.copyOfRange(4, 20)
        val iv = container.copyOfRange(20, 32)
        val ciphertext = container.copyOfRange(32, container.size)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(salt)
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }
}
