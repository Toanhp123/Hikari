package app.openstory.network

import app.openstory.common.AppResult

data class PluginHttpRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val responseCharset: String? = null,
)

data class PluginHttpResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
    val decodedText: String? = null,
)

interface PluginHttpGateway {
    suspend fun execute(
        request: PluginHttpRequest,
        budget: RequestBudget,
    ): AppResult<PluginHttpResponse>
}
