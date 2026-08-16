package app.openstory.plugins.runtime.install

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.persistence.StoredPluginVersion
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransactionalPluginPackageStorage(
    rootDirectory: Path,
    private val stagingName: () -> String = { UUID.randomUUID().toString() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PluginPackageStorage {
    private val root = rootDirectory.toAbsolutePath().normalize()
    private val pluginsRoot = root.resolve("plugins").normalize()
    private val stagingRoot = root.resolve("staging").normalize()

    override suspend fun store(value: VerifiedPluginPackage): PluginCallResult<StoredPluginVersion> =
        withContext(ioDispatcher) {
            val destination = installedPath(value.pluginId, value.version)
            val staging = stagingRoot.resolve(stagingName()).normalize()
            when {
                destination == null || !staging.startsWith(stagingRoot) ->
                    PluginCallResult.Failure("plugin.package_path_invalid", false)
                Files.exists(destination) -> PluginCallResult.Failure("plugin.package_version_conflict", false)
                else -> storeAt(value, staging, destination)
            }
        }

    private fun storeAt(
        value: VerifiedPluginPackage,
        staging: Path,
        destination: Path,
    ): PluginCallResult<StoredPluginVersion> = try {
        deleteQuietly(staging)
        Files.createDirectories(staging)
        value.entries.forEach { (name, bytes) ->
            val target = staging.resolve(name).normalize()
            require(target.startsWith(staging)) { "Entry escapes staging" }
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        Files.createDirectories(destination.parent)
        Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
        makeReadOnly(destination)
        PluginCallResult.Success(
            StoredPluginVersion(value.version, destination.toString(), value.sha256, value.signerFingerprint),
        )
    } catch (_: RuntimeException) {
        deleteQuietly(staging)
        PluginCallResult.Failure("storage.plugin_package_write_failed", retryable = true)
    }

    override suspend fun readEntry(
        pluginId: PluginId,
        version: String,
        entry: String,
    ): PluginCallResult<ByteArray> = withContext(ioDispatcher) {
        val rootPath = installedPath(pluginId, version)
        when {
            entry != MAIN_SCRIPT && entry != MANIFEST ->
                PluginCallResult.Failure("plugin.package_entry_denied", false)
            rootPath == null -> PluginCallResult.Failure("plugin.package_path_invalid", false)
            else -> {
                val target = rootPath.resolve(entry).normalize()
                if (target.startsWith(rootPath) && Files.isRegularFile(target)) {
                    PluginCallResult.Success(Files.readAllBytes(target))
                } else {
                    PluginCallResult.Failure("plugin.package_entry_missing", false)
                }
            }
        }
    }

    override suspend fun remove(location: String) {
        withContext(ioDispatcher) {
            val path = Paths.get(location).toAbsolutePath().normalize()
            if (path.startsWith(root)) deleteQuietly(path)
        }
    }

    private fun installedPath(pluginId: PluginId, version: String): Path? {
        if (!SAFE_SEGMENT.matches(pluginId.value) || !SAFE_SEGMENT.matches(version)) return null
        val path = pluginsRoot.resolve(pluginId.value).resolve(version).normalize()
        return path.takeIf { it.startsWith(pluginsRoot) }
    }

    private companion object {
        val SAFE_SEGMENT = Regex("[A-Za-z0-9._-]+")
        const val MAIN_SCRIPT = "main.js"
        const val MANIFEST = "manifest.json"
    }
}

private fun makeReadOnly(root: Path) {
    Files.walk(root).use { paths -> paths.forEach { it.toFile().setWritable(false, false) } }
}

private fun deleteQuietly(root: Path) {
    if (!Files.exists(root)) return
    runCatching {
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach {
                it.toFile().setWritable(true, false)
                Files.deleteIfExists(it)
            }
        }
    }
}
