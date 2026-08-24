package app.openstory.plugins.runtime.capabilities.http

import kotlinx.coroutines.CancellationException

class CompositeManagedCredentialProvider(
    private val providers: List<ManagedCredentialProvider>,
) : ManagedCredentialProvider {
    override suspend fun headers(request: ManagedCredentialRequest): Map<String, String> {
        val merged = linkedMapOf<String, String>()
        val owners = mutableSetOf<String>()
        providers.forEach { provider ->
            val provided = try {
                provider.headers(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw HttpCapabilityFailure("plugin.http_credentials_failed", retryable = true)
            }
            provided.forEach { (name, value) ->
                val ownershipKey = name.lowercase()
                if (!owners.add(ownershipKey)) {
                    throw HttpCapabilityFailure("plugin.http_managed_header_collision")
                }
                merged[name] = value
            }
        }
        return merged
    }
}
