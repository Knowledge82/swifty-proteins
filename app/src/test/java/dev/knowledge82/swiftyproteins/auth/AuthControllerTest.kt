package dev.knowledge82.swiftyproteins.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executor
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** An account store in memory that can be told to fail. */
class MemoryStore(var saved: AccountsSnapshot = AccountsSnapshot.EMPTY) : AccountStore {
    var unreadable = false
    var failSaves = false
    var saves = 0
    override fun load(): AccountsSnapshot = if (unreadable) throw AccountFormatException("broken") else saved
    override fun save(snapshot: AccountsSnapshot) {
        if (failSaves) throw IOException("disk full")
        saves++
        saved = snapshot
    }
}

/** Fingerprint keys made with ordinary software AES: the ciphers behave like the real ones after a finger. */
class FakeBiometrics(var availability: BiometricAvailability = BiometricAvailability.AVAILABLE) : BiometricGateway {
    val keys = mutableMapOf<String, SecretKey>()
    val invalidated = mutableSetOf<String>()
    val forgotten = mutableListOf<String>()
    override fun availability() = availability
    override fun enrolCipher(username: String): Cipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        keys[username] = key
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }
    override fun signInCipher(username: String, iv: ByteArray): Cipher {
        if (username in invalidated) throw KeyInvalidatedException()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, keys.getValue(username), GCMParameterSpec(128, iv)) }
    }
    override fun forget(username: String) {
        forgotten += username
        keys.remove(username)
    }
}

class AuthControllerTest {

    private val store = MemoryStore()
    private val bio = FakeBiometrics()
    private var clock = 5_000_000L

    // Unconfined: every coroutine runs at once, in the calling thread, so the tests read like plain code.
    private fun controller(work: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined) = AuthController(
        store, FakeHasher(), bio, CoroutineScope(Dispatchers.Unconfined), work, now = { clock }, newToken = { byteArrayOf(4, 2, 4, 2) },
    )

    private fun AuthController.typeNewAccount(name: String = "Adept42", password: String = "omnissiah7") {
        onUsernameChange(name)
        onPasswordChange(password)
        onConfirmationChange(password)
    }

    private fun AuthController.enrolWithFinger() {
        acceptBiometricOffer()
        val request = state.value.prompt!!
        assertEquals(PromptRequest.Purpose.ENROL, request.purpose)
        onPromptShown(request.id)
        onBiometricOutcome(BiometricOutcome.Success(request.cipher))
    }

    @Test
    fun withNoAccountsTheCreateScreenComesFirst() {
        val c = controller()
        assertEquals(AuthScreen.CREATE, c.state.value.screen)
        assertFalse(c.state.value.hasAccounts)
        assertFalse(c.state.value.unlocked)
    }

    @Test
    fun withAccountsTheSignInScreenComesFirstFilledWithTheLastUser() {
        store.saved = AccountsSnapshot(listOf(Account("magos", FakeHasher().hash("machine99"))), "magos")
        val c = controller()
        assertEquals(AuthScreen.SIGN_IN, c.state.value.screen)
        assertEquals("magos", c.state.value.username)
        assertTrue(c.state.value.hasAccounts)
    }

    @Test
    fun anUnreadableStoreSaysSoAndStartsEmpty() {
        store.unreadable = true
        val c = controller()
        assertEquals(AuthScreen.CREATE, c.state.value.screen)
        assertEquals(AuthDialog.Message(AuthController.UNREADABLE), c.state.value.dialog)
    }

    @Test
    fun theCreateFormIsCheckedLiveAndTheButtonWaitsForEverything() {
        val c = controller()
        c.onUsernameChange("ab")
        assertEquals(UsernamePolicy.Problem.TOO_SHORT, c.state.value.usernameProblem)
        c.onUsernameChange("adept42")
        c.onPasswordChange("omnissiah7")
        assertFalse(c.state.value.canCreate)               // confirmation missing
        c.onConfirmationChange("omnissiah")
        assertTrue(c.state.value.confirmationDiffers)
        c.onConfirmationChange("omnissiah7")
        assertTrue(c.state.value.canCreate)
    }

    @Test
    fun creatingAnAccountUnlocksWelcomesThenOffersTheFingerprint() {
        val c = controller()
        c.typeNewAccount()
        c.create()
        val s = c.state.value
        assertTrue(s.unlocked)
        assertEquals("Account Created", (s.dialog as AuthDialog.Message).text.title)
        assertEquals("", s.password)                                  // never kept after use
        assertEquals("adept42", store.saved.accounts.single().username)
        c.closeMessage()
        assertEquals(AuthDialog.OfferBiometric("adept42"), c.state.value.dialog)
    }

    @Test
    fun noFingerprintOfferWhenThePhoneCannot() {
        bio.availability = BiometricAvailability.NONE_ENROLLED
        val c = controller()
        c.typeNewAccount()
        c.create()
        c.closeMessage()
        assertNull(c.state.value.dialog)
        assertEquals(BiometricButton.HIDDEN, c.state.value.biometricButton)
        assertNotNull(c.state.value.biometricNote)
    }

