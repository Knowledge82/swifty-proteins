package dev.knowledge82.swiftyproteins.auth

import java.security.MessageDigest

/** The result of "create account". */
sealed interface CreateResult {
    data class Created(val account: Account) : CreateResult
    /** The form is not valid: what is wrong with each field (null / empty = that field is fine). */
    data class Invalid(
        val username: UsernamePolicy.Problem?,
        val password: Set<PasswordPolicy.Rule>,
        val confirmationDiffers: Boolean,
    ) : CreateResult
    data object Taken : CreateResult
}

/** The result of a password sign-in. */
sealed interface SignInResult {
    data class Success(val account: Account) : SignInResult
    /** ONE answer for "no such user" and "wrong password" (document 05, D44). */
    data object Wrong : SignInResult
    data class Blocked(val remainingMillis: Long) : SignInResult
}

/**
 * The accounts and the rules to change them (document 05). Pure Kotlin, tested with a fake hasher and a fake clock.
 *
 * NOT thread-safe and SLOW (each password check is an Argon2id hash): the ViewModel calls it from ONE background
 * coroutine at a time. It only changes [snapshot]; saving it to the encrypted file is the caller's job.
 *
 * @param now wall-clock time in ms. Wall clock because the brake must survive a restart (the "since boot" clock starts
 *   again from zero). Known limit: someone who changes the phone's date can shorten the wait (document 05, 15).
 */
class AccountBook(
    initial: AccountsSnapshot,
    private val hasher: PasswordHasher,
    private val now: () -> Long,
) {
    var snapshot: AccountsSnapshot = initial
        private set

    val isEmpty: Boolean get() = snapshot.accounts.isEmpty()

    fun find(rawUsername: String): Account? {
        val name = UsernamePolicy.normalize(rawUsername)
        return snapshot.accounts.firstOrNull { it.username == name }
    }

    fun create(rawUsername: String, password: String, confirmation: String): CreateResult {
        val nameProblem = UsernamePolicy.check(rawUsername)
        val unmet = PasswordPolicy.unmet(password, rawUsername)
        val differs = password != confirmation
        if (nameProblem != null || unmet.isNotEmpty() || differs) return CreateResult.Invalid(nameProblem, unmet, differs)
        if (find(rawUsername) != null) return CreateResult.Taken
        val account = Account(UsernamePolicy.normalize(rawUsername), hasher.hash(password))
        snapshot = snapshot.copy(accounts = snapshot.accounts + account, lastUsername = account.username)
        return CreateResult.Created(account)
    }

    fun signIn(rawUsername: String, password: String): SignInResult {
        val account = find(rawUsername)
        if (account == null) {
            // Same work as a real check, so the time does not reveal that the name does not exist.
            hasher.verify(password, decoy)
            return SignInResult.Wrong
        }
        val remaining = account.blockedUntilMillis - now()
        if (remaining > 0) return SignInResult.Blocked(remaining)

        if (!hasher.verify(password, account.password)) {
            val failures = account.failedAttempts + 1
            val wait = LoginThrottle.waitAfter(failures)
            replace(account.copy(failedAttempts = failures, blockedUntilMillis = if (wait > 0) now() + wait else 0))
            return if (wait > 0) SignInResult.Blocked(wait) else SignInResult.Wrong
        }
        // Right password: brake reset, and an old hash is upgraded to today's parameters (D40).
        val upgraded = if (account.password.params != hasher.params) hasher.hash(password) else account.password
        val updated = account.copy(password = upgraded, failedAttempts = 0, blockedUntilMillis = 0)
        replace(updated)
        snapshot = snapshot.copy(lastUsername = updated.username)
        return SignInResult.Success(updated)
    }

    /** A fingerprint sign-in that decrypted the token: checks it belongs to this account. */
    fun signInWithToken(username: String, token: ByteArray): Account? {
        val account = find(username) ?: return null
        val link = account.biometric ?: return null
        if (!MessageDigest.isEqual(sha256(token), link.tokenSha256)) return null
        val updated = account.copy(failedAttempts = 0, blockedUntilMillis = 0)
        replace(updated)
        snapshot = snapshot.copy(lastUsername = updated.username)
        return updated
    }

    fun linkBiometric(username: String, encryptedToken: ByteArray, iv: ByteArray, token: ByteArray) {
        val account = find(username) ?: return
        replace(account.copy(biometric = BiometricLink(encryptedToken, iv, sha256(token)), biometricOffered = true))
    }

    /** [askAgain]: true when it was turned off by the system (fingerprints changed), so it is offered again. */
    fun unlinkBiometric(username: String, askAgain: Boolean) {
        val account = find(username) ?: return
        replace(account.copy(biometric = null, biometricOffered = account.biometricOffered && !askAgain))
    }

    fun markBiometricOffered(username: String) {
        val account = find(username) ?: return
        replace(account.copy(biometricOffered = true))
    }

    private fun replace(updated: Account) {
        snapshot = snapshot.copy(accounts = snapshot.accounts.map { if (it.username == updated.username) updated else it })
    }

    // A fixed, made-up hash with today's parameters: checking against it costs exactly one real check.
    private val decoy by lazy { PasswordHash(hasher.params, ByteArray(Argon2Hasher.SALT_BYTES), ByteArray(Argon2Hasher.HASH_BYTES)) }

    companion object {
        fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
