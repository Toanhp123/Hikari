package app.openstory.benchmark

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import app.openstory.catalog.feature.fixture.BenchmarkCatalogDiagnostics
import app.openstory.catalog.feature.seed.BenchmarkCatalogFixture
import app.openstory.catalog.feature.seed.BenchmarkCatalogPreparation
import app.openstory.startup.createAppLaunchStateStore
import kotlinx.coroutines.runBlocking

class BenchmarkLaunchStateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val persisted = runBlocking {
            BenchmarkCatalogDiagnostics.reset()
            val launchStatePersisted = createAppLaunchStateStore(applicationContext)
                .markInitialSetupCompleted()
            if (launchStatePersisted) {
                val preparationEvidence = BenchmarkCatalogFixture.prepare(
                    context = applicationContext,
                    preparation = BenchmarkCatalogPreparation.fromWireValue(
                        intent.getStringExtra(EXTRA_PREPARATION) ?: BenchmarkCatalogPreparation.NORMAL.wireValue,
                    ),
                )
                BenchmarkCatalogDiagnostics.reset()
                BenchmarkPreparationEvidenceStore.write(applicationContext, preparationEvidence)
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

    private companion object {
        const val EXTRA_PREPARATION = "catalog-preparation"
    }
}
