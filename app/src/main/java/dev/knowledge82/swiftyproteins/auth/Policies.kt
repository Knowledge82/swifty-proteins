package dev.knowledge82.swiftyproteins.auth

import java.util.Locale

/**
 * The rules for a user name (document 05, D44). Pure Kotlin, tested.
 *
 * A name is compared WITHOUT case: "Adept" and "adept" are the same account. So the name is normalised once, here,
 * and everything else (the account book, the key alias of the fingerprint) only sees the normalised form.
 */
object UsernamePolicy {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20

    /** Why a name is not acceptable. */
    enum class Problem { EMPTY, TOO_SHORT, TOO_LONG, BAD_CHARACTERS }

    /**
     * Spaces at both ends removed, lower case. Locale.ROOT and not the phone's language: in Turkish, "I" lower-cased
     * is a dotless "ı", so the same name typed on a Turkish phone would become another account. (Kotlin's
     * lowercase() without arguments already uses Locale.ROOT; Java's toLowerCase() does NOT. Written out on purpose.)
     */
    fun normalize(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    /** null = acceptable. Checked on the NORMALISED name. */
    fun check(raw: String): Problem? {
        val name = normalize(raw)
        return when {
            name.isEmpty() -> Problem.EMPTY
            name.length < MIN_LENGTH -> Problem.TOO_SHORT
            name.length > MAX_LENGTH -> Problem.TOO_LONG
            !name.all(::isAllowed) -> Problem.BAD_CHARACTERS
            else -> null
        }
    }

    /** The line shown under the field. */
    fun message(problem: Problem): String = when (problem) {
        Problem.EMPTY -> "Enter a user name."
        Problem.TOO_SHORT -> "The user name needs at least $MIN_LENGTH characters."
        Problem.TOO_LONG -> "The user name can have at most $MAX_LENGTH characters."
        Problem.BAD_CHARACTERS -> "Use only letters a-z, digits, and . _ -"
    }

    // ASCII only on purpose: the name becomes part of a Keystore alias and of a text file; look-alike letters from
    // other alphabets ("а" Cyrillic vs "a" Latin) would make two names that look identical.
    private fun isAllowed(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '.' || c == '-'
}

/**
 * The minimum requirements of a password (document 05, D44): shown as a checklist while the user types, and checked
 * again before creating the account. Pure Kotlin, tested.
 */
object PasswordPolicy {

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64

    /** Each rule, with the text of its line in the checklist. */
    enum class Rule(val label: String) {
        LENGTH("$MIN_LENGTH to $MAX_LENGTH characters"),
        LETTER("At least one letter"),
        DIGIT("At least one digit"),
        NOT_USERNAME("Does not contain the user name"),
    }

    /**
     * The rules this password does NOT meet yet (empty set = acceptable).
     * Length counts CHARACTERS as the user sees them (code points): an emoji is one, not two UTF-16 units.
     */
    fun unmet(password: String, username: String): Set<Rule> {
        val result = mutableSetOf<Rule>()
        val length = password.codePointCount(0, password.length)
        if (length < MIN_LENGTH || length > MAX_LENGTH) result += Rule.LENGTH
        if (password.none { it.isLetter() }) result += Rule.LETTER
        if (password.none { it.isDigit() }) result += Rule.DIGIT
        val name = UsernamePolicy.normalize(username)
        // Without a name there is nothing to compare: the rule is not failed because of an empty field.
        if (name.isNotEmpty() && password.lowercase(Locale.ROOT).contains(name)) result += Rule.NOT_USERNAME
        return result
    }

    fun isAcceptable(password: String, username: String): Boolean = unmet(password, username).isEmpty()
}

/**
 * The brake against guessing passwords on the phone itself (document 05, D44). Pure Kotlin, tested.
 *
 * The first [FREE_ATTEMPTS] - 1 failures cost nothing; at the [FREE_ATTEMPTS]-th the account waits [FIRST_WAIT_MILLIS],
 * and every further failure doubles the wait, up to [MAX_WAIT_MILLIS]. A good sign-in resets everything.
 */
object LoginThrottle {

    const val FREE_ATTEMPTS = 5
    const val FIRST_WAIT_MILLIS = 30_000L
    const val MAX_WAIT_MILLIS = 300_000L

    /** How long to wait after [failures] failed attempts in a row. */
    fun waitAfter(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        // The exponent is capped BEFORE the shift: "30 000 shl 60" overflows into nonsense (even negative numbers).
        val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
        return (FIRST_WAIT_MILLIS shl doublings).coerceAtMost(MAX_WAIT_MILLIS)
    }

    /** "Too many attempts. Try again in 25 s." / "... in 4 min 10 s." (rounded UP: never says 0 s while still blocked). */
    fun message(remainingMillis: Long): String {
        val seconds = ((remainingMillis + 999) / 1000).coerceAtLeast(1)
        val text = if (seconds < 60) "$seconds s" else if (seconds % 60 == 0L) "${seconds / 60} min" else "${seconds / 60} min ${seconds % 60} s"
        return "Too many failed attempts. Try again in $text."
    }

    // 30 s doubled 4 times is 480 s, already over the 300 s cap: more doublings would change nothing.
    private const val MAX_DOUBLINGS = 4
}
