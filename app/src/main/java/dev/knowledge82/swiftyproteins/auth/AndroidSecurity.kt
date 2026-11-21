package dev.knowledge82.swiftyproteins.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.AuthenticationRequest
import androidx.biometric.AuthenticationResult
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/*
 * The Android half of the sign-in (document 05): the Keystore keys and the mapping of androidx.biometric's codes to
 * the pure types. Everything with a decision in it lives in the pure files and is tested on the JVM; what is here is
 * checked on the phone (document 05, section 16).
 */

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val AES_GCM = "AES/GCM/NoPadding"

private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

/**
 * The key of the accounts file (D41): AES-256, created the first time, NO user authentication (the file must be read
 * before anyone signs in). It never leaves the secure hardware: the app only gets a handle.
 */
fun accountsKey(): SecretKey {
    (keyStore().getKey(ACCOUNTS_KEY_ALIAS, null) as? SecretKey)?.let { return it }
    val spec = KeyGenParameterSpec.Builder(ACCOUNTS_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
        init(spec)
        generateKey()
    }
}

/** The encrypted accounts file, in the folder Android never puts in backups (D41). */
fun accountStore(context: Context): AccountStore =
    EncryptedAccountStore(File(context.noBackupFilesDir, ACCOUNTS_FILE), AesGcmAead(::accountsKey))

private const val ACCOUNTS_KEY_ALIAS = "swifty_accounts"
private const val ACCOUNTS_FILE = "accounts.bin"

/** BiometricManager + one Keystore key per account that only works right after a Class 3 biometric (D42). */
class AndroidBiometricGateway(context: Context) : BiometricGateway {

    private val manager = BiometricManager.from(context.applicationContext)

    override fun availability(): BiometricAvailability =
        when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.UPDATE_REQUIRED
            else -> BiometricAvailability.UNSUPPORTED
        }

    override fun enrolCipher(username: String): Cipher {
        forget(username)   // a fresh key every time the fingerprint is turned on
        val builder = KeyGenParameterSpec.Builder(alias(username), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // Somebody who adds their finger to the phone does not inherit the access (document 05, section 3).
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 0 seconds = EVERY use needs a fresh biometric; only Class 3 (strong) counts.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)   // the same meaning before Android 11
        }
        val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(builder.build())
            generateKey()
        }
        // init() works without a finger; it is doFinal() that the hardware allows only after the biometric dialog.
        return Cipher.getInstance(AES_GCM).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    override fun signInCipher(username: String, iv: ByteArray): Cipher {
        val key = keyStore().getKey(alias(username), null) as? SecretKey ?: throw KeyInvalidatedException()
        return try {
            Cipher.getInstance(AES_GCM).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            // New fingerprints since the key was made: the key is dead for good.
            throw KeyInvalidatedException()
        }
    }

    override fun forget(username: String) {
        val store = keyStore()
        if (store.containsAlias(alias(username))) store.deleteEntry(alias(username))
    }

    // The name is [a-z0-9_.-] (UsernamePolicy): safe inside an alias.
    private fun alias(username: String) = "swifty_bio_$username"
}

/**
 * What the system's dialog for this request looks like. Class 3 with the CryptoObject: the dialog unlocks the KEY,
 * not just "a finger was seen". The one fallback is shown as the negative button: "Use password".
 */
fun PromptRequest.toAuthenticationRequest(): AuthenticationRequest.Biometric {
    val enrol = purpose == PromptRequest.Purpose.ENROL
    return AuthenticationRequest.Biometric.Builder(
        if (enrol) "Enable bio-auspex" else "Bio-auspex scan",
        AuthenticationRequest.Biometric.Fallback.CustomOption(USE_PASSWORD),
    )
        .setSubtitle(if (enrol) "Confirm with your fingerprint" else "Sign in as $username")
        .setMinStrength(AuthenticationRequest.Biometric.Strength.Class3(BiometricPrompt.CryptoObject(cipher)))
        .build()
}

/** androidx.biometric's result -> the pure [BiometricOutcome]. */
fun AuthenticationResult.toOutcome(): BiometricOutcome = when (this) {
    is AuthenticationResult.Success -> BiometricOutcome.Success(crypto?.cipher)
    is AuthenticationResult.Error -> BiometricOutcome.Failure(errorOf(errorCode), errString.toString())
    // "Use password": the user's choice, like Cancel.
    else -> BiometricOutcome.Failure(BiometricError.CANCELED, null)
}

private fun errorOf(code: Int): BiometricError = when (code) {
    BiometricPrompt.ERROR_LOCKOUT -> BiometricError.LOCKOUT
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricError.LOCKOUT_PERMANENT
    BiometricPrompt.ERROR_TIMEOUT -> BiometricError.TIMEOUT
    BiometricPrompt.ERROR_NO_BIOMETRICS -> BiometricError.NO_BIOMETRICS
    BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_HW_NOT_PRESENT -> BiometricError.HARDWARE
    BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED -> BiometricError.CANCELED
    else -> BiometricError.OTHER
}

private const val USE_PASSWORD = "Use password"
