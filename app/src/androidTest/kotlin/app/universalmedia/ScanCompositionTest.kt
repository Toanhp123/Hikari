package app.universalmedia

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.universalmedia.core.model.RootId
import app.universalmedia.ingestion.local.LocalScanScheduler
import app.universalmedia.ingestion.local.LocalScanWorkerFactory
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCompositionTest {
    @Test
    fun applicationEagerlyInitializesCustomFactoryAndExecutesWorker() {
        val application = ApplicationProvider.getApplicationContext<UniversalMediaApplication>()
        val manager = WorkManager.getInstance(application)
        val rootId = RootId.generate()
        LocalScanScheduler(manager).enqueue(rootId).result.get(10, TimeUnit.SECONDS)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var state: WorkInfo.State
        do {
            state = manager.getWorkInfosForUniqueWork("scan-root:${rootId.value}")
                .get(10, TimeUnit.SECONDS).single().state
            if (state.isFinished) break
            Thread.sleep(50)
        } while (System.nanoTime() < deadline)
        // A missing root is a terminal domain result. Construction failure also fails work,
        // so assert the configured factory independently through WorkManager's configuration.
        assertTrue(manager.configuration.workerFactory is LocalScanWorkerFactory)
        assertEquals(WorkInfo.State.FAILED, state)
    }
}
