package dev.knowledge82.swiftyproteins.ui

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.knowledge82.swiftyproteins.auth.AccountStore
import dev.knowledge82.swiftyproteins.auth.AndroidBiometricGateway
import dev.knowledge82.swiftyproteins.auth.Argon2Hasher
import dev.knowledge82.swiftyproteins.auth.AuthController
import dev.knowledge82.swiftyproteins.auth.BiometricGateway
import dev.knowledge82.swiftyproteins.auth.PasswordHasher
import dev.knowledge82.swiftyproteins.auth.accountStore
import kotlinx.coroutines.Dispatchers

/**
 * Thin wrapper, like [LigandsViewModel]: it keeps the [AuthController] alive across rotations and connects it to the
 * life of the WHOLE app (document 05, D43). All the rules are in the controller, tested on the JVM.
 */
class AuthViewModel(
    store: AccountStore,
    hasher: PasswordHasher,
    biometrics: BiometricGateway,
) : ViewModel() {

    // Dispatchers.Default: Argon2id is pure computation (the accounts file is tiny, so it goes there too).
    val controller = AuthController(store, hasher, biometrics, viewModelScope, Dispatchers.Default)

    // ON_STOP of the PROCESS: no activity of the app is visible any more (Home, another app, screen off, share sheet).
    // Rotating does NOT send it (ProcessLifecycleOwner waits a moment before deciding), nor does the biometric dialog.
    private val lockOnBackground = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) controller.lock()
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lockOnBackground)
    }

    // The process lifecycle lives as long as the app: without this, the observer (and this ViewModel) would stay
    // registered after the activity is finished for good.
    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lockOnBackground)
    }
}

/** Builds the [AuthViewModel] with the real pieces: encrypted file, Argon2id, the phone's biometrics. */
class AuthViewModelFactory(context: Context) : ViewModelProvider.Factory {

    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            "This factory only builds AuthViewModel, not ${modelClass.name}"
        }
        return AuthViewModel(accountStore(appContext), Argon2Hasher(), AndroidBiometricGateway(appContext)) as T
    }
}
