package dev.knowledge82.swiftyproteins.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher

/** The phone's fingerprint machinery, seen from the pure code. The Android version lives in AndroidBiometrics.kt. */
interface BiometricGateway {
    fun availability(): BiometricAvailability

    /** A NEW key for this account (an old one is replaced) and a Cipher ready to ENCRYPT with it after the finger. */
    fun enrolCipher(username: String): Cipher

    /**
     * A Cipher ready to DECRYPT with this account's key after the finger.
     * @throws KeyInvalidatedException if the phone's fingerprints changed since the key was made.
     */
    fun signInCipher(username: String, iv: ByteArray): Cipher

    /** Deletes this account's key (if any). */
    fun forget(username: String)
}

class KeyInvalidatedException : Exception("biometric key permanently invalidated")

/** How the biometric dialog ended, as the activity reports it. */
sealed interface BiometricOutcome {
    /** The system accepted the finger; [cipher] is the one we gave it, now allowed to work (null = it did not come back). */
    data class Success(val cipher: Cipher?) : BiometricOutcome
    data class Failure(val error: BiometricError, val systemText: String?) : BiometricOutcome
}

enum class AuthScreen { LOADING, SIGN_IN, CREATE }

/**
 * The fingerprint button on the sign-in screen. It depends ONLY on the phone, never on the name typed: a button that
 * lit up for some names would tell anyone which accounts exist (document 05, D44).
 */
enum class BiometricButton {
    /** Not shown: the phone cannot (the note says why). */
    HIDDEN,
    SHOWN,
}

sealed interface AuthDialog {
    data class Message(val text: DialogText) : AuthDialog
    /** "Enable bio-auspex for adept42?" */
    data class OfferBiometric(val username: String) : AuthDialog
}

/** A request for the activity: show the system's biometric dialog with this cipher. [id] tells requests apart. */
data class PromptRequest(val id: Long, val purpose: Purpose, val username: String, val cipher: Cipher) {
    enum class Purpose { ENROL, SIGN_IN }
}

/** Everything the sign-in and create-account screens show. */
data class AuthUiState(
    val screen: AuthScreen = AuthScreen.LOADING,
    val unlocked: Boolean = false,
    val username: String = "",
    val password: String = "",
    val confirmation: String = "",
    /** A hash is being computed: buttons off, "Verifying..." shown. */
    val busy: Boolean = false,
    /** The line under the form: wrong password, blocked, name taken. */
    val formError: String? = null,
    val biometricButton: BiometricButton = BiometricButton.HIDDEN,
    val biometricNote: String? = null,
    val dialog: AuthDialog? = null,
    val prompt: PromptRequest? = null,
    /** At least one account exists (the create screen then offers "back to sign in"). */
    val hasAccounts: Boolean = false,
) {
    /** Create screen, live: problems of the name (only once something was typed). */
    val usernameProblem: UsernamePolicy.Problem? get() = if (username.isBlank()) null else UsernamePolicy.check(username)

    /** Create screen, live: the checklist. */
    val unmetRules: Set<PasswordPolicy.Rule> get() = PasswordPolicy.unmet(password, username)

    /** Create screen, live: the two passwords differ (only once the second was typed). */
    val confirmationDiffers: Boolean get() = confirmation.isNotEmpty() && confirmation != password

    val canCreate: Boolean
        get() = !busy && UsernamePolicy.check(username) == null && unmetRules.isEmpty() && confirmation == password

    val canSignIn: Boolean get() = !busy && username.isNotBlank() && password.isNotEmpty()
}

/**
 * The whole sign-in flow (document 05): pure Kotlin with coroutines, tested on the JVM with fakes.
 * The ViewModel only builds it with the real pieces and forwards the calls (as with LigandLoader).
 *
 * Threads: every call comes from the MAIN thread. The slow work (Argon2id, the file) runs on [work], one operation
 * at a time ([mutex]): two taps on "Sign in" never run two checks in parallel over the same account book.
 *
 * @param newToken the random bytes encrypted by the fingerprint key (a parameter so the tests can fix them)
 */
