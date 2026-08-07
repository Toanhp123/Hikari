package app.openstory.plugin.host.selector

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.selector.HttpGet
import app.openstory.plugin.api.selector.RemoveElements
import app.openstory.plugin.api.selector.SelectorRequestPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SelectorDocumentLoaderTest {
    @Test
    fun loadsDocumentWithinDeclaredHost() = runTest {
        val gateway = RecordingGateway("<article>Novel</article>")
        val loader = loader(gateway)

        val result = loader.load(
            request = SelectorRequestPlan(
                listOf(HttpGet("/search?q={query}")),
            ),
            input = mapOf("query" to "Light Novel"),
            context = SelectorExecutionContext("https://allowed.example/catalog/"),
        )

        assertIs<AppResult.Success<HtmlDocument>>(result)
        assertEquals(
            "https://allowed.example/search?q=Light%20Novel",
            gateway.requests.single().url,
        )
    }

    @Test
    fun removesDeclaredElementsBeforeReturningDocument() = runTest {
        val parser = RecordingParser()
        val loader = loader(RecordingGateway("<p>Body</p>"), parser)

        val result = loader.load(
            request = SelectorRequestPlan(
                listOf(
                    HttpGet("https://allowed.example/chapter"),
                    RemoveElements(".advertisement"),
                ),
            ),
            input = emptyMap(),
            context = SelectorExecutionContext(),
        )

        assertIs<AppResult.Success<HtmlDocument>>(result)
        assertEquals(listOf(".advertisement"), parser.removedSelectors)
    }

    @Test
    fun missingTemplateInputReturnsTypedFailure() = runTest {
        val result = loader(RecordingGateway("unused")).load(
            request = SelectorRequestPlan(
                listOf(HttpGet("https://allowed.example/{storyId}")),
            ),
            input = emptyMap(),
            context = SelectorExecutionContext(),
        )

        assertPluginFailure(
            result = result,
            code = "plugin.selector_missing_input",
            operationIndex = "0",
        )
    }

    @Test
    fun documentCharacterLimitIsEnforcedBeforeParse() = runTest {
        val parser = RecordingParser()
        val result = loader(
            gateway = RecordingGateway("12345"),
            parser = parser,
            limits = SelectorLimits(maxDocumentCharacters = 4),
        ).load(absoluteRequest(), emptyMap(), SelectorExecutionContext())

        assertPluginFailure(result, "plugin.selector_document_limit", "0")
        assertEquals(0, parser.parseCount)
    }

    @Test
    fun nodeLimitIsEnforcedAfterParse() = runTest {
        val parser = RecordingParser(nodeCount = 4)
        val result = loader(
            gateway = RecordingGateway("<p>Body</p>"),
            parser = parser,
            limits = SelectorLimits(maxDocumentNodes = 3),
        ).load(absoluteRequest(), emptyMap(), SelectorExecutionContext())

        assertPluginFailure(result, "plugin.selector_node_limit", "0")
        assertEquals(1, parser.parseCount)
    }

    @Test
    fun cancellationPropagatesWithoutSuccessValue() = runTest {
        val gateway = CancellationGateway()
        val deferred = async {
            loader(gateway).load(
                absoluteRequest(),
                emptyMap(),
                SelectorExecutionContext(),
            )
        }
        gateway.started.await()

        deferred.cancel()

        assertFailsWith<kotlinx.coroutines.CancellationException> {
            deferred.await()
        }
        assertTrue(gateway.released)
    }

    @Test
    fun operationFailureContainsOperationIndex() = runTest {
        val result = loader(RecordingGateway("nope", status = 503)).load(
            absoluteRequest(),
            emptyMap(),
            SelectorExecutionContext(),
        )

        assertPluginFailure(result, "plugin.selector_http_status", "0")
    }

    private fun absoluteRequest() = SelectorRequestPlan(
        listOf(HttpGet("https://allowed.example/chapter")),
    )

    private fun loader(
        gateway: PluginHttpGateway,
        parser: HtmlDocumentAdapter = RecordingParser(),
        limits: SelectorLimits = SelectorLimits(),
    ) = SelectorDocumentLoader(gateway, parser, limits)

    private fun assertPluginFailure(
        result: AppResult<HtmlDocument>,
        code: String,
        operationIndex: String,
    ) {
        val failure = assertIs<AppResult.Failure>(result)
        val error = assertIs<AppError.Plugin>(failure.error)
        assertEquals(code, error.code)
        assertEquals(
            AppError.Diagnostic.of("operation_index" to operationIndex),
            error.diagnostic,
        )
    }

    private class RecordingGateway(
        private val html: String,
        private val status: Int = 200,
    ) : PluginHttpGateway {
        val requests = mutableListOf<PluginHttpRequest>()

        override suspend fun execute(
            request: PluginHttpRequest,
            budget: RequestBudget,
        ): AppResult<PluginHttpResponse> {
            requests += request
            return AppResult.Success(
                PluginHttpResponse(
                    status = status,
                    headers = emptyMap(),
                    body = html.encodeToByteArray(),
                    decodedText = html,
                ),
            )
        }
    }

    private class CancellationGateway : PluginHttpGateway {
        val started = CompletableDeferred<Unit>()
        var released = false

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

    private class RecordingParser(
        private val nodeCount: Int = 1,
    ) : HtmlDocumentAdapter {
        var parseCount = 0
        val removedSelectors = mutableListOf<String>()

        override fun parse(html: String, baseUri: String): HtmlDocument {
            parseCount += 1
            return TestDocument
        }

        override fun nodeCount(document: HtmlDocument): Int = nodeCount

        override fun removeElements(
            document: HtmlDocument,
            css: String,
        ): HtmlDocument {
            removedSelectors += css
            return document
        }

        override fun selectAll(document: HtmlDocument, css: String) =
            emptyList<HtmlElement>()

        override fun selectText(elements: List<HtmlElement>, css: String) =
            emptyList<String>()

        override fun selectAttribute(
            elements: List<HtmlElement>,
            css: String,
            attribute: String,
        ) = emptyList<HtmlAttributeValue>()
    }

    private data object TestDocument : HtmlDocument
}
