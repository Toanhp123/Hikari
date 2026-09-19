package app.universalmedia

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import app.universalmedia.data.RoomMediaStore
import app.universalmedia.data.UniversalMediaDatabase
import app.universalmedia.ingestion.local.LocalRootScanRunner
import app.universalmedia.ingestion.local.LocalScanScheduler
import app.universalmedia.ingestion.local.LocalScanWorkerFactory
import app.universalmedia.source.api.SourceResolver
import app.universalmedia.source.local.LocalSourceResolver
import app.universalmedia.storage.local.SafLocalStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

class UniversalMediaApplication : Application() {
    internal lateinit var library: LibraryStateHolder
        private set
    internal lateinit var storage: SafLocalStorage
        private set
    internal lateinit var sources: SourceResolver
        private set

    override fun onCreate() {
        super.onCreate()
        val store = RoomMediaStore(UniversalMediaDatabase.open(this))
        storage = SafLocalStorage(contentResolver)
        sources = LocalSourceResolver(store.sources, storage)
        val runner = LocalRootScanRunner(store, storage, store, store)
        WorkManager.initialize(
            this,
            Configuration.Builder().setWorkerFactory(LocalScanWorkerFactory(runner)).build(),
        )
        val scheduler = LocalScanScheduler(WorkManager.getInstance(this))
        val coordinator = LibraryCoordinator(store) { rootId ->
            withContext(Dispatchers.IO) { scheduler.enqueue(rootId).result.get() }
        }
        library =
            LibraryStateHolder(
                store,
                coordinator,
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            )
    }
}
