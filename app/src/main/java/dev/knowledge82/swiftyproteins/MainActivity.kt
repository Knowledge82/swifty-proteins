package dev.knowledge82.swiftyproteins

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.knowledge82.swiftyproteins.splash.SplashHold
import dev.knowledge82.swiftyproteins.ui.LigandsViewModel
import dev.knowledge82.swiftyproteins.ui.LigandsViewModelFactory
import dev.knowledge82.swiftyproteins.ui.SwiftyApp
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyProteinsTheme

class MainActivity : ComponentActivity() {

    // Created the first time it is used and KEPT across rotations: the same object comes back after the
    // activity is destroyed and recreated. The lambda builds it only if it does not exist yet.
    private val viewModel: LigandsViewModel by viewModels { LigandsViewModelFactory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // MUST come before super.onCreate: it swaps the "starting" theme for the normal one and takes over the splash.
        // The system removes the splash when the first frame is ready; we ask it to stay at least SplashHold.MIN_VISIBLE_MILLIS.
        val hold = SplashHold(startMillis = SystemClock.uptimeMillis())
        installSplashScreen().setKeepOnScreenCondition { hold.shouldKeep(SystemClock.uptimeMillis()) }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwiftyProteinsTheme {
                SwiftyApp(viewModel)
            }
        }
    }
}
