package app.openstory.plugins.runtime.capabilities.html

import app.openstory.plugins.runtime.PluginCallResult
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
data class HtmlQueryRequest(
    val body: String,
    val selector: String,
    val attribute: String? = null,
    val limit: Int = 50,
)

@Serializable
data class HtmlQueryResponse(val values: List<String>)

class HtmlCapability(
    private val maxDocumentChars: Int = 2_000_000,
    private val maxResults: Int = 200,
    private val maxResultChars: Int = 16_384,
) {
    fun query(request: HtmlQueryRequest): PluginCallResult<HtmlQueryResponse> {
        return when {
            request.body.length > maxDocumentChars -> failure("plugin.html_document_too_large")
            request.selector.isBlank() || request.selector.length > MAX_SELECTOR_LENGTH ->
                failure("plugin.html_selector_invalid")
            request.limit !in 1..maxResults -> failure("plugin.html_result_budget_invalid")
            else -> runCatching {
                val values = Jsoup.parse(request.body).select(request.selector).take(request.limit).map { element ->
                    val value = request.attribute?.let(element::attr) ?: element.text()
                    if (value.length > maxResultChars) value.take(maxResultChars) else value
                }
                PluginCallResult.Success(HtmlQueryResponse(values))
            }.getOrElse {
                failure("plugin.html_query_invalid")
            }
        }
    }

    private fun failure(code: String) = PluginCallResult.Failure(code, retryable = false)

    private companion object {
        const val MAX_SELECTOR_LENGTH = 2048
    }
}
