package app.openstory.plugin.host.selector.mapper

import app.openstory.common.AppResult
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.api.content.ChapterBlock
import app.openstory.plugin.api.content.ChapterDocument
import app.openstory.plugin.api.content.ChapterImageReference
import app.openstory.plugin.api.content.ChapterText
import app.openstory.plugin.api.content.ChapterTextSpan
import app.openstory.plugin.api.content.ChapterTextStyle
import app.openstory.plugin.api.selector.SelectorBinding
import app.openstory.plugin.api.selector.TextBinding
import app.openstory.plugin.api.selector.content.ChapterBlockVariantBinding
import app.openstory.plugin.api.selector.content.ChapterDocumentBinding
import app.openstory.plugin.api.selector.content.ChapterSpanMode
import app.openstory.plugin.api.selector.content.ChapterTextBinding
import app.openstory.plugin.api.selector.content.DividerBlockBinding
import app.openstory.plugin.api.selector.content.HeadingBlockBinding
import app.openstory.plugin.api.selector.content.ImageBlockBinding
import app.openstory.plugin.api.selector.content.NoteBlockBinding
import app.openstory.plugin.api.selector.content.ParagraphBlockBinding
import app.openstory.plugin.api.selector.content.UnmatchedElementPolicy
import app.openstory.plugin.host.selector.HtmlDocument
import app.openstory.plugin.host.selector.HtmlDocumentAdapter
import app.openstory.plugin.host.selector.HtmlElement
import app.openstory.plugin.host.selector.HtmlSemanticStyle
import app.openstory.plugin.host.selector.binding.SelectorBindingEvaluator
import app.openstory.plugin.host.selector.binding.SelectorBoundValue
import app.openstory.plugin.host.selector.binding.SelectorEvaluationBudget
import app.openstory.plugin.host.selector.binding.SelectorFieldPath
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator

