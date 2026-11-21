package dev.knowledge82.swiftyproteins.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    // Tiny parameters: the tests check the LOGIC; the cost itself is checked by the official vector below.
    private val fast = HashParams(memoryKiB = 64, iterations = 1, parallelism = 1)

    @Test
    fun theOfficialArgon2idVectorOfRfc9106() {
        // RFC 9106, section 5.3: password 32 x 0x01, salt 16 x 0x02, secret 8 x 0x03, associated data 12 x 0x04,
        // 32 KiB, 3 passes, 4 lanes, 32-byte tag. Proves that Bouncy Castle is called as "Argon2id v1.3".
        val tag = Argon2Hasher.derive(
            password = ByteArray(32) { 1 },
            salt = ByteArray(16) { 2 },
            params = HashParams(memoryKiB = 32, iterations = 3, parallelism = 4),
            length = 32,
            secret = ByteArray(8) { 3 },
            associatedData = ByteArray(12) { 4 },
        )
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", Hex.encode(tag))
    }

    @Test
    fun theRightPasswordVerifiesAndAWrongOneDoesNot() {
        val hasher = Argon2Hasher(fast)
        val stored = hasher.hash("omnissiah7")
        assertTrue(hasher.verify("omnissiah7", stored))
        assertFalse(hasher.verify("omnissiah8", stored))
        assertFalse(hasher.verify("", stored))
    }

    @Test
    fun theSameSaltAndPasswordAlwaysGiveTheSameHash() {
        val salt = ByteArray(16) { 7 }
        val a = Argon2Hasher.derive(Argon2Hasher.passwordBytes("omnissiah7"), salt, fast, 32)
        val b = Argon2Hasher.derive(Argon2Hasher.passwordBytes("omnissiah7"), salt, fast, 32)
        assertArrayEquals(a, b)
    }

    @Test
    fun twoAccountsWithTheSamePasswordGetDifferentSaltsAndHashes() {
        val hasher = Argon2Hasher(fast)
        val one = hasher.hash("omnissiah7")
        val two = hasher.hash("omnissiah7")
        assertFalse(one.salt.contentEquals(two.salt))
        assertFalse(one.hash.contentEquals(two.hash))
        assertEquals(Argon2Hasher.SALT_BYTES, one.salt.size)
        assertEquals(Argon2Hasher.HASH_BYTES, one.hash.size)
    }

    @Test
    fun theParametersAreStoredWithTheHashAndUsedToVerify() {
        val stored = Argon2Hasher(fast).hash("omnissiah7")
        assertEquals(fast, stored.params)
        // A hasher with OTHER parameters for new hashes still checks the old hash with its own parameters.
        assertTrue(Argon2Hasher(HashParams(128, 2, 1)).verify("omnissiah7", stored))
    }

    @Test
    fun anAccentTypedTwoWaysIsTheSamePassword() {
        val hasher = Argon2Hasher(fast)
        val stored = hasher.hash("cafépass1")              // "é" as one character
        assertTrue(hasher.verify("cafépass1", stored))    // "e" + combining accent
    }

    @Test
    fun theDefaultParametersAreOwaspsMinimum() {
        assertEquals(HashParams(19_456, 2, 1), HashParams.OWASP)
        assertEquals(HashParams.OWASP, Argon2Hasher().params)
    }

    @Test
    fun thePasswordBytesAreWipedAfterHashing() {
        val bytes = Argon2Hasher.passwordBytes("omnissiah7")
        Argon2Hasher.derive(bytes, ByteArray(16), fast, 32)
        assertTrue(bytes.all { it == 0.toByte() })
    }
}
