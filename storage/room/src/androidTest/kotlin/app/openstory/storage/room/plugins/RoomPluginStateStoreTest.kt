package app.openstory.storage.room.plugins

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.runtime.persistence.StoredPluginState
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPluginStateStoreTest {
    @Test
    fun pluginStateAndVersionProvenanceRoundTrip() = runTest {
        withDatabase { database ->
            val store = RoomPluginStateStore(database)
            val expected = StoredPluginState(
                pluginId = PluginId("org.example.plugin"),
                services = setOf(PluginService.CATALOG),
                enabled = true,
                activeVersion = version("2.0.0"),
                previousVersion = version("1.0.0"),
                acceptedNetworkHosts = setOf("api.example.com"),
            )

            store.replace(expected)

            assertEquals(expected, store.find(expected.pluginId))
        }
    }

    private fun version(value: String) = StoredPluginVersion(
        version = value,
        packageLocation = "plugins/org.example.plugin/$value",
        sha256 = "a".repeat(64),
        signerFingerprint = null,
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