internal class ChapterDocumentMapper(
    private val outputValidator: PluginWireDtoValidator,
    private val urlPolicy: PluginUrlPolicy,
    private val html: HtmlDocumentAdapter,
    private val evaluator: SelectorBindingEvaluator,
) {
    suspend fun map(
        document: HtmlDocument,
        binding: ChapterDocumentBinding,
        budget: SelectorEvaluationBudget,
    ): AppResult<ChapterDocument> = try {
        val title = binding.title?.let {
            evaluateText(it, document, SelectorFieldPath.root("title"), budget)
        }
        val blocks = mutableListOf<ChapterBlock>()
        html.selectAll(document, binding.blocks.css).forEachIndexed { index, element ->
            budget.consumeChapterBlock()
            val variant = binding.blocks.variants.firstOrNull { html.matches(element, it.matches) }
            if (variant == null) {
                if (binding.blocks.unmatchedElementPolicy == UnmatchedElementPolicy.ERROR) {
                    throw SelectorMappingFailure(
                        "plugin.selector_field_invalid",
                        "blocks.$index",
                    )
                }
            } else {
                blocks += mapBlock(variant, element, index, budget)
            }
        }
        outputValidator.validateChapterDocument(ChapterDocument(title, blocks))
    } catch (failure: EvaluatorResultFailure) {
        failure.value
    } catch (failure: SelectorMappingFailure) {
        mappingFailure(failure)
    } catch (_: IllegalArgumentException) {
        mappingFailure(
            SelectorMappingFailure("plugin.selector_field_invalid", "blocks"),
        )
    }

    private suspend fun mapBlock(
        variant: ChapterBlockVariantBinding,
        element: HtmlElement,
        index: Int,
        budget: SelectorEvaluationBudget,
    ): ChapterBlock = when (variant) {
        is ParagraphBlockBinding -> ChapterBlock.Paragraph(
            mapText(variant.text, element, index, budget),
        )
        is HeadingBlockBinding -> ChapterBlock.Heading(
            level = evaluateInteger(variant.level, element, "blocks.$index.level", budget),
            text = mapText(variant.text, element, index, budget),
        )
        is DividerBlockBinding -> ChapterBlock.Divider
        is ImageBlockBinding -> mapImage(variant, element, index, budget)
        is NoteBlockBinding -> ChapterBlock.Note(
            mapText(variant.text, element, index, budget),
        )
    }

    private suspend fun mapText(
        binding: ChapterTextBinding,
        element: HtmlElement,
        index: Int,
        budget: SelectorEvaluationBudget,
    ): ChapterText {
        val path = SelectorFieldPath.root("blocks").index(index).field("text")
        val value = evaluateText(binding.value, element, path, budget)
        val semantic = if (binding.spans == ChapterSpanMode.SEMANTIC_HTML) {
            val css = (binding.value as? TextBinding)?.css
            html.semanticText(element, css)
        } else {
            null
        }
        val spans = semantic
            ?.takeIf { it.value == value }
            ?.spans
            ?.map { span ->
                ChapterTextSpan(
                    start = span.start,
                    endExclusive = span.endExclusive,
                    style = when (span.style) {
                        HtmlSemanticStyle.EMPHASIS -> ChapterTextStyle.EMPHASIS
                        HtmlSemanticStyle.STRONG -> ChapterTextStyle.STRONG
                    },
                )
            }
            .orEmpty()
        budget.consumeChapterCharacters(value.length)
        budget.consumeSpans(spans.size)
        return ChapterText(value, spans)
    }

    private suspend fun mapImage(
        binding: ImageBlockBinding,
        element: HtmlElement,
        index: Int,
        budget: SelectorEvaluationBudget,
    ): ChapterBlock.Image {
        val root = SelectorFieldPath.root("blocks").index(index)
        val url = evaluateText(binding.url, element, root.field("url"), budget)
        val validated = (urlPolicy.resolve(url) as? AppResult.Success)?.value
            ?: throw SelectorMappingFailure("plugin.selector_field_invalid", "blocks.$index.url")
        val declaredHost = binding.declaredHost?.let {
            evaluateText(it, element, root.field("declaredHost"), budget)
        } ?: validated.host
        val altText = binding.altText?.let {
            evaluateText(it, element, root.field("altText"), budget)
        }
        return ChapterBlock.Image(
            reference = ChapterImageReference(validated.value, declaredHost),
            altText = altText,
        )
    }

    private suspend fun evaluateText(
        binding: SelectorBinding,
        scope: app.openstory.plugin.host.selector.HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): String = when (val value = evaluate(binding, scope, path, budget)) {
        is SelectorBoundValue.Text -> value.value
        else -> throw SelectorMappingFailure("plugin.selector_field_invalid", path.value)
    }

    private suspend fun evaluateInteger(
        binding: SelectorBinding,
        scope: app.openstory.plugin.host.selector.HtmlScope,
        path: String,
        budget: SelectorEvaluationBudget,
    ): Int = when (
        val value = evaluate(binding, scope, SelectorFieldPath.root("blocks"), budget)
    ) {
        is SelectorBoundValue.IntegerValue -> value.value
        else -> throw SelectorMappingFailure("plugin.selector_field_invalid", path)
    }

    private suspend fun evaluate(
        binding: SelectorBinding,
        scope: app.openstory.plugin.host.selector.HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): SelectorBoundValue = when (val result = evaluator.evaluate(binding, scope, path, budget)) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw EvaluatorResultFailure(result)
    }
}

private class EvaluatorResultFailure(
    val value: AppResult.Failure,
) : RuntimeException(null, null, false, false)

private fun mappingFailure(failure: SelectorMappingFailure): AppResult.Failure =
    AppResult.Failure(
        app.openstory.common.AppError.Plugin(
            code = failure.code,
            retryable = false,
            diagnostic = app.openstory.common.AppError.Diagnostic.of(
                "field_path" to failure.path,
            ),
        ),
    )
