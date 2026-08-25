package app.openstory.auth

import app.openstory.plugins.api.manifest.PluginAuthenticationCapability
import java.net.URI

class PluginLoginNavigationPolicy(
    private val capability: PluginAuthenticationCapability,
) {
    fun allows(url: String): Boolean = normalized(url)?.let { uri ->
        uri.scheme == "https" && uri.host in capability.navigationHosts
    } == true

    fun isCompletion(url: String): Boolean = normalized(url)?.let { uri ->
        uri.scheme == "https" &&
            uri.host == capability.completion.host &&
            uri.path.startsWith(capability.completion.pathPrefix)
    } == true

    private fun normalized(url: String): URI? = runCatching { URI(url).normalize() }.getOrNull()
        ?.takeIf { it.userInfo == null && it.port == -1 && it.fragment == null }
}
