package app.openstory.plugin.host.selector

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.plugin.api.selector.HttpGet
import app.openstory.plugin.api.selector.RemoveElements
import app.openstory.plugin.api.selector.SelectorRequestOperation
import app.openstory.plugin.api.selector.SelectorRequestPlan
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.withTimeoutOrNull

class SelectorDocumentLoader(
    private val http: PluginHttpGateway,
    private val parser: HtmlDocumentAdapter,
    private val limits: SelectorLimits,
) {
    suspend fun load(
        request: SelectorRequestPlan,
        input: Map<String, String>,
        context: SelectorExecutionContext,
    ): AppResult<HtmlDocument> =
        withTimeoutOrNull(limits.maxWallClockMillis) {
            loadWithinBudget(request, input, context)
        } ?: pluginFailure("plugin.selector_timeout")

    private suspend fun loadWithinBudget(
        request: SelectorRequestPlan,
        input: Map<String, String>,
        context: SelectorExecutionContext,
    ): AppResult<HtmlDocument> {
        var document: HtmlDocument? = null
        var failure: AppResult.Failure? =
            if (request.operations.size > limits.maxOperations) {
                pluginFailure("plugin.selector_operation_limit")
            } else {
                null
            }

        request.operations.forEachIndexed { operationIndex, operation ->
            if (failure != null) {
                return@forEachIndexed
            }
            val result = executeOperation(operation, document, input, context)

            when (result) {
                is AppResult.Success -> document = result.value
                is AppResult.Failure ->
                    failure = result.withOperationIndex(operationIndex)
            }
        }

        return failure
            ?: document?.let { value -> AppResult.Success(value) }
            ?: pluginFailure("plugin.selector_type_mismatch")
    }

    private suspend fun executeOperation(
        operation: SelectorRequestOperation,
        document: HtmlDocument?,
        input: Map<String, String>,
        context: SelectorExecutionContext,
    ): AppResult<HtmlDocument> =
        when (operation) {
            is HttpGet -> loadHttpDocument(operation, input, context)
            is RemoveElements -> {
                val current =
                    document ?: return pluginFailure("plugin.selector_type_mismatch")
                AppResult.Success(
                    parser.removeElements(current, operation.css),
                )
            }
        }

    private suspend fun loadHttpDocument(
        operation: HttpGet,
        input: Map<String, String>,
        context: SelectorExecutionContext,
    ): AppResult<HtmlDocument> {
        val renderedUrl = prepareUrl(operation, input, context)
        return when (renderedUrl) {
            is AppResult.Failure -> renderedUrl
            is AppResult.Success -> loadResponse(renderedUrl.value)
        }
    }

    private fun prepareUrl(
        operation: HttpGet,
        input: Map<String, String>,
        context: SelectorExecutionContext,
    ): AppResult<String> {
        val resolved = context.resolveUrlTemplate(operation.urlTemplate)
        return if (resolved == null) {
            originRequired()
        } else {
            val rendered = renderUrlTemplate(resolved, input)
            if (rendered == null) {
                pluginFailure("plugin.selector_missing_input")
            } else {
                AppResult.Success(rendered)
            }
        }
    }

    private suspend fun loadResponse(
        renderedUrl: String,
    ): AppResult<HtmlDocument> =
        when (
            val response = http.execute(
                request = PluginHttpRequest(url = renderedUrl),
                budget = limits.requestBudget,
            )
        ) {
            is AppResult.Failure -> response
            is AppResult.Success -> parseResponse(response.value, renderedUrl)
        }

    private fun parseResponse(
        response: app.openstory.network.PluginHttpResponse,
        renderedUrl: String,
    ): AppResult<HtmlDocument> {
        return if (response.status !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
            pluginFailure("plugin.selector_http_status")
        } else {
            val text = response.decodedText ?: response.body.decodeToString()
            if (text.length > limits.maxDocumentCharacters) {
                pluginFailure("plugin.selector_document_limit")
            } else {
                val document = parser.parse(text, renderedUrl)
                if (parser.nodeCount(document) > limits.maxDocumentNodes) {
                    pluginFailure("plugin.selector_node_limit")
                } else {
                    AppResult.Success(document)
                }
            }
        }
    }

    private fun renderUrlTemplate(
        template: String,
        input: Map<String, String>,
    ): String? {
        var missingInput = false
        val rendered = TEMPLATE_VARIABLE.replace(template) { match ->
            input[match.groupValues[1]]?.let(::encodeUrlValue)
                ?: run {
                    missingInput = true
                    ""
                }
        }
        return rendered.takeUnless { missingInput }
    }

    private fun encodeUrlValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")

    private companion object {
        val TEMPLATE_VARIABLE = Regex("""\{([A-Za-z][A-Za-z0-9_]*)}""")
        const val HTTP_SUCCESS_MIN = 200
        const val HTTP_SUCCESS_MAX = 299
    }
}

private fun originRequired(): AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code = "plugin.selector_origin_required",
            retryable = false,
            diagnostic = AppError.Diagnostic.of(
                "field_path" to "execution_context.origin",
            ),
        ),
    )

private fun pluginFailure(code: String): AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code = code,
            retryable = false,
        ),
    )

private fun AppResult.Failure.withOperationIndex(
    operationIndex: Int,
): AppResult.Failure {
    val pluginError = error as? AppError.Plugin
    return if (pluginError?.code?.startsWith("plugin.selector_") == true) {
        AppResult.Failure(
            pluginError.copy(
                diagnostic = pluginError.diagnostic.with(
                    "operation_index" to operationIndex.toString(),
                ),
            ),
        )
    } else {
        this
    }
}