    @Test
    fun aTakenNameIsSaidUnderTheForm() {
        val c = controller()
        c.typeNewAccount()
        c.create()
        c.lock()
        c.showCreate()
        c.typeNewAccount(name = "ADEPT42", password = "another9x")
        c.create()
        assertEquals(AuthController.TAKEN, c.state.value.formError)
        assertFalse(c.state.value.unlocked)
    }

    @Test
    fun theWholeFingerprintRoundTrip() {
        val c = controller()
        c.typeNewAccount()
        c.create()
        c.closeMessage()
        c.enrolWithFinger()
        assertEquals("Bio-Auspex Enabled", (c.state.value.dialog as AuthDialog.Message).text.title)
        assertNotNull(store.saved.accounts.single().biometric)

        c.lock()
        assertFalse(c.state.value.unlocked)
        assertEquals(AuthScreen.SIGN_IN, c.state.value.screen)
        assertEquals("adept42", c.state.value.username)
        assertEquals(BiometricButton.SHOWN, c.state.value.biometricButton)

        c.startBiometricSignIn()
        val request = c.state.value.prompt!!
        assertEquals(PromptRequest.Purpose.SIGN_IN, request.purpose)
        c.onPromptShown(request.id)
        c.onBiometricOutcome(BiometricOutcome.Success(null))   // the cipher may not come back: ours is used
        assertTrue(c.state.value.unlocked)
    }

    @Test
    fun aPromptIsHandedOverOnlyOnce() {
        val c = controller()
        c.typeNewAccount()
        c.create()
        c.closeMessage()
        c.acceptBiometricOffer()
        val id = c.state.value.prompt!!.id
        c.onPromptShown(id)
        assertNull(c.state.value.prompt)
    }

    @Test
    fun cancellingTheFingerprintShowsNothingAndALockoutExplainsItself() {
        val c = controller()
        c.typeNewAccount()
        c.create()
        c.closeMessage()
        c.enrolWithFinger()
        c.lock()
        c.startBiometricSignIn()
        c.onPromptShown(c.state.value.prompt!!.id)
        c.onBiometricOutcome(BiometricOutcome.Failure(BiometricError.CANCELED, "Cancelled"))
        assertNull(c.state.value.dialog)
        assertFalse(c.state.value.unlocked)
        c.startBiometricSignIn()
        c.onPromptShown(c.state.value.prompt!!.id)
        c.onBiometricOutcome(BiometricOutcome.Failure(BiometricError.LOCKOUT, null))
        assertEquals("Too Many Attempts", (c.state.value.dialog as AuthDialog.Message).text.title)
    }

    @Test
    fun newFingerprintsOnThePhoneTurnTheFingerprintSignInOff() {
        val c = controller()
        c.typeNewAccount()
        c.create()
        c.closeMessage()
        c.enrolWithFinger()
        c.lock()
        bio.invalidated += "adept42"
        c.startBiometricSignIn()
        assertNull(c.state.value.prompt)
        assertEquals("Fingerprints Changed", (c.state.value.dialog as AuthDialog.Message).text.title)
        assertEquals(listOf("adept42"), bio.forgotten)
        assertNull(store.saved.accounts.single().biometric)
        assertFalse(store.saved.accounts.single().biometricOffered)   // offered again after the next password sign-in
        c.closeMessage()
        c.startBiometricSignIn()
        assertEquals(AuthController.NO_FINGERPRINT, c.state.value.formError)
    }

    @Test
    fun aWrongPasswordIsSaidAndTheFifthBlocks() {
        val c = controller()
        c.typeNewAccount()
        c.create()
        c.lock()
        repeat(4) {
            c.onPasswordChange("bad")
            c.signIn()
            assertEquals(AuthController.WRONG, c.state.value.formError)
            assertEquals("", c.state.value.password)
        }
        c.onPasswordChange("bad")
        c.signIn()
        assertEquals("Too many failed attempts. Try again in 30 s.", c.state.value.formError)
        assertEquals(5, store.saved.accounts.single().failedAttempts)   // the brake survives a restart
    }

    @Test
    fun aPasswordSignInOffersTheFingerprintOnceAndDecliningIsRemembered() {
        store.saved = AccountsSnapshot(listOf(Account("magos", FakeHasher().hash("machine99"))), "magos")
        val c = controller()
        c.onPasswordChange("machine99")
        c.signIn()
        assertTrue(c.state.value.unlocked)
        assertEquals(AuthDialog.OfferBiometric("magos"), c.state.value.dialog)
        c.declineBiometricOffer()
        assertNull(c.state.value.dialog)
        assertTrue(store.saved.accounts.single().biometricOffered)
        c.lock()
        c.onPasswordChange("machine99")
        c.signIn()
        assertNull(c.state.value.dialog)
    }

