package app.openstory.plugins.runtime.auth

import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialProvider
import app.openstory.plugins.runtime.capabilities.http.ManagedCredentialRequest

class PluginSessionManagedCredentialProvider(
    private val sessions: PluginSessionService,
) : ManagedCredentialProvider {
    override suspend fun headers(request: ManagedCredentialRequest): Map<String, String> {
        val records = sessions.sessionFor(request)
        if (records.isEmpty()) return emptyMap()
        val cookie = records.sortedBy(PluginSessionRecord::cookieName)
            .joinToString("; ") { record -> "${record.cookieName}=${record.cookieValue.raw}" }
        return mapOf("Cookie" to cookie)
    }
}
