package app.openstory.plugin.host.selector.binding

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.api.selector.AttributeBinding
import app.openstory.plugin.api.selector.BooleanBinding
import app.openstory.plugin.api.selector.DoubleBinding
import app.openstory.plugin.api.selector.EnumBinding
import app.openstory.plugin.api.selector.IntegerBinding
import app.openstory.plugin.api.selector.ListBinding
import app.openstory.plugin.api.selector.LongBinding
import app.openstory.plugin.api.selector.ObjectBinding
import app.openstory.plugin.api.selector.TextBinding
import app.openstory.plugin.api.selector.TextListBinding
import app.openstory.plugin.api.selector.ConstantTextBinding
import app.openstory.plugin.api.selector.SelectorTimestampFormat
import app.openstory.plugin.api.selector.TimestampBinding
import app.openstory.plugin.api.selector.UrlBinding
import app.openstory.plugin.host.selector.JsoupHtmlDocumentAdapter
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SelectorBindingEvaluatorTest {
    private val parser = JsoupHtmlDocumentAdapter()
    private val evaluator = SelectorBindingEvaluator(
        html = parser,
        urlPolicy = PluginUrlPolicy(setOf("allowed.example")),
    )

    @Test
    fun nestedMissingValueReportsItsStableFieldPath() = runTest {
        val document = parser.parse(
            html = """
                <main>
                  <article><a href="/n/1"><span>A</span></a></article>
                  <article><a href="/n/2"></a></article>
                </main>
            """.trimIndent(),
            baseUri = "https://allowed.example/search",
        )
        val binding = ListBinding(
            css = "article",
            item = ObjectBinding(
                fields = linkedMapOf(
                    "sourceId" to AttributeBinding(css = "a", attribute = "href"),
                    "title" to TextBinding(css = "span"),
                ),
            ),
        )

        val result = evaluator.evaluate(
            binding = binding,
            scope = document,
            path = SelectorFieldPath.root("items"),
            budget = SelectorEvaluationBudget(),
        )

        assertPluginFailure(
            result = result,
            code = "plugin.selector_field_missing",
            fieldPath = "items.1.title",
        )
    }

    @Test
    fun textListStopsAtTheEndpointWideOutputLimit() = runTest {
        val document = parser.parse(
            html = "<ul><li>1</li><li>2</li><li>3</li><li>4</li></ul>",
            baseUri = "https://allowed.example/",
        )

        val result = evaluator.evaluate(
            binding = TextListBinding(css = "li"),
            scope = document,
            path = SelectorFieldPath.root("items"),
            budget = SelectorEvaluationBudget(maxOutputItems = 3),
        )

        assertPluginFailure(
            result = result,
            code = "plugin.selector_output_limit",
            fieldPath = "items",
        )
    }

    @Test
    fun relativeUrlUsesTheFetchedDocumentBaseAndAllowedHostPolicy() = runTest {
        val document = parser.parse(
            html = "<main></main>",
            baseUri = "https://allowed.example/catalog/search",
        )

        val result = evaluator.evaluate(
            binding = UrlBinding(ConstantTextBinding("../novel/1")),
            scope = document,
            path = SelectorFieldPath.root("sourceUrl"),
            budget = SelectorEvaluationBudget(),
        )

        val success = assertIs<AppResult.Success<SelectorBoundValue.Text>>(result)
        assertEquals("https://allowed.example/novel/1", success.value.value)
    }

    @Test
    fun scalarBindingsProduceTypedValues() = runTest {
        val document = parser.parse("<main></main>", "https://allowed.example/")
        val binding = ObjectBinding(
            linkedMapOf(
                "integer" to IntegerBinding(ConstantTextBinding("7")),
                "long" to LongBinding(ConstantTextBinding("9000000000")),
                "double" to DoubleBinding(ConstantTextBinding("8.5")),
                "boolean" to BooleanBinding(ConstantTextBinding("yes"), setOf("yes")),
                "enum" to EnumBinding(ConstantTextBinding("novel"), mapOf("novel" to "LIGHT_NOVEL")),
                "timestamp" to TimestampBinding(
                    ConstantTextBinding("2026-08-07T00:00:00Z"),
                    SelectorTimestampFormat.ISO_8601,
                ),
            ),
        )

        val result = evaluator.evaluate(
            binding,
            document,
            SelectorFieldPath.root("values"),
            SelectorEvaluationBudget(),
        )

        val fields = assertIs<AppResult.Success<SelectorBoundValue.ObjectValue>>(result).value.fields
        assertEquals(SelectorBoundValue.IntegerValue(7), fields["integer"])
        assertEquals(SelectorBoundValue.LongValue(9_000_000_000), fields["long"])
        assertEquals(SelectorBoundValue.DoubleValue(8.5), fields["double"])
        assertEquals(SelectorBoundValue.BooleanValue(true), fields["boolean"])
        assertEquals(SelectorBoundValue.Text("LIGHT_NOVEL"), fields["enum"])
        assertEquals(SelectorBoundValue.LongValue(1_786_060_800_000), fields["timestamp"])
    }

    @Test
    fun malformedScalarFailsClosedWithOnlyItsFieldPath() = runTest {
        val document = parser.parse("<main></main>", "https://allowed.example/private?cursor=secret")

        val result = evaluator.evaluate(
            IntegerBinding(ConstantTextBinding("not-an-integer")),
            document,
            SelectorFieldPath.root("details").field("popularityRank"),
            SelectorEvaluationBudget(),
        )

        assertPluginFailure(
            result,
            code = "plugin.selector_field_invalid",
            fieldPath = "details.popularityRank",
        )
    }

    @Test
    fun cancellationDuringLargeListProducesNoPartialResult() = runTest {
        val html = buildString {
            append("<main>")
            repeat(5_000) { append("<article><span>Novel</span></article>") }
            append("</main>")
        }
        val document = parser.parse(html, "https://allowed.example/")
        val deferred = async {
            evaluator.evaluate(
                binding = ListBinding(
                    css = "article",
                    item = TextBinding("span"),
                ),
                scope = document,
                path = SelectorFieldPath.root("items"),
                budget = SelectorEvaluationBudget(),
            )
        }

        yield()
        deferred.cancel()

        assertFailsWith<kotlinx.coroutines.CancellationException> {
            deferred.await()
        }
    }

    private fun assertPluginFailure(
        result: AppResult<SelectorBoundValue>,
        code: String,
        fieldPath: String,
    ) {
        val failure = assertIs<AppResult.Failure>(result)
        val error = assertIs<AppError.Plugin>(failure.error)
        assertEquals(code, error.code)
        assertEquals(
            AppError.Diagnostic.of("field_path" to fieldPath),
            error.diagnostic,
        )
    }
}
