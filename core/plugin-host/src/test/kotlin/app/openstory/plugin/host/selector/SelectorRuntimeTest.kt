package app.openstory.plugin.host.selector

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.selector.HttpGet
import app.openstory.plugin.api.selector.NormalizeWhitespace
import app.openstory.plugin.api.selector.SelectAll
import app.openstory.plugin.api.selector.SelectAttribute
import app.openstory.plugin.api.selector.SelectText
import app.openstory.plugin.api.selector.SelectorPluginDefinition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SelectorRuntimeTest {

    @Test
    fun runtimeExecutesDeclaredPipelineInOrder() = runTest {
        val gateway = RecordingGateway(
            html = """
                <html>
                    <body>
                        <article>
                            <a href="/n/1">  Novel  </a>
                        </article>
                    </body>
                </html>
            """.trimIndent(),
        )

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(
                    maxOperations = 8,
                ),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate =
                        "https://allowed.example/search?q={query}",
                ),
                SelectAll(css = "article"),
                SelectText(css = "a"),
                NormalizeWhitespace(),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = mapOf("query" to "Novel"),
        )

        val success =
            assertIs<AppResult.Success<*>>(result)

        val text =
            assertIs<SelectorValue.Text>(success.value)

        assertEquals(
            listOf("Novel"),
            text.values,
        )

        assertEquals(
            listOf(
                "https://allowed.example/search?q=Novel",
            ),
            gateway.requests.map { it.url },
        )
    }

    @Test
    fun runtimeRejectsPipelineBeyondOperationLimit() = runTest {
        val gateway = RecordingGateway(
            html = "<article>Novel</article>",
        )

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(
                    maxOperations = 1,
                ),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate = "https://allowed.example/",
                ),
                SelectAll(css = "article"),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = emptyMap(),
        )

        val failure =
            assertIs<AppResult.Failure>(result)

        assertEquals(
            "plugin.selector_operation_limit",
            failure.error.code,
        )

        assertEquals(
            emptyList(),
            gateway.requests,
        )
    }
    @Test
    fun runtimeRejectsDocumentBeyondCharacterLimit() = runTest {
        val gateway = RecordingGateway(
            html = "<article>Document exceeds limit</article>",
        )

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(
                    maxOperations = 4,
                    maxDocumentCharacters = 10,
                ),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate = "https://allowed.example/",
                ),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = emptyMap(),
        )

        val failure =
            assertIs<AppResult.Failure>(result)

        assertEquals(
            "plugin.selector_document_limit",
            failure.error.code,
        )

        assertEquals(
            1,
            gateway.requests.size,
        )
    }
    @Test
    fun runtimeReturnsTypedFailureForInvalidOperationOrder() = runTest {
        val gateway = RecordingGateway(
            html = "<article>Novel</article>",
        )

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                SelectAll(css = "article"),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = emptyMap(),
        )

        val failure =
            assertIs<AppResult.Failure>(result)

        assertEquals(
            "plugin.selector_type_mismatch",
            failure.error.code,
        )

        assertEquals(
            AppError.Diagnostic.of(
                "operation_index" to "0",
            ),
            failure.error.diagnostic,
        )

        assertEquals(
            emptyList(),
            gateway.requests,
        )
    }
    @Test
    fun cancellationPropagatesAndReleasesGatewayResources() = runTest {
        val gateway = CancellationAwareGateway()

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate = "https://allowed.example/",
                ),
            ),
        )

        val operation = launch {
            runtime.execute(
                definition = definition,
                input = emptyMap(),
            )
        }

        gateway.started.await()
        operation.cancelAndJoin()

        assertTrue(operation.isCancelled)
        assertTrue(gateway.released)
    }

    @Test
    fun runtimeReturnsTypedFailureWhenWallClockBudgetExpires() = runTest {
        val gateway = CancellationAwareGateway()

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(
                    maxWallClockMillis = 100,
                ),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate = "https://allowed.example/",
                ),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = emptyMap(),
        )

        val failure =
            assertIs<AppResult.Failure>(result)

        assertEquals(
            "plugin.selector_timeout",
            failure.error.code,
        )

        assertTrue(gateway.released)
    }
    @Test
    fun diagnosticReportsSecondOperationIndex() = runTest {
        val gateway = RecordingGateway(
            html = "<article>Novel</article>",
        )

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate = "https://allowed.example/",
                ),
                NormalizeWhitespace(),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = emptyMap(),
        )

        val failure =
            assertIs<AppResult.Failure>(result)

        assertEquals(
            "plugin.selector_type_mismatch",
            failure.error.code,
        )

        assertEquals(
            AppError.Diagnostic.of(
                "operation_index" to "1",
            ),
            failure.error.diagnostic,
        )

        assertEquals(
            1,
            gateway.requests.size,
        )
    }
    @Test
    fun gatewayExceptionReturnsIndexedTypedFailure() = runTest {
        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = ThrowingGateway(),
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate = "https://allowed.example/",
                ),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = emptyMap(),
        )

        val failure =
            assertIs<AppResult.Failure>(result)

        assertEquals(
            "plugin.selector_execution_failed",
            failure.error.code,
        )

        assertEquals(
            AppError.Diagnostic.of(
                "operation_index" to "0",
            ),
            failure.error.diagnostic,
        )
    }

    private class ThrowingGateway :
        PluginHttpGateway {

        override suspend fun execute(
            request: PluginHttpRequest,
            budget: RequestBudget,
        ): AppResult<PluginHttpResponse> {
            throw IllegalStateException(
                "Synthetic gateway failure",
            )
        }
    }
    @Test
    fun sourceRelativeUrlAttributesAreNormalized() = runTest {
        val gateway = RecordingGateway(
            html = """
                <html>
                    <body>
                        <article>
                            <a href="/novels/1">One</a>
                        </article>
                        <article>
                            <a href="https://allowed.example/novels/2">
                                Two
                            </a>
                        </article>
                    </body>
                </html>
            """.trimIndent(),
        )

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate =
                        "https://allowed.example/catalog/page",
                ),
                SelectAll(
                    css = "article",
                ),
                SelectAttribute(
                    css = "a",
                    attribute = "href",
                ),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = emptyMap(),
        )

        val success =
            assertIs<AppResult.Success<*>>(result)

        val text =
            assertIs<SelectorValue.Text>(
                success.value,
            )

        assertEquals(
            listOf(
                "https://allowed.example/novels/1",
                "https://allowed.example/novels/2",
            ),
            text.values,
        )
    }
    @Test
    fun nonUrlAttributesRemainUnchanged() = runTest {
        val gateway = RecordingGateway(
            html = """
                <html>
                    <body>
                        <article data-id="/novels/1">
                            Novel
                        </article>
                    </body>
                </html>
            """.trimIndent(),
        )

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate =
                        "https://allowed.example/catalog/page",
                ),
                SelectAll(
                    css = "body",
                ),
                SelectAttribute(
                    css = "article",
                    attribute = "data-id",
                ),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = emptyMap(),
        )

        val success =
            assertIs<AppResult.Success<*>>(result)

        val text =
            assertIs<SelectorValue.Text>(
                success.value,
            )

        assertEquals(
            listOf("/novels/1"),
            text.values,
        )
    }
    @Test
    fun missingAttributeReturnsTypedFieldFailure() = runTest {
        val gateway = RecordingGateway(
            html = """
                <html>
                    <body>
                        <article>
                            <a>Novel without link</a>
                        </article>
                    </body>
                </html>
            """.trimIndent(),
        )

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate =
                        "https://allowed.example/catalog",
                ),
                SelectAll(
                    css = "article",
                ),
                SelectAttribute(
                    css = "a",
                    attribute = "href",
                ),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = emptyMap(),
        )

        val failure =
            assertIs<AppResult.Failure>(result)

        assertEquals(
            "plugin.selector_field_missing",
            failure.error.code,
        )

        assertEquals(
            AppError.Diagnostic.of(
                "operation_index" to "2",
                "field_path" to "attribute.href",
            ),
            failure.error.diagnostic,
        )
    }
    @Test
    fun normalizationRejectsTextBeyondRegexBudget() = runTest {
        val gateway = RecordingGateway(
            html = """
                <html>
                    <body>
                        <article>
                            <a>Text longer than budget</a>
                        </article>
                    </body>
                </html>
            """.trimIndent(),
        )

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(
                    maxRegexInputCharacters = 8,
                ),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate =
                        "https://allowed.example/catalog",
                ),
                SelectAll(
                    css = "article",
                ),
                SelectText(
                    css = "a",
                ),
                NormalizeWhitespace(),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = emptyMap(),
        )

        val failure =
            assertIs<AppResult.Failure>(result)

        assertEquals(
            "plugin.selector_regex_input_limit",
            failure.error.code,
        )

        assertEquals(
            AppError.Diagnostic.of(
                "operation_index" to "3",
            ),
            failure.error.diagnostic,
        )
    }
    @Test
    fun relativeHttpGetResolvesAgainstExecutionOrigin() = runTest {
        val gateway = RecordingGateway(
            html = "<article>Novel</article>",
        )

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate =
                        "/search?q={query}",
                ),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = mapOf(
                "query" to "Novel",
            ),
            context =
                SelectorExecutionContext(
                    origin =
                        "https://allowed.example/catalog/",
                ),
        )

        assertIs<AppResult.Success<*>>(result)

        assertEquals(
            listOf(
                "https://allowed.example/search?q=Novel",
            ),
            gateway.requests.map(
                PluginHttpRequest::url,
            ),
        )
    }
    @Test
    fun relativeHttpGetWithoutOriginReturnsTypedFailure() = runTest {
        val gateway = RecordingGateway(
            html = "<article>Novel</article>",
        )

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate =
                        "/search?q={query}",
                ),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = mapOf(
                "query" to "Novel",
            ),
        )

        val failure =
            assertIs<AppResult.Failure>(result)

        assertEquals(
            "plugin.selector_origin_required",
            failure.error.code,
        )

        assertEquals(
            AppError.Diagnostic.of(
                "operation_index" to "0",
                "field_path" to
                    "execution_context.origin",
            ),
            failure.error.diagnostic,
        )

        assertEquals(
            expected = 0,
            actual = gateway.requests.size,
            message =
                "A relative URL must not reach the gateway without an execution origin.",
        )
    }
    private class CancellationAwareGateway :
        PluginHttpGateway {

        val started =
            CompletableDeferred<Unit>()

        var released: Boolean =
            false
            private set

        override suspend fun execute(
            request: PluginHttpRequest,
            budget: RequestBudget,
        ): AppResult<PluginHttpResponse> {
            started.complete(Unit)

            try {
                awaitCancellation()
            } finally {
                released = true
            }
        }
    }
    @Test
    fun runtimeRejectsDocumentBeyondNodeLimit() = runTest {
        val gateway = RecordingGateway(
            html = """
                <html>
                    <body>
                        <article>
                            <a href="/n/1">Novel</a>
                        </article>
                    </body>
                </html>
            """.trimIndent(),
        )

        val runtime = SelectorRuntime(
            interpreter = SelectorInterpreter(
                http = gateway,
                parser = JsoupHtmlDocumentAdapter(),
                transforms = TransformRegistry(),
                limits = SelectorLimits(
                    maxOperations = 4,
                    maxDocumentCharacters = 1_000,
                    maxDocumentNodes = 3,
                ),
            ),
        )

        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate = "https://allowed.example/",
                ),
            ),
        )

        val result = runtime.execute(
            definition = definition,
            input = emptyMap(),
        )

        val failure =
            assertIs<AppResult.Failure>(result)

        assertEquals(
            "plugin.selector_node_limit",
            failure.error.code,
        )

        assertEquals(
            1,
            gateway.requests.size,
        )
    }
    private class RecordingGateway(
        private val html: String,
    ) : PluginHttpGateway {

        val requests =
            mutableListOf<PluginHttpRequest>()

        override suspend fun execute(
            request: PluginHttpRequest,
            budget: RequestBudget,
        ): AppResult<PluginHttpResponse> {
            requests += request

            return AppResult.Success(
                PluginHttpResponse(
                    status = 200,
                    headers = emptyMap(),
                    body = html.encodeToByteArray(),
                    decodedText = html,
                ),
            )
        }
    }
}
