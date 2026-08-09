package app.openstory.storage.room.plugins

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.persistence.PluginDiagnosticEvent
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPluginDiagnosticsSinkTest {
    @Test
    fun diagnosticsAreNewestFirstBoundedAndRedacted() = runTest {
        withDatabase { database ->
            val pluginId = PluginId("org.example.plugin")
            val sink = RoomPluginDiagnosticsSink(database)
            sink.record(event(pluginId, 1, "first"))
            sink.record(event(pluginId, 2, "second"))

            assertEquals(
                listOf("second"),
                sink.recent(pluginId, limit = 1).map { it.safeDetail },
            )
            assertFalse(sink.recent(pluginId, 10).joinToString().contains("marker-secret"))
        }
    }

    private fun event(pluginId: PluginId, at: Long, detail: String) = PluginDiagnosticEvent(
        pluginId = pluginId,
        code = "plugin.execution_failed",
        operation = "catalog.home",
        occurredAtEpochMillis = at,
        safeDetail = detail,
    )

    private suspend fun withDatabase(block: suspend (OpenStoryDatabase) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OpenStoryDatabase::class.java,
        ).build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }
}
