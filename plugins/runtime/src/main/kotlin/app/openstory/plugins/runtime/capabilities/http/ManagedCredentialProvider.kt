package app.openstory.plugins.runtime.capabilities.http

import app.openstory.common.id.PluginId

data class ManagedCredentialRequest(
    val pluginId: PluginId,
    val url: String,
) {
    init {
        require(url.isNotBlank()) { "Managed credential URL must not be blank" }
    }
}

fun interface ManagedCredentialProvider {
    suspend fun headers(request: ManagedCredentialRequest): Map<String, String>

    companion object {
        val NONE = ManagedCredentialProvider { emptyMap() }
    }
}
