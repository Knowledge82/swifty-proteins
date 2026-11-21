package dev.knowledge82.swiftyproteins

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.registerForAuthenticationResult
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.knowledge82.swiftyproteins.auth.toAuthenticationRequest
import dev.knowledge82.swiftyproteins.auth.toOutcome
import dev.knowledge82.swiftyproteins.sound.CogitatorSounds
import dev.knowledge82.swiftyproteins.sound.LocalSounds
import dev.knowledge82.swiftyproteins.ui.AuthViewModel
import dev.knowledge82.swiftyproteins.ui.AuthViewModelFactory
import dev.knowledge82.swiftyproteins.ui.LigandsViewModel
import dev.knowledge82.swiftyproteins.ui.LigandsViewModelFactory
import dev.knowledge82.swiftyproteins.ui.SwiftyApp
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyProteinsTheme

class MainActivity : ComponentActivity() {

    // Created the first time it is used and KEPT across rotations: the same object comes back after the
    // activity is destroyed and recreated. The lambda builds it only if it does not exist yet.
    private val viewModel: LigandsViewModel by viewModels { LigandsViewModelFactory(applicationContext) }
    private val authViewModel: AuthViewModel by viewModels { AuthViewModelFactory(applicationContext) }

    // The system's biometric dialog (androidx.biometric 1.4, document 05). It MUST be registered while the activity
    // is being created (like any "activity result"): a field initializer is the place the library recommends.
    // The result goes to the controller, which knows which request was on screen (even after a rotation).
    private val biometricLauncher = registerForAuthenticationResult { result ->
        authViewModel.controller.onBiometricOutcome(result.toOutcome())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // MUST come before super.onCreate: it swaps the "starting" theme for the normal one. The system splash (plain
        // abyss, empty icon: invisible) stays just as long as Android needs; the LAUNCH SCREEN that lasts over one
        // second is the cogitator boot screen drawn by the app itself (BootScreen, documents 08c and 08d).
        installSplashScreen()

        super.onCreate(savedInstanceState)
        // The app is always dark (D26): light icons on the system bars, whatever the phone's own theme. With the
        // default, a phone in LIGHT mode would draw dark icons on our dark background (invisible clock and battery).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // The "Recent apps" screen keeps a picture of the app; without this it would show the molecule WITHOUT signing
        // in (D43). Android 13+; not FLAG_SECURE, which would also blank our own share capture (document 05, 9).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)

        setContent {
            SwiftyProteinsTheme {
                val auth = authViewModel.controller
                val authState by auth.state.collectAsStateWithLifecycle()
                // A biometric dialog requested by the controller is shown ONCE (keyed by its id), then handed back.
                val prompt = authState.prompt
                LaunchedEffect(prompt?.id) {
                    if (prompt != null) {
                        biometricLauncher.launch(prompt.toAuthenticationRequest())
                        auth.onPromptShown(prompt.id)
                    }
                }
                // The cogitator's voice (document 14): one player for the whole app, reachable from every screen.
                CompositionLocalProvider(LocalSounds provides CogitatorSounds.of(this)) {
                    SwiftyApp(viewModel, auth)
                }
            }
        }
    }
}
