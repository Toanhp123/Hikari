package app.openstory

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.lifecycleScope
import app.openstory.designsystem.glass.HikariBackdropMode
import app.openstory.designsystem.surface.HikariSurfaceShadowMode
import app.openstory.ui.OpenStoryApp
import app.openstory.navigation.AppRoute
import app.openstory.navigation.NotificationIntentParser
import app.openstory.work.WorkManagerCanonicalEngineWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var canonicalEngineWorkScheduler: WorkManagerCanonicalEngineWorkScheduler
    @Inject
    lateinit var notificationIntentParser: NotificationIntentParser

    private var notificationRoute by mutableStateOf<AppRoute?>(null)

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
                notificationRoute = notificationRoute,
                onNotificationRouteConsumed = { notificationRoute = null },
            )
        }
        parseNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseNotificationIntent(intent)
    }

    private fun parseNotificationIntent(intent: Intent) {
        lifecycleScope.launch {
            try {
                notificationRoute = notificationIntentParser.route(intent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notificationRoute = null
            }
        }
    }
}

private const val BENCHMARK_DISABLE_BACKDROP_EXTRA = "app.openstory.benchmark.DISABLE_BACKDROP"
private const val BENCHMARK_DISABLE_SURFACE_SHADOWS_EXTRA =
    "app.openstory.benchmark.DISABLE_SURFACE_SHADOWS"
private const val BENCHMARK_LEGACY_NAVIGATION_TRANSITIONS_EXTRA =
    "app.openstory.benchmark.LEGACY_NAVIGATION_TRANSITIONS"
