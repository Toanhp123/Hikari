package app.openstory.plugins.api.manifest

import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable
data class PluginAuthenticationCapability(
    val loginStartUrl: String,
    val navigationHosts: Set<String>,
    val completion: PluginAuthenticationCompletionTarget,
    val credentialTargets: List<PluginAuthenticationCredentialTarget>,
    val sessionTtlSeconds: Long,
) {
    init {
        require(navigationHosts.isNotEmpty() && navigationHosts.all(::isNormalizedHttpsHost))
        require(credentialTargets.isNotEmpty())
        require(credentialTargets.distinctBy { it.host to it.pathPrefix }.size == credentialTargets.size)
        require(sessionTtlSeconds in MIN_SESSION_TTL_SECONDS..MAX_SESSION_TTL_SECONDS)
        val login = requireHttpsAuthenticationUrl(loginStartUrl, allowQuery = true)
        require(login.host in navigationHosts)
        require(completion.host in navigationHosts)
    }

    fun policyFingerprint(): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalPolicy().encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    internal fun canonicalPolicy(): String = buildString {
        appendField(normalizedAuthenticationUrl(loginStartUrl, allowQuery = true))
        navigationHosts.sorted().forEach { appendField(it) }
        appendField(completion.host)
        appendField(completion.pathPrefix)
        credentialTargets.sortedWith(compareBy({ it.host }, { it.pathPrefix })).forEach { target ->
            appendField(target.host)
            appendField(target.pathPrefix)
            target.cookieNames.sorted().forEach { appendField(it) }
        }
        appendField(sessionTtlSeconds.toString())
    }

    private fun StringBuilder.appendField(value: String) {
        append(value.length).append(':').append(value)
    }

    companion object {
        const val MIN_SESSION_TTL_SECONDS = 60L
        const val MAX_SESSION_TTL_SECONDS = 30L * 24L * 60L * 60L
    }
}

@Serializable
data class PluginAuthenticationCompletionTarget(
    val host: String,
    val pathPrefix: String,
) {
    init {
        require(isNormalizedHttpsHost(host))
        requireNormalizedPathPrefix(pathPrefix)
    }
}

@Serializable
data class PluginAuthenticationCredentialTarget(
    val host: String,
    val pathPrefix: String,
    val cookieNames: Set<String>,
) {
    init {
        require(isNormalizedHttpsHost(host))
        requireNormalizedPathPrefix(pathPrefix)
        require(cookieNames.isNotEmpty())
        require(cookieNames.all(COOKIE_NAME_PATTERN::matches))
    }

    private companion object {
        val COOKIE_NAME_PATTERN = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
    }
}

private fun requireHttpsAuthenticationUrl(value: String, allowQuery: Boolean): URI {
    val uri = runCatching { URI(value) }.getOrNull()
        ?: throw IllegalArgumentException("Authentication URL is invalid")
    require(uri.scheme == "https" && isNormalizedHttpsHost(uri.host.orEmpty()))
    require(uri.userInfo == null && uri.port == -1 && uri.fragment == null)
    require(allowQuery || uri.query == null)
    requireNormalizedPathPrefix(uri.path.ifBlank { "/" })
    return uri
}

private fun normalizedAuthenticationUrl(value: String, allowQuery: Boolean): String {
    val uri = requireHttpsAuthenticationUrl(value, allowQuery)
    return URI("https", null, uri.host, -1, uri.path.ifBlank { "/" }, uri.query, null).toASCIIString()
}
