package app.openstory

import android.content.Context
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.install.PackageVerifier
import app.openstory.plugins.runtime.install.PluginInstaller
import app.openstory.plugins.runtime.install.TransactionalPluginPackageStorage
import app.openstory.plugins.runtime.persistence.StoredPluginState
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.plugins.RoomPluginStateStore
import java.security.MessageDigest

internal fun mangaDexPackageBytes(context: Context): ByteArray =
    context.assets.open("plugins/mangadex-content.osp").use { it.readBytes() }

internal suspend fun ensureMangaDexBundledPluginInstalled(
    context: Context,
    database: OpenStoryDatabase,
    packageBytes: ByteArray,
): PluginCallResult<StoredPluginState> {
    val state = RoomPluginStateStore(database)
    val packageSha256 = mangaDexSha256(packageBytes)
    val existing = state.find(PluginId(MANGADEX_PLUGIN_ID))
    if (existing?.activeVersion?.version == MANGADEX_PLUGIN_VERSION) {
        if (existing.activeVersion.sha256 != packageSha256) {
            return PluginCallResult.Failure("plugin.bundled_package_version_conflict", false)
        }
        val enabled = if (existing.enabled) existing else existing.copy(enabled = true)
        if (!existing.enabled) state.replace(enabled)
        return PluginCallResult.Success(enabled)
    }
    val storage = TransactionalPluginPackageStorage(context.filesDir.toPath().resolve("plugin-runtime"))
    return PluginInstaller(PackageVerifier(), storage, state).install(
        packageBytes = packageBytes,
        provenance = PluginArtifact(
            pluginId = MANGADEX_PLUGIN_ID,
            version = MANGADEX_PLUGIN_VERSION,
            downloadUrl = "https://bundled.openstory.app/mangadex-content.osp",
            sha256 = packageSha256,
        ),
    )
}

internal fun fixtureText(context: Context, name: String): String =
    context.assets.open("plugins/mangadex-content-fixtures/$name").bufferedReader().use { it.readText() }

internal fun mangaDexSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

internal const val MANGADEX_PLUGIN_ID = "org.openstory.content.mangadex"
internal const val MANGADEX_PLUGIN_VERSION = "1.3.0"
internal const val ONE_PIECE_MANGADEX_ID = "a1c7c817-4e59-43b7-9365-09675a149a6f"
internal const val ONE_PIECE_MANGADEX_URL = "https://mangadex.org/title/$ONE_PIECE_MANGADEX_ID"
