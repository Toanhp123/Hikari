package app.openstory.benchmark

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import app.openstory.catalog.feature.seed.BenchmarkCatalogFixture
import app.openstory.startup.createAppLaunchStateStore
import kotlinx.coroutines.runBlocking

class BenchmarkLaunchStateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val persisted = runBlocking {
            val launchStatePersisted = createAppLaunchStateStore(applicationContext)
                .markInitialSetupCompleted()
            if (launchStatePersisted) {
                BenchmarkCatalogFixture.prepare(applicationContext)
            }
            launchStatePersisted
        }
        check(persisted) {
            "Benchmark launch-state fixture could not persist Ready state."
        }

        setContentView(
            TextView(this).apply {
                text = "HIKARI_V2_BENCHMARK_READY"
            },
        )
    }
}
