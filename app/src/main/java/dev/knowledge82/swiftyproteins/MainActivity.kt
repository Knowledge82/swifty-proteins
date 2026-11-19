package dev.knowledge82.swiftyproteins

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.knowledge82.swiftyproteins.ui.LigandsViewModel
import dev.knowledge82.swiftyproteins.ui.LigandsViewModelFactory
import dev.knowledge82.swiftyproteins.ui.SwiftyApp
import dev.knowledge82.swiftyproteins.ui.theme.SwiftyProteinsTheme

class MainActivity : ComponentActivity() {

    // Created the first time it is used and KEPT across rotations: the same object comes back after the
    // activity is destroyed and recreated. The lambda builds it only if it does not exist yet.
    private val viewModel: LigandsViewModel by viewModels { LigandsViewModelFactory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwiftyProteinsTheme {
                SwiftyApp(viewModel)
            }
        }
    }
}
