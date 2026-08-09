package app.openstory.di

import android.content.Context
import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.RequestBudget
import java.net.URI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class BundledCatalogFixtureGateway(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : PluginHttpGateway {
    private val assets = context.applicationContext.assets

    override suspend fun execute(
        request: PluginHttpRequest,
        budget: RequestBudget,
    ): AppResult<PluginHttpResponse> = withContext(ioDispatcher) {
        val fixturePath = fixturePath(request.url)
            ?: return@withContext AppResult.Failure(
                AppError.Network(
                    code = "network.fixture_route_not_found",
                    retryable = false,
                ),
            )
        val html = assets.open(fixturePath).bufferedReader().use { reader -> reader.readText() }
        val body = html.encodeToByteArray()
        if (body.size.toLong() > budget.maxDecompressedBytes) {
            return@withContext AppResult.Failure(
                AppError.Network(
                    code = "network.response_decompressed_too_large",
                    retryable = false,
                ),
            )
        }
        if (html.length > budget.maxDecodedCharacters) {
            return@withContext AppResult.Failure(
                AppError.Network(
                    code = "network.response_decoded_text_too_large",
                    retryable = false,
                ),
            )
        }

        AppResult.Success(
            PluginHttpResponse(
                status = 200,
                headers = emptyMap(),
                body = body,
                decodedText = html,
            ),
        )
    }

    private fun fixturePath(url: String): String? = runCatching {
        val uri = URI(url)
        val path = uri.path
        when (uri.host) {
            SELECTOR_HOST -> when {
                path == "/home" -> "$SELECTOR_FIXTURE_ROOT/home.html"
                path == "/search" -> "$SELECTOR_FIXTURE_ROOT/search.html"
                path.startsWith("/story/") -> "$SELECTOR_FIXTURE_ROOT/details.html"
                else -> null
            }
            JAVASCRIPT_HOST -> when {
                path == "/home" -> "$JAVASCRIPT_FIXTURE_ROOT/home.json"
                path == "/search" -> "$JAVASCRIPT_FIXTURE_ROOT/search.json"
                path.startsWith("/story/") -> "$JAVASCRIPT_FIXTURE_ROOT/details.json"
                else -> null
            }
            else -> null
        }
    }.getOrNull()

    private companion object {
        const val SELECTOR_HOST = "catalog.openstory.example"
        const val JAVASCRIPT_HOST = "javascript.openstory.example"
        const val SELECTOR_FIXTURE_ROOT = "plugins/default-catalog-fixtures"
        const val JAVASCRIPT_FIXTURE_ROOT = "plugins/javascript-catalog-fixtures"
    }
}
