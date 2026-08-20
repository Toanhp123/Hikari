package app.openstory.plugins.runtime.install

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginCapabilities
import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.manifest.PluginProtocolVersion
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest

class TransactionalPluginPackageStorageTest {
    private val root = Files.createTempDirectory("plugin-package-storage")

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `package filesystem work starts on the injected io dispatcher`() = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "plugin-package-io-test")
        }
        val ioDispatcher = executor.asCoroutineDispatcher()
        var stagingThread: String? = null
        try {
            val storage = TransactionalPluginPackageStorage(
                rootDirectory = root,
                stagingName = {
                    stagingThread = Thread.currentThread().name
                    "stage-1"
                },
                ioDispatcher = ioDispatcher,
            )

            val stored = assertIs<PluginCallResult.Success<StoredPluginVersion>>(storage.store(pluginPackage()))
            val version = stored.value
            val script = assertIs<PluginCallResult.Success<ByteArray>>(
                storage.readEntry(PluginId("org.example.plugin"), "1.0.0", "main.js"),
            )

            assertEquals("plugin-package-io-test", stagingThread)
            assertContentEquals("export default {}".encodeToByteArray(), script.value)

            storage.remove(version.packageLocation)
            assertFalse(Files.exists(Paths.get(version.packageLocation)))
        } finally {
            ioDispatcher.close()
            executor.shutdownNow()
        }
    }

    private fun pluginPackage() = VerifiedPluginPackage(
        pluginId = PluginId("org.example.plugin"),
        version = "1.0.0",
        sha256 = "a".repeat(64),
        signerFingerprint = null,
        manifest = PluginManifest(
            id = "org.example.plugin",
            name = "Example",
            version = "1.0.0",
            protocol = PluginProtocolVersion(1),
            provides = setOf(PluginService.CATALOG),
            capabilities = PluginCapabilities(),
        ),
        entries = mapOf(
            "main.js" to "export default {}".encodeToByteArray(),
            "manifest.json" to "{}".encodeToByteArray(),
        ),
    )
}
