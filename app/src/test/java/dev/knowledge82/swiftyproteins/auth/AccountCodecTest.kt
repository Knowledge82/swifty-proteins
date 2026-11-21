package dev.knowledge82.swiftyproteins.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountCodecTest {

    private val plain = Account("adept42", PasswordHash(HashParams(19_456, 2, 1), byteArrayOf(1, 2, 0x7F), byteArrayOf(-1, 0, 16)))
    private val withFinger = Account(
        username = "magos.x",
        password = PasswordHash(HashParams(64, 3, 1), byteArrayOf(9), byteArrayOf(8)),
        failedAttempts = 6,
        blockedUntilMillis = 1_763_700_000_123,
        biometric = BiometricLink(byteArrayOf(5, 6), byteArrayOf(7), byteArrayOf(-128)),
        biometricOffered = true,
    )

    @Test
    fun whatIsWrittenIsReadBackExactly() {
        val back = AccountCodec.decode(AccountCodec.encode(AccountsSnapshot(listOf(plain, withFinger), "magos.x")))
        assertEquals("magos.x", back.lastUsername)
        assertEquals(2, back.accounts.size)
        val a = back.accounts[0]
        assertEquals("adept42", a.username)
        assertEquals(HashParams(19_456, 2, 1), a.password.params)
        assertArrayEquals(byteArrayOf(1, 2, 0x7F), a.password.salt)
        assertArrayEquals(byteArrayOf(-1, 0, 16), a.password.hash)
        assertNull(a.biometric)
        val b = back.accounts[1]
        assertEquals(6, b.failedAttempts)
        assertEquals(1_763_700_000_123, b.blockedUntilMillis)
        assertEquals(true, b.biometricOffered)
        assertArrayEquals(byteArrayOf(5, 6), b.biometric!!.encryptedToken)
        assertArrayEquals(byteArrayOf(7), b.biometric!!.iv)
        assertArrayEquals(byteArrayOf(-128), b.biometric!!.tokenSha256)
    }

    @Test
    fun noAccountsAndNobodySignedInYet() {
        val back = AccountCodec.decode(AccountCodec.encode(AccountsSnapshot.EMPTY))
        assertEquals(AccountsSnapshot.EMPTY, back)
    }

    @Test
    fun theFileIsReadableText() {
        val text = AccountCodec.encode(AccountsSnapshot(listOf(plain), "adept42"))
        assertEquals(
            "SWIFTY-ACCOUNTS 1\nlast adept42\naccount adept42 19456 2 1 01027f ff0010 0 0 0 - - -\n",
            text,
        )
    }

    @Test
    fun aDamagedFileIsRejectedNeverHalfRead() {
        val good = AccountCodec.encode(AccountsSnapshot(listOf(plain), "adept42"))
        val broken = listOf(
            "",                                                     // empty
            good.replace("SWIFTY-ACCOUNTS 1", "SWIFTY-ACCOUNTS 2"), // another version
            good.replace("last adept42\n", ""),                     // no 'last' line
            good.replace(" 01027f ", " 01027 "),                    // odd hex length
            good.replace(" 01027f ", " 01027g "),                   // not hex
            good.replace(" 19456 ", " 0 "),                         // impossible cost
            good.replace(" 19456 ", " lots "),                      // not a number
            good.replace("adept42 19456", "Adept42 19456"),         // not normalised
            good.replace(" 0 0 0 - - -", " 0 0 0 0a - -"),          // half a fingerprint link
            good.replace(" 0 0 0 -", " 0 0 2 -"),                   // bad flag
            good.replace(" 0 0 0 -", " -1 0 0 -"),                  // negative count
            good + good.lines()[2] + "\n",                          // the same account twice
            good.trimEnd().dropLast(2) + "\n",                      // line cut off
        )
        for (text in broken) assertThrows(text, AccountFormatException::class.java) { AccountCodec.decode(text) }
    }

    @Test
    fun hexRoundTripsEveryByte() {
        val all = ByteArray(256) { it.toByte() }
        assertArrayEquals(all, Hex.decode(Hex.encode(all)))
        assertEquals("00ff80", Hex.encode(byteArrayOf(0, -1, -128)))
    }
}
