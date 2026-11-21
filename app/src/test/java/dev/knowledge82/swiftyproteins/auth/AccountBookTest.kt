package dev.knowledge82.swiftyproteins.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountBookTest {

    private var clock = 1_000_000L
    private val hasher = FakeHasher()
    private val book = AccountBook(AccountsSnapshot.EMPTY, hasher) { clock }

    private fun created() = book.create("Adept42", "omnissiah7", "omnissiah7")

    @Test
    fun anAccountIsCreatedWithTheNormalisedNameAndBecomesTheLastUser() {
        val result = created()
        assertTrue(result is CreateResult.Created)
        assertEquals("adept42", (result as CreateResult.Created).account.username)
        assertEquals("adept42", book.snapshot.lastUsername)
        assertFalse(book.isEmpty)
    }

    @Test
    fun theSameNameWithOtherCaseIsTaken() {
        created()
        assertEquals(CreateResult.Taken, book.create(" ADEPT42", "another9x", "another9x"))
    }

    @Test
    fun anInvalidFormSaysWhatIsWrongWithEachFieldAndCreatesNothing() {
        val result = book.create("ab", "short", "shorter")
        assertEquals(
            CreateResult.Invalid(UsernamePolicy.Problem.TOO_SHORT, setOf(PasswordPolicy.Rule.LENGTH, PasswordPolicy.Rule.DIGIT), true),
            result,
        )
        assertTrue(book.isEmpty)
        assertEquals(0, hasher.hashes)   // nothing slow is done for a form that is not valid
    }

    @Test
    fun theRightPasswordSignsInAndTheWrongOneDoesNot() {
        created()
        assertTrue(book.signIn("ADEPT42", "omnissiah7") is SignInResult.Success)
        assertEquals(SignInResult.Wrong, book.signIn("adept42", "omnissiah8"))
    }

    @Test
    fun anUnknownNameGetsTheSameAnswerAndCostsTheSameWork() {
        created()
        val before = hasher.verifications
        assertEquals(SignInResult.Wrong, book.signIn("nobody", "omnissiah7"))
        assertEquals(before + 1, hasher.verifications)   // the decoy check
    }

    @Test
    fun theFifthWrongPasswordBlocksForThirtySeconds() {
        created()
        repeat(4) { assertEquals(SignInResult.Wrong, book.signIn("adept42", "bad")) }
        assertEquals(SignInResult.Blocked(30_000), book.signIn("adept42", "bad"))
        // Even the RIGHT password is refused while blocked, without spending a hash.
        val before = hasher.verifications
        clock += 29_999
        assertEquals(SignInResult.Blocked(1), book.signIn("adept42", "omnissiah7"))
        assertEquals(before, hasher.verifications)
        // After the wait, the right password works and resets the brake.
        clock += 1
        assertTrue(book.signIn("adept42", "omnissiah7") is SignInResult.Success)
        assertEquals(0, book.find("adept42")!!.failedAttempts)
        assertEquals(0L, book.find("adept42")!!.blockedUntilMillis)
    }

    @Test
    fun afterAWaitTheNextWrongPasswordDoublesIt() {
        created()
        repeat(5) { book.signIn("adept42", "bad") }
        clock += 30_000
        assertEquals(SignInResult.Blocked(60_000), book.signIn("adept42", "bad"))
    }

    @Test
    fun anOldHashIsUpgradedToTodaysParametersOnSignIn() {
        created()
        hasher.params = HashParams(2, 2, 1)
        book.signIn("adept42", "omnissiah7")
        assertEquals(HashParams(2, 2, 1), book.find("adept42")!!.password.params)
    }

    @Test
    fun aFingerprintTokenSignsInOnlyIfItIsTheRightOne() {
        created()
        assertNull(book.signInWithToken("adept42", byteArrayOf(1, 2, 3)))   // not linked yet
        book.linkBiometric("adept42", encryptedToken = byteArrayOf(9), iv = byteArrayOf(8), token = byteArrayOf(1, 2, 3))
        assertTrue(book.find("adept42")!!.biometricOffered)
        assertNull(book.signInWithToken("adept42", byteArrayOf(1, 2, 4)))
        assertNotNull(book.signInWithToken("ADEPT42", byteArrayOf(1, 2, 3)))
        assertNull(book.signInWithToken("nobody", byteArrayOf(1, 2, 3)))
    }

    @Test
    fun aFingerprintSignInAlsoResetsTheBrake() {
        created()
        book.linkBiometric("adept42", byteArrayOf(9), byteArrayOf(8), byteArrayOf(1))
        repeat(3) { book.signIn("adept42", "bad") }
        book.signInWithToken("adept42", byteArrayOf(1))
        assertEquals(0, book.find("adept42")!!.failedAttempts)
    }

    @Test
    fun unlinkingBecauseTheFingerprintsChangedOffersItAgain() {
        created()
        book.linkBiometric("adept42", byteArrayOf(9), byteArrayOf(8), byteArrayOf(1))
        book.unlinkBiometric("adept42", askAgain = true)
        assertNull(book.find("adept42")!!.biometric)
        assertFalse(book.find("adept42")!!.biometricOffered)
    }

    @Test
    fun unlinkingByChoiceDoesNotAskAgain() {
        created()
        book.linkBiometric("adept42", byteArrayOf(9), byteArrayOf(8), byteArrayOf(1))
        book.unlinkBiometric("adept42", askAgain = false)
        assertTrue(book.find("adept42")!!.biometricOffered)
    }

    @Test
    fun decliningTheOfferIsRemembered() {
        created()
        book.markBiometricOffered("adept42")
        assertTrue(book.find("adept42")!!.biometricOffered)
    }

    @Test
    fun otherAccountsAreNotTouched() {
        created()
        book.create("magos", "machine99", "machine99")
        repeat(5) { book.signIn("adept42", "bad") }
        assertEquals(0, book.find("magos")!!.failedAttempts)
        assertTrue(book.signIn("magos", "machine99") is SignInResult.Success)
        assertEquals("magos", book.snapshot.lastUsername)
    }

    @Test
    fun aSignInMakesThatAccountTheLastUser() {
        created()
        book.create("magos", "machine99", "machine99")   // the last one created is "magos"
        book.signIn("adept42", "omnissiah7")
        assertEquals("adept42", book.snapshot.lastUsername)
        book.linkBiometric("magos", byteArrayOf(9), byteArrayOf(8), byteArrayOf(1))
        book.signInWithToken("magos", byteArrayOf(1))
        assertEquals("magos", book.snapshot.lastUsername)
    }
}
