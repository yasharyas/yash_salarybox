package com.yasharya.attendance.data.local

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 over a per-user random salt.
 *
 * The credentials in this app are deliberately dummy ones, so this is not
 * protecting anything valuable. It is here because storing a plaintext password
 * column is the kind of thing that quietly survives into a real product, and
 * because the platform ships everything needed for the correct version at no
 * cost in dependencies.
 */
object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            Base64.decode(salt, Base64.NO_WRAP),
            ITERATIONS,
            KEY_LENGTH_BITS,
        )
        val key = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    /** Constant-time compare so a wrong password cannot be narrowed down by timing. */
    fun verify(password: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(
            hash(password, salt).toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8),
        )
}
