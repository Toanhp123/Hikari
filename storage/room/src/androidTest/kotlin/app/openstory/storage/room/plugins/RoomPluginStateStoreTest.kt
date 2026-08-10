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
import kotlin.test.assertFailsWith
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

    @Test
    fun activatingNewVersionKeepsImmutablePreviousProvenance() = runTest {
        withDatabase { database ->
            val store = RoomPluginStateStore(database)
            val first = StoredPluginState(
                pluginId = PluginId("org.example.plugin"),
                services = setOf(PluginService.CATALOG),
                enabled = true,
                activeVersion = version("1.0.0"),
                previousVersion = null,
                acceptedNetworkHosts = setOf("old.example.com"),
            )
            store.replace(first)
            val second = first.copy(
                services = setOf(PluginService.CONTENT),
                activeVersion = version("2.0.0"),
                previousVersion = version("1.0.0"),
                acceptedNetworkHosts = setOf("new.example.com"),
            )

            store.replace(second)

            assertEquals(second, store.find(second.pluginId))
        }
    }

    @Test
    fun rollbackRestoresCapabilitiesOwnedByPreviousVersion() = runTest {
        withDatabase { database ->
            val store = RoomPluginStateStore(database)
            val first = StoredPluginState(
                pluginId = PluginId("org.example.plugin"),
                services = setOf(PluginService.CATALOG),
                enabled = true,
                activeVersion = version("1.0.0"),
                previousVersion = null,
                acceptedNetworkHosts = setOf("old.example.com"),
            )
            store.replace(first)
            val second = first.copy(
                services = setOf(PluginService.CONTENT),
                activeVersion = version("2.0.0"),
                previousVersion = first.activeVersion,
                acceptedNetworkHosts = setOf("new.example.com"),
            )
            store.replace(second)

            store.replace(
                second.copy(
                    activeVersion = first.activeVersion,
                    previousVersion = second.activeVersion,
                ),
            )

            assertEquals(
                first.copy(previousVersion = second.activeVersion),
                store.find(first.pluginId),
            )
        }
    }

    @Test
    fun sameVersionWithDifferentArtifactProvenanceIsRejected() = runTest {
        withDatabase { database ->
            val store = RoomPluginStateStore(database)
            val original = StoredPluginState(
                pluginId = PluginId("org.example.plugin"),
                services = setOf(PluginService.CATALOG),
                enabled = true,
                activeVersion = version("1.0.0"),
                previousVersion = null,
                acceptedNetworkHosts = setOf("api.example.com"),
            )
            store.replace(original)

            assertFailsWith<IllegalStateException> {
                store.replace(
                    original.copy(
                        activeVersion = original.activeVersion.copy(
                            packageLocation = "plugins/tampered/1.0.0",
                            sha256 = "b".repeat(64),
                        ),
                    ),
                )
            }

            assertEquals(original, store.find(original.pluginId))
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
