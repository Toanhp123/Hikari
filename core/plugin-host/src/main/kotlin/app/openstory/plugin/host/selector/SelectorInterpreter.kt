package app.openstory.plugin.host.selector

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.selector.HttpGet
import app.openstory.plugin.api.selector.NormalizeWhitespace
import app.openstory.plugin.api.selector.RemoveElements
import app.openstory.plugin.api.selector.SelectAll
import app.openstory.plugin.api.selector.SelectAttribute
import app.openstory.plugin.api.selector.SelectText
import app.openstory.plugin.api.selector.SelectorOperation
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

data class SelectorLimits(
    val maxOperations: Int = 64,
    val maxDocumentCharacters: Int = 2_000_000,
    val maxDocumentNodes: Int = 50_000,
    val maxWallClockMillis: Long = 10_000,
    val maxElements: Int = 10_000,
    val maxTextValues: Int = 10_000,
    val maxRegexInputCharacters: Long = 1_000_000,
    val requestBudget: RequestBudget =
        RequestBudget(),
) {
    init {
        require(maxOperations > 0) {
            "Maximum operation count must be positive."
        }

        require(maxDocumentCharacters > 0) {
            "Maximum document size must be positive."
        }

        require(maxDocumentNodes > 0) {
            "Maximum document node count must be positive."
        }

        require(maxWallClockMillis > 0) {
            "Maximum wall-clock duration must be positive."
        }

        require(maxElements > 0) {
            "Maximum element count must be positive."
        }

        require(maxTextValues > 0) {
            "Maximum text value count must be positive."
        }

        require(maxRegexInputCharacters > 0) {
            "Maximum regex input character count must be positive."
        }
    }
}

sealed interface SelectorValue {
    data object None : SelectorValue

    data class Document(
        val value: HtmlDocument,
    ) : SelectorValue

    data class Elements(
        val values: List<HtmlElement>,
    ) : SelectorValue

    data class Text(
        val values: List<String>,
    ) : SelectorValue
}

