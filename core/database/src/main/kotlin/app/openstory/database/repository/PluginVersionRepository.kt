package app.openstory.database.repository

import app.openstory.database.OpenStoryDatabase
import app.openstory.database.dao.PluginStateDao
import app.openstory.database.entity.PluginVersionEntity

data class PluginVersionSnapshot(
    val pluginId: String,
    val version: String,
    val packageSha256: String,
    val location: String,
    val signatureState: String,
    val signerKeyId: String?,
    val signerFingerprintSha256: String?,
    val installSource: String,
    val sourceReference: String,
    val acceptedCapabilities: Set<String>,
)

class PluginVersionRepository internal constructor(
    private val dao: PluginStateDao,
) {
    constructor(database: OpenStoryDatabase) : this(database.pluginStateDao())

    suspend fun pinActive(pluginId: String): PluginVersionSnapshot? {
        val activeVersion = dao.find(pluginId)?.activeVersion ?: return null
        return find(pluginId, activeVersion)
    }

    suspend fun findPrevious(pluginId: String): PluginVersionSnapshot? {
        val previousVersion = dao.find(pluginId)?.previousVersion ?: return null
        return find(pluginId, previousVersion)
    }

    suspend fun find(
        pluginId: String,
        version: String,
    ): PluginVersionSnapshot? = dao.findVersion(pluginId, version)?.toSnapshot()
}

private fun PluginVersionEntity.toSnapshot(): PluginVersionSnapshot = PluginVersionSnapshot(
    pluginId = pluginId,
    version = version,
    packageSha256 = packageSha256,
    location = location,
    signatureState = trustSignatureState,
    signerKeyId = signerKeyId,
    signerFingerprintSha256 = signerFingerprintSha256,
    installSource = installSource,
    sourceReference = sourceReference,
    acceptedCapabilities = acceptedCapabilities
        .split(',')
        .filter(String::isNotBlank)
        .toSet(),
)
