package app.openstory.di

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.RequestBudget

internal class MyAnimeListHttpGateway(
    clientId: String,
    private val delegate: PluginHttpGateway,
) : PluginHttpGateway {
    private val normalizedClientId = clientId.trim()

    override suspend fun execute(
        request: PluginHttpRequest,
        budget: RequestBudget,
    ): AppResult<PluginHttpResponse> = when {
        normalizedClientId.isEmpty() -> clientIdFailure(MISSING_CLIENT_ID)
        !normalizedClientId.matches(SAFE_CLIENT_ID) -> clientIdFailure(INVALID_CLIENT_ID)
        else -> delegate.execute(
            request = request.copy(
                headers = request.headers + (CLIENT_ID_HEADER to normalizedClientId),
            ),
            budget = budget,
        )
    }

    private fun clientIdFailure(code: String): AppResult.Failure = AppResult.Failure(
        AppError.Plugin(
            code = code,
            retryable = false,
        ),
    )

    private companion object {
        const val CLIENT_ID_HEADER = "X-MAL-CLIENT-ID"
        const val MISSING_CLIENT_ID = "plugin.myanimelist_client_id_missing"
        const val INVALID_CLIENT_ID = "plugin.myanimelist_client_id_invalid"
        val SAFE_CLIENT_ID = Regex("[A-Za-z0-9._-]{1,256}")
    }
}
