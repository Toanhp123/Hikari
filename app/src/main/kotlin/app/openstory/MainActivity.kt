package app.openstory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import app.openstory.designsystem.glass.HikariBackdropMode
import app.openstory.designsystem.surface.HikariSurfaceShadowMode
import app.openstory.ui.OpenStoryApp
import app.openstory.work.WorkManagerCanonicalEngineWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var canonicalEngineWorkScheduler: WorkManagerCanonicalEngineWorkScheduler

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
        val surfaceShadowMode = if (
            intent.getBooleanExtra(BENCHMARK_DISABLE_SURFACE_SHADOWS_EXTRA, false)
        ) {
            HikariSurfaceShadowMode.DISABLED_FOR_BENCHMARK
        } else {
            HikariSurfaceShadowMode.ENABLED
        }
        val useLegacyNavigationTransitions = intent.getBooleanExtra(
            BENCHMARK_LEGACY_NAVIGATION_TRANSITIONS_EXTRA,
            false,
        )
        setContent {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                canonicalEngineWorkScheduler.scheduleDrain()
                canonicalEngineWorkScheduler.ensureDailySafety()
            }
            OpenStoryApp(
                backdropMode = backdropMode,
                surfaceShadowMode = surfaceShadowMode,
                useLegacyNavigationTransitions = useLegacyNavigationTransitions,
            )
        }
    }
}

private const val BENCHMARK_DISABLE_BACKDROP_EXTRA = "app.openstory.benchmark.DISABLE_BACKDROP"
private const val BENCHMARK_DISABLE_SURFACE_SHADOWS_EXTRA =
    "app.openstory.benchmark.DISABLE_SURFACE_SHADOWS"
private const val BENCHMARK_LEGACY_NAVIGATION_TRANSITIONS_EXTRA =
    "app.openstory.benchmark.LEGACY_NAVIGATION_TRANSITIONS"
