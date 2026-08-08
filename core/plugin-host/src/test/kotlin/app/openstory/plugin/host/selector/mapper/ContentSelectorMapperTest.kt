package app.openstory.plugin.host.selector.mapper

import app.openstory.common.AppResult
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.content.ChapterBlock
import app.openstory.plugin.api.content.ContentStoryCandidate
import app.openstory.plugin.api.content.ChapterTextStyle
import app.openstory.plugin.api.selector.AttributeBinding
import app.openstory.plugin.api.selector.TextBinding
import app.openstory.plugin.api.selector.UrlBinding
import app.openstory.plugin.api.selector.content.ChapterBlockListBinding
import app.openstory.plugin.api.selector.content.ChapterDocumentBinding
import app.openstory.plugin.api.selector.content.ChapterSpanMode
import app.openstory.plugin.api.selector.content.ChapterTextBinding
import app.openstory.plugin.api.selector.content.DividerBlockBinding
import app.openstory.plugin.api.selector.content.ImageBlockBinding
import app.openstory.plugin.api.selector.content.ParagraphBlockBinding
import app.openstory.plugin.host.selector.JsoupHtmlDocumentAdapter
import app.openstory.plugin.host.selector.binding.SelectorBindingEvaluator
import app.openstory.plugin.host.selector.binding.SelectorEvaluationBudget
import app.openstory.plugin.host.selector.binding.SelectorBoundValue
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ContentSelectorMapperTest {
    private val html = JsoupHtmlDocumentAdapter()
    private val urlPolicy = PluginUrlPolicy(setOf("allowed.example"))
    private val evaluator = SelectorBindingEvaluator(html, urlPolicy)
    private val mapper = ContentSelectorMapper(
        outputValidator = PluginWireDtoValidator(urlPolicy),
        urlPolicy = urlPolicy,
        html = html,
        evaluator = evaluator,
    )

    @Test
    fun chapterPreservesDomOrderAndSemanticSpans() = runTest {
        val document = html.parse(
            html = """
                <main class="chapter">
                  <p>Hello <strong>world</strong></p>
                  <hr>
                  <img src="/images/one.jpg" alt="Cover">
                </main>
            """.trimIndent(),
            baseUri = "https://allowed.example/chapter/1",
        )
        val binding = ChapterDocumentBinding(
            title = null,
            blocks = ChapterBlockListBinding(
                css = ".chapter > *",
                variants = listOf(
                    ParagraphBlockBinding(
                        matches = "p",
                        text = ChapterTextBinding(
                            value = TextBinding(),
                            spans = ChapterSpanMode.SEMANTIC_HTML,
                        ),
                    ),
                    DividerBlockBinding(matches = "hr"),
                    ImageBlockBinding(
                        matches = "img",
                        url = UrlBinding(AttributeBinding(attribute = "src")),
                        altText = AttributeBinding(attribute = "alt"),
                    ),
                ),
            ),
        )

        val result = mapper.mapChapter(
            document = document,
            binding = binding,
            budget = SelectorEvaluationBudget(),
        )

        val chapter = assertIs<AppResult.Success<app.openstory.plugin.api.content.ChapterDocument>>(result).value
        assertEquals(3, chapter.blocks.size)
        val paragraph = assertIs<ChapterBlock.Paragraph>(chapter.blocks[0])
        assertEquals("Hello world", paragraph.text.value)
        assertEquals(ChapterTextStyle.STRONG, paragraph.text.spans.single().style)
        assertIs<ChapterBlock.Divider>(chapter.blocks[1])
        val image = assertIs<ChapterBlock.Image>(chapter.blocks[2])
        assertEquals("https://allowed.example/images/one.jpg", image.reference.url)
        assertEquals("allowed.example", image.reference.declaredHost)
        assertEquals("Cover", image.altText)
    }

    @Test
    fun searchMapsCandidateAndContinuationToken() {
        val result = mapper.mapSearch(
            items = list(
                obj(
                    "sourceStoryId" to text("story-1"),
                    "sourceUrl" to text("https://allowed.example/story/1"),
                    "title" to text("Story One"),
                    "authors" to list(text("Author")),
                    "contentType" to text("LIGHT_NOVEL"),
                    "languageTags" to list(text("en")),
                ),
            ),
            nextToken = text("cursor-2"),
        )

        val page = assertIs<AppResult.Success<Page<ContentStoryCandidate>>>(result).value
        assertEquals("story-1", page.items.single().sourceStoryId)
        assertEquals("cursor-2", page.nextToken)
    }

    private fun text(value: String) = SelectorBoundValue.Text(value)

    private fun list(vararg values: SelectorBoundValue) =
        SelectorBoundValue.ListValue(values.toList())

    private fun obj(vararg fields: Pair<String, SelectorBoundValue>) =
        SelectorBoundValue.ObjectValue(linkedMapOf(*fields))
}
