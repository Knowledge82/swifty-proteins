package dev.knowledge82.swiftyproteins.auth

/**
 * Can this phone do a Class 3 (strong) biometric sign-in? The Android code maps BiometricManager's codes to this
 * (document 05, section 4); everything after that is pure and tested.
 */
enum class BiometricAvailability { AVAILABLE, NONE_ENROLLED, NO_HARDWARE, UNAVAILABLE, UPDATE_REQUIRED, UNSUPPORTED }

/** Why the biometric dialog ended without success. The Android code maps BiometricPrompt's error codes to this. */
enum class BiometricError {
    LOCKOUT,
    LOCKOUT_PERMANENT,
    TIMEOUT,
    NO_BIOMETRICS,
    HARDWARE,
    /** The user (Cancel, Back, "Use password") or the system (the app went away) closed it: not a failure. */
    CANCELED,
    /** The key died because the phone's fingerprints changed (KeyPermanentlyInvalidatedException). */
    KEY_INVALIDATED,
    OTHER,
}

/** A dialog: title (blackletter) and plain text. */
data class DialogText(val title: String, val message: String)

/** The words of the biometric sign-in (document 05, section 11). Pure Kotlin, tested. */
object BiometricText {

    /**
     * The dialog for an error, or null when NO dialog must be shown (the user cancelled on purpose).
     * [systemText] is Android's own message, added only for errors we have no better words for.
     */
    fun dialogFor(error: BiometricError, systemText: String? = null): DialogText? = when (error) {
        BiometricError.CANCELED -> null
        BiometricError.LOCKOUT -> DialogText(
            "Too Many Attempts",
            "Too many failed attempts. The fingerprint is disabled for 30 seconds. Use your password, or try again later.",
        )
        BiometricError.LOCKOUT_PERMANENT -> DialogText(
            "Biometrics Locked",
            "Too many failed attempts. Unlock the phone with its PIN, pattern or password to use the fingerprint again. " +
                "You can sign in with your password now.",
        )
        BiometricError.TIMEOUT -> DialogText("Time Out", "Authentication timed out. Please try again.")
        BiometricError.NO_BIOMETRICS -> DialogText(
            "No Fingerprint",
            "No fingerprint is enrolled on this phone. Add one in the phone settings, or use your password.",
        )
        BiometricError.HARDWARE -> DialogText(
            "Sensor Unavailable",
            "The biometric sensor is not available right now. Use your password.",
        )
        BiometricError.KEY_INVALIDATED -> DialogText(
            "Fingerprints Changed",
            "The fingerprints on this phone changed, so the fingerprint sign-in was turned off for safety. " +
                "Sign in with your password to turn it on again.",
        )
        BiometricError.OTHER -> DialogText(
            "Authentication Failed",
            "Authentication failed. Please try again." + (systemText?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
        )
    }

    /** Shown under the sign-in form when the fingerprint button is hidden; null = nothing to explain. */
    fun availabilityNote(availability: BiometricAvailability): String? = when (availability) {
        BiometricAvailability.AVAILABLE -> null
        BiometricAvailability.NONE_ENROLLED -> "Add a fingerprint in the phone settings to use the bio-auspex."
        BiometricAvailability.NO_HARDWARE, BiometricAvailability.UNSUPPORTED ->
            "This phone has no strong biometric sensor: sign in with your password."
        BiometricAvailability.UNAVAILABLE -> "The biometric sensor is not available right now: sign in with your password."
        BiometricAvailability.UPDATE_REQUIRED -> "A security update is needed to use the biometric sensor."
    }
}

/**
 * Locked or not (document 05, D43). Pure Kotlin, tested.
 *
 * Starts LOCKED. [lock] on every trip to the background; [unlock] after a good password or fingerprint. The user
 * name of the session is kept while locked: the sign-in form is filled with it when the app comes back.
 */
class SessionState {
    var username: String? = null
        private set
    var isLocked: Boolean = true
        private set

    fun unlock(username: String) {
        this.username = username
        isLocked = false
    }

    fun lock() {
        isLocked = true
    }
}
