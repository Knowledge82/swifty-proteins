package dev.knowledge82.swiftyproteins.auth

/**
 * The fingerprint sign-in of one account (document 05, D42): a random token, encrypted with this account's Keystore
 * key that only works right after a fingerprint, plus the SHA-256 of the token to recognise it after decrypting.
 */
class BiometricLink(val encryptedToken: ByteArray, val iv: ByteArray, val tokenSha256: ByteArray)

/** One account. Immutable: every change makes a copy (easy to test, nothing changes behind your back). */
data class Account(
    /** Already normalised (lower case, see [UsernamePolicy.normalize]). */
    val username: String,
    val password: PasswordHash,
    /** Wrong passwords in a row (the brake, [LoginThrottle]). */
    val failedAttempts: Int = 0,
    /** Wall-clock time (ms since 1970) until which sign-in is refused; 0 = not blocked. */
    val blockedUntilMillis: Long = 0,
    /** The fingerprint sign-in, or null when it is off. */
    val biometric: BiometricLink? = null,
    /**
     * Was "Enable bio-auspex?" already asked? It is asked once, after a password sign-in (or right after creating the
     * account). Turning the fingerprint off because the phone's fingerprints changed resets it: asked again next time.
     */
    val biometricOffered: Boolean = false,
)

/** Everything stored: the accounts and the name of the last one that signed in (to fill the field). */
data class AccountsSnapshot(val accounts: List<Account>, val lastUsername: String?) {
    companion object {
        val EMPTY = AccountsSnapshot(emptyList(), null)
    }
}

/** The stored data could not be read (damaged, from another version, cut off). */
class AccountFormatException(message: String) : Exception(message)

/**
 * The text format of the accounts file BEFORE encryption (document 05, D41). Pure Kotlin, tested.
 *
 * ```
 * SWIFTY-ACCOUNTS 1
 * last adept42                      ("last -" when nobody signed in yet)
 * account adept42 19456 2 1 <salt> <hash> 0 0 1 <token> <iv> <sha>   ("-" for the three when the fingerprint is off)
 * ```
 * Binary fields in hexadecimal: no org.json (not in the JVM tests) and no java.util.Base64 (missing on API 24-25).
 * A user name has no spaces ([UsernamePolicy]), so a space can separate the fields.
 */
object AccountCodec {

    private const val HEADER = "SWIFTY-ACCOUNTS 1"
    private const val NONE = "-"

    fun encode(snapshot: AccountsSnapshot): String = buildString {
        append(HEADER).append('\n')
        append("last ").append(snapshot.lastUsername ?: NONE).append('\n')
        for (a in snapshot.accounts) {
            val p = a.password
            val b = a.biometric
            append(
                listOf(
                    "account", a.username,
                    p.params.memoryKiB, p.params.iterations, p.params.parallelism,
                    Hex.encode(p.salt), Hex.encode(p.hash),
                    a.failedAttempts, a.blockedUntilMillis, if (a.biometricOffered) 1 else 0,
                    b?.encryptedToken?.let(Hex::encode) ?: NONE, b?.iv?.let(Hex::encode) ?: NONE,
                    b?.tokenSha256?.let(Hex::encode) ?: NONE,
                ).joinToString(" "),
            ).append('\n')
        }
    }

    /** @throws AccountFormatException on ANY problem: a damaged file is never half-read. */
    fun decode(text: String): AccountsSnapshot {
        val lines = text.split('\n').filter { it.isNotEmpty() }
        if (lines.firstOrNull() != HEADER) throw AccountFormatException("unknown header")
        val last = lines.getOrNull(1)?.split(' ')
        if (last == null || last.size != 2 || last[0] != "last") throw AccountFormatException("missing 'last' line")
        val accounts = lines.drop(2).map(::decodeAccount)
        if (accounts.map { it.username }.toSet().size != accounts.size) throw AccountFormatException("duplicate account")
        val lastName = last[1].takeUnless { it == NONE }
        return AccountsSnapshot(accounts, lastName)
    }

    private fun decodeAccount(line: String): Account {
        val f = line.split(' ')
        if (f.size != 13 || f[0] != "account") throw AccountFormatException("bad account line")
        if (UsernamePolicy.check(f[1]) != null || UsernamePolicy.normalize(f[1]) != f[1]) throw AccountFormatException("bad user name")
        val params = HashParams(int(f[2]), int(f[3]), int(f[4]))
        if (params.memoryKiB <= 0 || params.iterations <= 0 || params.parallelism <= 0) throw AccountFormatException("bad parameters")
        val bioParts = listOf(f[10], f[11], f[12])
        val biometric = when {
            bioParts.all { it == NONE } -> null
            bioParts.none { it == NONE } -> BiometricLink(Hex.decode(f[10]), Hex.decode(f[11]), Hex.decode(f[12]))
            else -> throw AccountFormatException("half a fingerprint link")
        }
        return Account(
            username = f[1],
            password = PasswordHash(params, Hex.decode(f[5]), Hex.decode(f[6])),
            failedAttempts = int(f[7]).also { if (it < 0) throw AccountFormatException("negative count") },
            blockedUntilMillis = f[8].toLongOrNull() ?: throw AccountFormatException("bad time"),
            biometricOffered = when (f[9]) { "1" -> true; "0" -> false; else -> throw AccountFormatException("bad flag") },
            biometric = biometric,
        )
    }

    private fun int(s: String): Int = s.toIntOrNull() ?: throw AccountFormatException("bad number")
}

/** Bytes <-> lowercase hexadecimal. */
object Hex {
    private const val DIGITS = "0123456789abcdef"

    fun encode(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            append(DIGITS[v ushr 4]).append(DIGITS[v and 0x0F])
        }
    }

    /** @throws AccountFormatException if it is not hexadecimal of even length (or is empty). */
    fun decode(text: String): ByteArray {
        if (text.isEmpty() || text.length % 2 != 0) throw AccountFormatException("bad hex length")
        return ByteArray(text.length / 2) { i ->
            val hi = DIGITS.indexOf(text[2 * i])
            val lo = DIGITS.indexOf(text[2 * i + 1])
            if (hi < 0 || lo < 0) throw AccountFormatException("bad hex digit")
            ((hi shl 4) or lo).toByte()
        }
    }
}