class SelectorInterpreter(
    private val http: PluginHttpGateway,
    private val parser: HtmlDocumentAdapter,
    private val transforms: TransformRegistry,
    private val limits: SelectorLimits,
) {
    suspend fun execute(
        operations: List<SelectorOperation>,
        input: Map<String, String>,
    ): AppResult<SelectorValue> {
        val result =
            withTimeoutOrNull(
                timeMillis =
                    limits.maxWallClockMillis,
            ) {
                executeWithinWallClockBudget(
                    operations = operations,
                    input = input,
                )
            }

        return result
            ?: pluginFailure(
                code =
                    "plugin.selector_timeout",
            )
    }
    private suspend fun executeWithinWallClockBudget(
        operations: List<SelectorOperation>,
        input: Map<String, String>,
    ): AppResult<SelectorValue> {
        if (operations.size > limits.maxOperations) {
            return pluginFailure(
                code =
                    "plugin.selector_operation_limit",
            )
        }

        var current: SelectorValue =
            SelectorValue.None

        operations.forEachIndexed {
                operationIndex,
                operation,
            ->

            val result =
                try {
                    executeOperation(
                        operation = operation,
                        current = current,
                        input = input,
                    )
                } catch (
                    cancellation:
                        CancellationException,
                ) {
                    throw cancellation
                } catch (
                    exception: Exception,
                ) {
                    pluginFailure(
                        code =
                            "plugin.selector_execution_failed",
                    )
                }

            when (result) {
                is AppResult.Success ->
                    current = result.value

                is AppResult.Failure ->
                    return result.withOperationIndex(
                        operationIndex,
                    )
            }
        }

        return AppResult.Success(current)
    }

    private suspend fun executeOperation(
        operation: SelectorOperation,
        current: SelectorValue,
        input: Map<String, String>,
    ): AppResult<SelectorValue> =
        when (operation) {
            is HttpGet ->
                executeHttpGet(
                    operation = operation,
                    current = current,
                    input = input,
                )

            is RemoveElements ->
                removeElements(
                    operation = operation,
                    current = current,
                )

            is SelectAll ->
                selectAll(
                    operation = operation,
                    current = current,
                )

            is SelectText ->
                selectText(
                    operation = operation,
                    current = current,
                )

            is SelectAttribute ->
                selectAttribute(
                    operation = operation,
                    current = current,
                )

            is NormalizeWhitespace ->
                normalizeWhitespace(
                    operation = operation,
                    current = current,
                )
        }

    private suspend fun executeHttpGet(
        operation: HttpGet,
        current: SelectorValue,
        input: Map<String, String>,
    ): AppResult<SelectorValue> {
        if (current !is SelectorValue.None) {
            return typeMismatch()
        }

        val renderedUrl =
            renderUrlTemplate(
                template = operation.urlTemplate,
                input = input,
            ) ?: return pluginFailure(
                code =
                    "plugin.selector_missing_input",
            )

        return when (
            val response =
                http.execute(
                    request =
                        PluginHttpRequest(
                            url = renderedUrl,
                        ),
                    budget =
                        limits.requestBudget,
                )
        ) {
            is AppResult.Failure ->
                response

            is AppResult.Success -> {
                val httpResponse =
                    response.value

                if (
                    httpResponse.status !in
                        200..299
                ) {
                    return pluginFailure(
                        code =
                            "plugin.selector_http_status",
                    )
                }

                val text =
                    httpResponse.decodedText
                        ?: httpResponse.body
                            .decodeToString()

                if (
                    text.length >
                    limits.maxDocumentCharacters
                ) {
                    return pluginFailure(
                        code =
                            "plugin.selector_document_limit",
                    )
                }

                val document =
                    parser.parse(
                        html = text,
                        baseUri = renderedUrl,
                    )

                if (
                    parser.nodeCount(document) >
                    limits.maxDocumentNodes
                ) {
                    return pluginFailure(
                        code =
                            "plugin.selector_node_limit",
                    )
                }

                AppResult.Success(
                    SelectorValue.Document(
                        value = document,
                    ),
                )
            }
        }
    }

    private fun removeElements(
        operation: RemoveElements,
        current: SelectorValue,
    ): AppResult<SelectorValue> {
        val document =
            (current as? SelectorValue.Document)
                ?: return typeMismatch()

        return AppResult.Success(
            SelectorValue.Document(
                value =
                    parser.removeElements(
                        document =
                            document.value,
                        css = operation.css,
                    ),
            ),
        )
    }

    private fun selectAll(
        operation: SelectAll,
        current: SelectorValue,
    ): AppResult<SelectorValue> {
        val document =
            (current as? SelectorValue.Document)
                ?: return typeMismatch()

        val elements =
            parser.selectAll(
                document = document.value,
                css = operation.css,
            )

        if (
            elements.size >
            limits.maxElements
        ) {
            return pluginFailure(
                code =
                    "plugin.selector_element_limit",
            )
        }

        return AppResult.Success(
            SelectorValue.Elements(elements),
        )
    }

    private fun selectText(
        operation: SelectText,
        current: SelectorValue,
    ): AppResult<SelectorValue> {
        val elements =
            (current as? SelectorValue.Elements)
                ?: return typeMismatch()

        val values =
            parser.selectText(
                elements = elements.values,
                css = operation.css,
            )

        return boundedText(values)
    }

    private fun selectAttribute(
        operation: SelectAttribute,
        current: SelectorValue,
    ): AppResult<SelectorValue> {
        val elements =
            (current as? SelectorValue.Elements)
                ?: return typeMismatch()

        val attributes =
            parser.selectAttribute(
                elements = elements.values,
                css = operation.css,
                attribute = operation.attribute,
            )

        if (
            attributes.any { attribute ->
                !attribute.present
            }
        ) {
            return fieldMissing(
                fieldPath =
                    "attribute." +
                        operation.attribute
                            .toDiagnosticToken(),
            )
        }

        return boundedText(
            attributes.map(
                HtmlAttributeValue::value,
            ),
        )
    }

    private fun normalizeWhitespace(
        operation: NormalizeWhitespace,
        current: SelectorValue,
    ): AppResult<SelectorValue> {
        val text =
            (current as? SelectorValue.Text)
                ?: return typeMismatch()

        if (
            operation.enabled &&
            text.values.totalCharacterCount() >
            limits.maxRegexInputCharacters
        ) {
            return pluginFailure(
                code =
                    "plugin.selector_regex_input_limit",
            )
        }

        return AppResult.Success(
            SelectorValue.Text(
                values =
                    transforms.normalizeWhitespace(
                        values = text.values,
                        enabled = operation.enabled,
                    ),
            ),
        )
    }

    private fun boundedText(
        values: List<String>,
    ): AppResult<SelectorValue> {
        if (
            values.size >
            limits.maxTextValues
        ) {
            return pluginFailure(
                code =
                    "plugin.selector_text_limit",
            )
        }

        return AppResult.Success(
            SelectorValue.Text(values),
        )
    }

    private fun renderUrlTemplate(
        template: String,
        input: Map<String, String>,
    ): String? {
        var missingInput = false

        val rendered =
            TEMPLATE_VARIABLE.replace(
                input = template,
            ) { match ->
                val key =
                    match.groupValues[1]

                val value =
                    input[key]

                if (value == null) {
                    missingInput = true
                    ""
                } else {
                    encodeUrlValue(value)
                }
            }

        return if (missingInput) {
            null
        } else {
            rendered
        }
    }

    private fun encodeUrlValue(
        value: String,
    ): String =
        URLEncoder
            .encode(
                value,
                StandardCharsets.UTF_8.name(),
            )
            .replace(
                oldValue = "+",
                newValue = "%20",
            )

    private fun typeMismatch():
        AppResult.Failure =
        pluginFailure(
            code =
                "plugin.selector_type_mismatch",
        )

    private fun pluginFailure(
        code: String,
    ): AppResult.Failure =
        AppResult.Failure(
            AppError.Plugin(
                code = code,
                retryable = false,
            ),
        )

    private companion object {
        val TEMPLATE_VARIABLE =
            Regex(
                """\{([A-Za-z][A-Za-z0-9_]*)}""",
            )
    }
}
private fun List<String>.totalCharacterCount():
    Long =
    fold(0L) { total, value ->
        val valueLength =
            value.length.toLong()

        if (
            total >
            Long.MAX_VALUE - valueLength
        ) {
            Long.MAX_VALUE
        } else {
            total + valueLength
        }
    }
private fun fieldMissing(
    fieldPath: String,
): AppResult.Failure =
    AppResult.Failure(
        error =
            AppError.Plugin(
                code =
                    "plugin.selector_field_missing",
                retryable = false,
                diagnostic =
                    AppError.Diagnostic.of(
                        "field_path" to fieldPath,
                    ),
            ),
    )

private fun String.toDiagnosticToken():
    String =
    map { character ->
        when {
            character.isLetterOrDigit() ->
                character

            character == '.' ||
                character == '_' ||
                character == '-' ->
                character

            else ->
                '_'
        }
    }
        .joinToString(
            separator = "",
        )
        .ifBlank {
            "unknown"
        }
private fun AppResult.Failure.withOperationIndex(
    operationIndex: Int,
): AppResult.Failure {
    val pluginError =
        error as? AppError.Plugin
            ?: return this

    if (
        !pluginError.code.startsWith(
            prefix = "plugin.selector_",
        )
    ) {
        return this
    }

    return AppResult.Failure(
        error =
            pluginError.copy(
                diagnostic =
                    pluginError.diagnostic.with(
                        "operation_index" to
                            operationIndex.toString(),
                    ),
            ),
    )
}