    @Test
    fun lockingForgetsTheTypedPasswordAndAnyOpenDialog() {
        val c = controller()
        c.typeNewAccount()
        c.create()
        c.lock()
        c.onPasswordChange("half typed")
        c.lock()
        assertEquals("", c.state.value.password)
        assertNull(c.state.value.dialog)
        assertNull(c.state.value.prompt)
    }

    @Test
    fun aCheckThatEndsAfterTheAppWentAwayDoesNotUnlockIt() {
        val pending = ArrayDeque<Runnable>()
        val work = Executor { pending.add(it) }.asCoroutineDispatcher()
        store.saved = AccountsSnapshot(listOf(Account("magos", FakeHasher().hash("machine99"))), "magos")
        val c = controller(work)
        while (pending.isNotEmpty()) pending.removeFirst().run()        // loading the store
        c.onPasswordChange("machine99")
        c.signIn()
        assertTrue(c.state.value.busy)
        c.lock()                                                         // Home pressed while hashing
        while (pending.isNotEmpty()) pending.removeFirst().run()
        assertFalse(c.state.value.unlocked)
    }

    @Test
    fun aFailedSaveIsSaidButDoesNotStopTheSession() {
        val c = controller()
        store.failSaves = true
        c.typeNewAccount()
        c.create()
        assertTrue(c.state.value.unlocked)
        assertEquals(0, store.saves)
        // The user is in, and is TOLD the account was not saved (a welcome would hide it).
        assertEquals(AuthDialog.Message(AuthController.SAVE_FAILED), c.state.value.dialog)
        c.closeMessage()
        assertEquals(AuthDialog.OfferBiometric("adept42"), c.state.value.dialog)
    }

    @Test
    fun nothingHappensWhenTheButtonsShouldBeOff() {
        val c = controller()
        c.create()                                  // empty form
        assertFalse(c.state.value.busy)
        c.signIn()
        assertFalse(c.state.value.busy)
        assertEquals(0, store.saves)
    }

    @Test
    fun aResultWithNoDialogOnScreenIsIgnored() {
        val c = controller()
        c.typeNewAccount()
        c.create()
        c.closeMessage()
        c.enrolWithFinger()
        c.lock()
        c.startBiometricSignIn()
        c.onPromptShown(c.state.value.prompt!!.id)
        c.lock()                                                         // Home while the dialog was up
        c.onBiometricOutcome(BiometricOutcome.Success(null))
        assertFalse(c.state.value.unlocked)
    }

    @Test
    fun theSystemCanAlsoReportThatTheKeyDied() {
        val c = controller()
        c.typeNewAccount()
        c.create()
        c.closeMessage()
        c.enrolWithFinger()
        c.lock()
        c.startBiometricSignIn()
        c.onPromptShown(c.state.value.prompt!!.id)
        c.onBiometricOutcome(BiometricOutcome.Failure(BiometricError.KEY_INVALIDATED, null))
        assertEquals("Fingerprints Changed", (c.state.value.dialog as AuthDialog.Message).text.title)
        assertNull(store.saved.accounts.single().biometric)
    }

    @Test
    fun aWrongTokenDoesNotUnlock() {
        val c = controller()
        c.typeNewAccount()
        c.create()
        c.closeMessage()
        c.enrolWithFinger()
        c.lock()
        // A cipher that decrypts with ANOTHER key: GCM refuses it (the tag does not match).
        val link = store.saved.accounts.single().biometric!!
        val foreign = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        bio.keys["adept42"] = foreign
        c.startBiometricSignIn()
        c.onPromptShown(c.state.value.prompt!!.id)
        c.onBiometricOutcome(BiometricOutcome.Success(null))
        assertFalse(c.state.value.unlocked)
        assertEquals("Authentication Failed", (c.state.value.dialog as AuthDialog.Message).text.title)
        assertNotNull(link)
    }

    @Test
    fun theFingerprintButtonSaysTheSameForAnUnknownNameAndAnAccountWithoutFingerprint() {
        store.saved = AccountsSnapshot(listOf(Account("magos", FakeHasher().hash("machine99"))), "magos")
        val c = controller()
        assertEquals(BiometricButton.SHOWN, c.state.value.biometricButton)
        c.startBiometricSignIn()
        val forExisting = c.state.value.formError
        c.onUsernameChange("nobody")
        assertEquals(BiometricButton.SHOWN, c.state.value.biometricButton)
        c.startBiometricSignIn()
        assertEquals(forExisting, c.state.value.formError)
        assertEquals(AuthController.NO_FINGERPRINT, forExisting)
        assertNull(c.state.value.prompt)
    }

    @Test
    fun aDoubleTapOnTheFingerprintButtonAsksOnlyOnce() {
        val c = controller()
        c.typeNewAccount()
        c.create()
        c.closeMessage()
        c.enrolWithFinger()
        c.lock()
        c.startBiometricSignIn()
        val first = c.state.value.prompt!!.id
        c.startBiometricSignIn()
        assertEquals(first, c.state.value.prompt!!.id)
    }
}
