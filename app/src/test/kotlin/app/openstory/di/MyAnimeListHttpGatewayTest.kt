package app.openstory.di

import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.RequestBudget
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MyAnimeListHttpGatewayTest {
    @Test
    fun clientIdHeaderIsInjectedWithoutOverwritingPluginHeaders() = runBlocking {
        val delegate = RecordingPluginHttpGateway()
        val gateway = MyAnimeListHttpGateway(
            clientId = "hikari-client",
            delegate = delegate,
        )

        val result = gateway.execute(
            request = PluginHttpRequest(
                url = "https://api.myanimelist.net/v2/manga/13",
                headers = mapOf("Accept" to "application/json"),
            ),
            budget = RequestBudget(),
        )

        assertIs<AppResult.Success<PluginHttpResponse>>(result)
        assertEquals(
            mapOf(
                "Accept" to "application/json",
                "X-MAL-CLIENT-ID" to "hikari-client",
            ),
            delegate.requests.single().headers,
        )
    }


    @Test
    fun invalidClientIdFailsBeforeNetworkDispatch() = runBlocking {
        val delegate = RecordingPluginHttpGateway()
        val gateway = MyAnimeListHttpGateway(
            clientId = "bad\nclient",
            delegate = delegate,
        )

        val result = gateway.execute(
            request = PluginHttpRequest("https://api.myanimelist.net/v2/manga/13"),
            budget = RequestBudget(),
        )

        val failure = assertIs<AppResult.Failure>(result)
        assertEquals("plugin.myanimelist_client_id_invalid", failure.error.code)
        assertEquals(emptyList(), delegate.requests)
    }

    @Test
    fun missingClientIdFailsBeforeNetworkDispatch() = runBlocking {
        val delegate = RecordingPluginHttpGateway()
        val gateway = MyAnimeListHttpGateway(
            clientId = "   ",
            delegate = delegate,
        )

        val result = gateway.execute(
            request = PluginHttpRequest("https://api.myanimelist.net/v2/manga/13"),
            budget = RequestBudget(),
        )

        val failure = assertIs<AppResult.Failure>(result)
        assertEquals("plugin.myanimelist_client_id_missing", failure.error.code)
        assertEquals(emptyList(), delegate.requests)
    }
}

private class RecordingPluginHttpGateway : PluginHttpGateway {
    val requests = mutableListOf<PluginHttpRequest>()

    override suspend fun execute(
        request: PluginHttpRequest,
        budget: RequestBudget,
    ): AppResult<PluginHttpResponse> {
        requests += request
        return AppResult.Success(
            PluginHttpResponse(
                status = 200,
                headers = emptyMap(),
                body = "{}".encodeToByteArray(),
                decodedText = "{}",
            ),
        )
    }
}