class AuthController(
    private val store: AccountStore,
    private val hasher: PasswordHasher,
    private val biometrics: BiometricGateway,
    private val scope: CoroutineScope,
    private val work: CoroutineDispatcher,
    private val now: () -> Long = System::currentTimeMillis,
    private val newToken: () -> ByteArray = { ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes) },
) {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    private val mutex = Mutex()
    private val session = SessionState()
    private var book: AccountBook? = null
    private var nextPromptId = 1L

    /**
     * +1 on every [lock]. A slow check that started BEFORE the app went to the background must not unlock it AFTER:
     * each operation remembers the number it started with and drops its result if it changed.
     */
    private var lockGeneration = 0

    /**
     * The biometric dialog that is ON SCREEN now. Kept here (in the ViewModel) and not in the activity: if the phone
     * is rotated while the dialog is open, the activity is recreated and the result arrives to the NEW one.
     */
    private var inFlight: PromptRequest? = null

    /** After "Account created", the fingerprint offer waits until that message is closed. */
    private var pendingOffer: String? = null

    /** The last save of the current operation failed: that message wins over a welcome (it matters more). */
    @Volatile private var saveFailed = false

    init {
        scope.launch {
            val (loaded, unreadable) = withContext(work) {
                try {
                    store.load() to false
                } catch (e: AccountFormatException) {
                    AccountsSnapshot.EMPTY to true
                }
            }
            book = AccountBook(loaded, hasher, now)
            mutableState.update {
                it.copy(
                    screen = if (loaded.accounts.isEmpty()) AuthScreen.CREATE else AuthScreen.SIGN_IN,
                    username = loaded.lastUsername.orEmpty(),
                    dialog = if (unreadable) AuthDialog.Message(UNREADABLE) else null,
                    hasAccounts = loaded.accounts.isNotEmpty(),
                )
            }
            refreshBiometricButton()
        }
    }

    // ---------------------------------------------------------------- form

    fun onUsernameChange(value: String) = mutableState.update { it.copy(username = value, formError = null) }

    fun onPasswordChange(value: String) = mutableState.update { it.copy(password = value, formError = null) }

    fun onConfirmationChange(value: String) = mutableState.update { it.copy(confirmation = value, formError = null) }

    fun showCreate() = mutableState.update {
        it.copy(screen = AuthScreen.CREATE, username = "", password = "", confirmation = "", formError = null)
    }

    /** Back from "create account" (only when there is an account to sign in to). */
    fun showSignIn() {
        val last = book?.snapshot?.lastUsername.orEmpty()
        mutableState.update { it.copy(screen = AuthScreen.SIGN_IN, username = last, password = "", confirmation = "", formError = null) }
        refreshBiometricButton()
    }

    // ---------------------------------------------------------------- password

    fun signIn() {
        val s = state.value
        if (!s.canSignIn) return
        mutableState.update { it.copy(busy = true, formError = null) }
        val generation = lockGeneration
        runWork {
            val book = book ?: return@runWork
            val result = withContext(work) {
                book.signIn(s.username, s.password).also { save(book) }
            }
            if (generation != lockGeneration) return@runWork   // locked meanwhile: lock() already reset the form
            when (result) {
                is SignInResult.Success -> unlocked(result.account, offer = true, welcome = null)
                SignInResult.Wrong -> mutableState.update { it.copy(busy = false, password = "", formError = WRONG) }
                is SignInResult.Blocked -> mutableState.update {
                    it.copy(busy = false, password = "", formError = LoginThrottle.message(result.remainingMillis))
                }
            }
        }
    }

    fun create() {
        val s = state.value
        if (!s.canCreate) return
        mutableState.update { it.copy(busy = true, formError = null) }
        val generation = lockGeneration
        runWork {
            val book = book ?: return@runWork
            val result = withContext(work) {
                book.create(s.username, s.password, s.confirmation).also { if (it is CreateResult.Created) save(book) }
            }
            if (result is CreateResult.Created) mutableState.update { it.copy(hasAccounts = true) }
            // Locked meanwhile: the account exists and is saved, but the session stays locked (sign in to enter).
            if (generation != lockGeneration) return@runWork
            when (result) {
                is CreateResult.Created -> unlocked(
                    result.account,
                    offer = true,
                    welcome = DialogText("Account Created", "Account created. Welcome, ${result.account.username}."),
                )
                CreateResult.Taken -> mutableState.update { it.copy(busy = false, formError = TAKEN) }
                // canCreate already checked the same rules: only reachable if they changed in between.
                is CreateResult.Invalid -> mutableState.update { it.copy(busy = false) }
            }
        }
    }

    // ---------------------------------------------------------------- fingerprint

    /** The fingerprint button on the sign-in screen. */
    fun startBiometricSignIn() {
        val s = state.value
        if (s.busy || s.prompt != null) return
        val account = book?.find(s.username)
        val link = account?.biometric
        if (account == null || link == null) {
            // The SAME words for "no such user" and "no fingerprint for this user".
            mutableState.update { it.copy(formError = NO_FINGERPRINT) }
            return
        }
        // Keystore calls go to the worker: they talk to the secure hardware and can take a moment.
        runWork {
            val cipher = try {
                withContext(work) { biometrics.signInCipher(account.username, link.iv) }
            } catch (e: KeyInvalidatedException) {
                keyInvalidated(account.username)
                return@runWork
            } catch (e: GeneralSecurityException) {
                showMessage(BiometricText.dialogFor(BiometricError.OTHER, e.javaClass.simpleName)!!)
                return@runWork
            }
            prompt(PromptRequest.Purpose.SIGN_IN, account.username, cipher)
        }
    }

    fun acceptBiometricOffer() {
        val offer = state.value.dialog as? AuthDialog.OfferBiometric ?: return
        mutableState.update { it.copy(dialog = null) }
        runWork {
            val cipher = try {
                withContext(work) { biometrics.enrolCipher(offer.username) }   // makes a new key: not instant
            } catch (e: GeneralSecurityException) {
                showMessage(BiometricText.dialogFor(BiometricError.OTHER, e.javaClass.simpleName)!!)
                return@runWork
            }
            prompt(PromptRequest.Purpose.ENROL, offer.username, cipher)
        }
    }

    fun declineBiometricOffer() {
        val offer = state.value.dialog as? AuthDialog.OfferBiometric ?: return
        mutableState.update { it.copy(dialog = null) }
        runWork {
            val book = book ?: return@runWork
            withContext(work) {
                book.markBiometricOffered(offer.username)
                save(book)
            }
        }
    }

    /** The activity showed the dialog for [id]: it is now in flight, and must not be shown twice. */
    fun onPromptShown(id: Long) {
        val request = state.value.prompt?.takeIf { it.id == id } ?: return
        inFlight = request
        mutableState.update { it.copy(prompt = null) }
    }

    /** The activity reports how the dialog that is on screen ended. Ignored if none is (e.g. it was locked meanwhile). */
    fun onBiometricOutcome(outcome: BiometricOutcome) {
        val request = inFlight ?: return
        inFlight = null
        when (outcome) {
            is BiometricOutcome.Failure -> {
                if (outcome.error == BiometricError.KEY_INVALIDATED) return runWork { keyInvalidated(request.username) }
                BiometricText.dialogFor(outcome.error, outcome.systemText)?.let(::showMessage)
            }
            is BiometricOutcome.Success -> {
                val cipher = outcome.cipher ?: request.cipher
                when (request.purpose) {
                    PromptRequest.Purpose.ENROL -> finishEnrol(request.username, cipher)
                    PromptRequest.Purpose.SIGN_IN -> finishSignIn(request.username, cipher)
                }
            }
        }
    }

    private fun finishEnrol(username: String, cipher: Cipher) = runWork {
        val book = book ?: return@runWork
        val token = newToken()
        val ok = withContext(work) {
            try {
                val encrypted = cipher.doFinal(token)
                book.linkBiometric(username, encrypted, cipher.iv, token)
                save(book)
                true
            } catch (e: GeneralSecurityException) {
                false
            } finally {
                token.fill(0)
            }
        }
        if (ok) {
            showMessage(DialogText("Bio-Auspex Enabled", "Bio-auspex enabled. Next time, sign in with your fingerprint."))
        } else {
            biometrics.forget(username)
            showMessage(BiometricText.dialogFor(BiometricError.OTHER)!!)
        }
        refreshBiometricButton()
    }

    private fun finishSignIn(username: String, cipher: Cipher) {
        val generation = lockGeneration
        runWork { completeSignIn(username, cipher, generation) }
    }

    private suspend fun completeSignIn(username: String, cipher: Cipher, generation: Int) {
        val book = book ?: return
        val account = withContext(work) {
            val link = book.find(username)?.biometric ?: return@withContext null
            val token = try {
                cipher.doFinal(link.encryptedToken)
            } catch (e: GeneralSecurityException) {
                return@withContext null
            }
            book.signInWithToken(username, token).also {
                token.fill(0)
                if (it != null) save(book)
            }
        }
        if (generation != lockGeneration) return
        if (account != null) unlocked(account, offer = false, welcome = null)
        else showMessage(BiometricText.dialogFor(BiometricError.OTHER)!!)
    }

    /**
     * The key died (new fingerprints on the phone): forget it, say why, unlink the account (offered again later).
     * Called from inside [runWork] (it already holds the mutex) or from [onBiometricOutcome] (it does not).
     */
    private suspend fun keyInvalidated(username: String) {
        biometrics.forget(username)
        showMessage(BiometricText.dialogFor(BiometricError.KEY_INVALIDATED)!!)
        val book = book ?: return
        withContext(work) {
            book.unlinkBiometric(username, askAgain = true)
            save(book)
        }
        refreshBiometricButton()
    }

    // ---------------------------------------------------------------- lock

    /** The app went to the background (ProcessLifecycleOwner ON_STOP): locked, whatever the screen (D43). */
    fun lock() {
        lockGeneration++
        pendingOffer = null
        inFlight = null
        session.lock()
        val name = session.username ?: book?.snapshot?.lastUsername.orEmpty()
        mutableState.update {
            it.copy(
                unlocked = false,
                screen = if (book?.isEmpty == true) AuthScreen.CREATE else AuthScreen.SIGN_IN,
                username = name,
                password = "",
                confirmation = "",
                formError = null,
                // An offer or a notice that was open belongs to the old session; a prompt not yet shown is dropped.
                dialog = null,
                prompt = null,
            )
        }
        refreshBiometricButton()
    }

    // ---------------------------------------------------------------- helpers

    private fun unlocked(account: Account, offer: Boolean, welcome: DialogText?) {
        session.unlock(account.username)
        val canOffer = offer && account.biometric == null && !account.biometricOffered &&
            biometrics.availability() == BiometricAvailability.AVAILABLE
        mutableState.update {
            it.copy(
                unlocked = true,
                busy = false,
                password = "",
                confirmation = "",
                formError = null,
                // A failed save first (the user must know); else the welcome; the offer after it is closed.
                dialog = when {
                    saveFailed -> AuthDialog.Message(SAVE_FAILED)
                    welcome != null -> AuthDialog.Message(welcome)
                    canOffer -> AuthDialog.OfferBiometric(account.username)
                    else -> null
                },
            )
        }
        pendingOffer = if ((welcome != null || saveFailed) && canOffer) account.username else null
    }

    /** Closes a message; if a welcome was hiding the fingerprint offer, the offer appears now. */
    fun closeMessage() {
        val offerFor = pendingOffer
        pendingOffer = null
        mutableState.update { it.copy(dialog = offerFor?.let { name -> AuthDialog.OfferBiometric(name) }) }
    }

    private fun prompt(purpose: PromptRequest.Purpose, username: String, cipher: Cipher) {
        mutableState.update { it.copy(prompt = PromptRequest(nextPromptId++, purpose, username, cipher)) }
    }

    private fun showMessage(text: DialogText) = mutableState.update { it.copy(dialog = AuthDialog.Message(text), busy = false) }

    private fun refreshBiometricButton() {
        val availability = biometrics.availability()
        mutableState.update {
            it.copy(
                biometricButton = if (availability == BiometricAvailability.AVAILABLE) BiometricButton.SHOWN else BiometricButton.HIDDEN,
                biometricNote = BiometricText.availabilityNote(availability),
            )
        }
    }

    /** Saves; a failure to write is shown, but the session goes on (the change stays in memory until next time). */
    private fun save(book: AccountBook) {
        try {
            store.save(book.snapshot)
        } catch (e: java.io.IOException) {
            saveFailed = true
            mutableState.update { it.copy(dialog = AuthDialog.Message(SAVE_FAILED)) }
        }
    }

    private fun runWork(block: suspend CoroutineScope.() -> Unit) {
        scope.launch {
            try {
                mutex.withLock {
                    saveFailed = false
                    block()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never a crash on the sign-in screen: an unexpected failure is a message and the form comes back.
                mutableState.update {
                    it.copy(busy = false, dialog = AuthDialog.Message(BiometricText.dialogFor(BiometricError.OTHER, e.javaClass.simpleName)!!))
                }
            }
        }
    }

    companion object {
        const val TOKEN_BYTES = 32
        const val WRONG = "Wrong username or password."
        const val TAKEN = "This user name is already taken."
        const val NO_FINGERPRINT = "Fingerprint sign-in is not set up for this user name. Sign in with your password."
        val UNREADABLE = DialogText(
            "Archive Unreadable",
            "Stored accounts could not be read on this device. Create a new account.",
        )
        val SAVE_FAILED = DialogText("Archive Not Saved", "The account data could not be saved on the phone. Try again.")
    }
}
