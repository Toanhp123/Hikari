package app.openstory.plugins.api.packageformat

import kotlinx.serialization.Serializable

@Serializable
data class RepositoryIndex(
    val schema: Int,
    val artifacts: List<PluginArtifact>,
) {
    init {
        require(schema == CURRENT_SCHEMA) { "Unsupported repository index schema: $schema" }
        require(artifacts.size <= MAX_ARTIFACTS) { "Repository index has too many artifacts" }
        require(artifacts.map { it.pluginId to it.version }.distinct().size == artifacts.size) {
            "Repository index must not repeat a plugin version"
        }
    }

    companion object {
        const val CURRENT_SCHEMA = 1
        private const val MAX_ARTIFACTS = 10_000
    }
}
