package app.universalmedia

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import app.universalmedia.data.RoomMediaStore
import app.universalmedia.data.UniversalMediaDatabase
import app.universalmedia.ingestion.local.LocalRootScanRunner
import app.universalmedia.ingestion.local.LocalScanWorkerFactory
import app.universalmedia.storage.local.SafLocalStorage

class UniversalMediaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val store = RoomMediaStore(UniversalMediaDatabase.open(this))
        val runner = LocalRootScanRunner(store, SafLocalStorage(contentResolver), store, store)
        WorkManager.initialize(
            this,
            Configuration.Builder().setWorkerFactory(LocalScanWorkerFactory(runner)).build(),
        )
    }
}
