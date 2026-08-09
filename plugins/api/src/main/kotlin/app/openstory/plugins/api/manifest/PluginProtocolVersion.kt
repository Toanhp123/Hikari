package app.openstory.plugins.api.manifest

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PluginProtocolVersion(val major: Int) {
    init {
        require(major > 0) { "Protocol major must be positive" }
    }
}
