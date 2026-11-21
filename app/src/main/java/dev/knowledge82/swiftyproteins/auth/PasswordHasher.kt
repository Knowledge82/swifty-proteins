package dev.knowledge82.swiftyproteins.auth

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer

/** The cost of Argon2id: memory in KiB, passes over that memory, and parallel lanes. */
data class HashParams(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
    companion object {
        /** OWASP's minimum configuration for Argon2id: 19 MiB, 2 passes, 1 lane (document 05, D40). */
        val OWASP = HashParams(memoryKiB = 19_456, iterations = 2, parallelism = 1)
    }
}

/**
 * What is stored for a password: the parameters, the salt and the result. Never the password.
 * A class and not a data class: data classes compare ByteArrays by REFERENCE, which would make equals() lie.
 */
class PasswordHash(val params: HashParams, val salt: ByteArray, val hash: ByteArray)

/** Hashing and checking passwords. An interface so the account book can be tested with a fast fake. */
interface PasswordHasher {
    /** The parameters NEW hashes get. Older accounts keep theirs until they sign in again (then they are re-hashed). */
    val params: HashParams

    fun hash(password: String): PasswordHash

    fun verify(password: String, stored: PasswordHash): Boolean
}

/**
 * Argon2id with Bouncy Castle (document 05, D40). Pure Java: runs on the phone and in the JVM tests alike.
 *
 * SLOW ON PURPOSE (hundreds of milliseconds with [HashParams.OWASP], estimated): never call it on the UI thread.
 */
class Argon2Hasher(
    override val params: HashParams = HashParams.OWASP,
    private val random: SecureRandom = SecureRandom(),
) : PasswordHasher {

    override fun hash(password: String): PasswordHash {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PasswordHash(params, salt, derive(passwordBytes(password), salt, params, HASH_BYTES))
    }

    override fun verify(password: String, stored: PasswordHash): Boolean {
        val candidate = derive(passwordBytes(password), stored.salt, stored.params, stored.hash.size)
        // Constant time: compares ALL the bytes, whatever the first difference (document 05, section 2).
        return MessageDigest.isEqual(candidate, stored.hash)
    }

    companion object {
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32

        /**
         * The text of a password as bytes. NFC first: "é" can be typed as ONE character or as "e" + an accent; both
         * look the same and must give the same hash (RFC 8265 recommends normalising passwords).
         */
        fun passwordBytes(password: String): ByteArray =
            Normalizer.normalize(password, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)

        /**
         * Raw Argon2id (version 1.3). [secret] and [associatedData] are not used by the app; they exist so the tests
         * can check the official vector of RFC 9106, which uses them. The password bytes are wiped after use.
         */
        fun derive(
            password: ByteArray,
            salt: ByteArray,
            params: HashParams,
            length: Int,
            secret: ByteArray? = null,
            associatedData: ByteArray? = null,
        ): ByteArray {
            val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .withSalt(salt)
            if (secret != null) builder.withSecret(secret)
            if (associatedData != null) builder.withAdditional(associatedData)
            val out = ByteArray(length)
            try {
                Argon2BytesGenerator().apply { init(builder.build()) }.generateBytes(password, out)
            } finally {
                password.fill(0)
            }
            return out
        }
    }
}
