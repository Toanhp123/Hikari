package app.openstory.network

import app.openstory.common.AppError
import app.openstory.common.AppResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ValidatedPluginUrl(
    val value: String,
    val host: String,
)

class PluginUrlPolicy private constructor(
    allowedHosts: Set<String>,
    private val baseUrl: String?,
    private val allowCleartextForTesting: Boolean,
) {
    constructor(
        allowedHosts: Set<String>,
        baseUrl: String? = null,
    ) : this(
        allowedHosts = allowedHosts,
        baseUrl = baseUrl,
        allowCleartextForTesting = false,
    )

    private val normalizedAllowedHosts = allowedHosts.map(String::lowercase).toSet()

    fun resolve(
        candidate: String,
        documentBaseUrl: String? = null,
    ): AppResult<ValidatedPluginUrl> {
        val url = candidate.toHttpUrlOrNull()
            ?: documentBaseUrl?.toHttpUrlOrNull()?.resolve(candidate)
            ?: baseUrl?.toHttpUrlOrNull()?.resolve(candidate)
            ?: return pluginUrlFailure("plugin.invalid_url")

        return when {
            url.username.isNotEmpty() || url.password.isNotEmpty() ->
                pluginUrlFailure("plugin.invalid_url")

            !url.isHttps && !allowCleartextForTesting ->
                pluginUrlFailure("plugin.https_required")

            url.host.lowercase() !in normalizedAllowedHosts ->
                pluginUrlFailure("plugin.domain_denied")

            else -> AppResult.Success(
                ValidatedPluginUrl(
                    value = url.toString(),
                    host = url.host.lowercase(),
                ),
            )
        }
    }

    internal companion object {
        fun forTesting(
            allowedHosts: Set<String>,
            baseUrl: String? = null,
        ): PluginUrlPolicy =
            PluginUrlPolicy(
                allowedHosts = allowedHosts,
                baseUrl = baseUrl,
                allowCleartextForTesting = true,
            )
    }
}

private fun pluginUrlFailure(code: String): AppResult.Failure =
    AppResult.Failure(
        AppError.Network(
            code = code,
            retryable = false,
        ),
    )
