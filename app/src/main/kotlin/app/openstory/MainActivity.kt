package app.openstory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.openstory.designsystem.glass.HikariBackdropMode
import app.openstory.ui.OpenStoryApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val backdropMode = if (intent.getBooleanExtra(BENCHMARK_DISABLE_BACKDROP_EXTRA, false)) {
            HikariBackdropMode.DISABLED_FOR_BENCHMARK
        } else {
            HikariBackdropMode.ENABLED
        }
        setContent {
            OpenStoryApp(backdropMode = backdropMode)
        }
    }
}

private const val BENCHMARK_DISABLE_BACKDROP_EXTRA = "app.openstory.benchmark.DISABLE_BACKDROP"
