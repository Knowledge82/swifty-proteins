package dev.knowledge82.swiftyproteins

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.knowledge82.swiftyproteins.ui.LigandsViewModel
import dev.knowledge82.swiftyproteins.ui.LigandsViewModelFactory
import dev.knowledge82.swiftyproteins.ui.SwiftyApp
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyProteinsTheme

class MainActivity : ComponentActivity() {

    // Created the first time it is used and KEPT across rotations: the same object comes back after the
    // activity is destroyed and recreated. The lambda builds it only if it does not exist yet.
    private val viewModel: LigandsViewModel by viewModels { LigandsViewModelFactory(applicationContext) }

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
        setContent {
            SwiftyProteinsTheme {
                SwiftyApp(viewModel)
            }
        }
    }
}
