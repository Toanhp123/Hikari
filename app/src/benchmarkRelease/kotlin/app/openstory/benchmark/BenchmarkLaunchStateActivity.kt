package app.openstory.benchmark

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import app.openstory.startup.createAppLaunchStateStore
import kotlinx.coroutines.runBlocking

class BenchmarkLaunchStateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val persisted = runBlocking {
            createAppLaunchStateStore(applicationContext)
                .markInitialSetupCompleted()
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
