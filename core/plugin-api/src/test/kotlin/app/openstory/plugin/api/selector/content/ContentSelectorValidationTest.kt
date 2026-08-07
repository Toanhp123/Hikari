package app.openstory.plugin.api.selector.content

import app.openstory.plugin.api.selector.AttributeBinding
import app.openstory.plugin.api.selector.EnumBinding
import app.openstory.plugin.api.selector.HttpGet
import app.openstory.plugin.api.selector.ListBinding
import app.openstory.plugin.api.selector.ObjectBinding
import app.openstory.plugin.api.selector.SELECTOR_JSON
import app.openstory.plugin.api.selector.SelectorDefinition
import app.openstory.plugin.api.selector.SelectorRequestPlan
import app.openstory.plugin.api.selector.SelectorTokenKind
import app.openstory.plugin.api.selector.SelectorValidationErrorCode
import app.openstory.plugin.api.selector.TextBinding
import app.openstory.plugin.api.selector.TextSetBinding
import app.openstory.plugin.api.selector.UrlBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentSelectorValidationTest {
    @Test
    fun releaseBindingRequiresStableIdUrlLanguageAndRawTitle() {
        val selector = ContentReleasesSelector(
            request = documentRequest(),
            releases = ListBinding(
                css = "li.chapter",
                item = ObjectBinding(
                    fields = mapOf(
                        "sourceReleaseId" to AttributeBinding(attribute = "data-id"),
                        "rawTitle" to TextBinding(),
                    ),
                ),
            ),
        )

        assertEquals(
            SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
            ContentSelectorValidation.validateReleases(selector).validationCode(),
        )
    }

    @Test
    fun chapterBindingRejectsUnknownBlockVariant() {
        val source = """
            {
              "schemaVersion": 1,
              "content": {
                "chapter": {
                  "request": {"operations":[{"type":"http_get","urlTemplate":"https://allowed.example/chapter"}]},
                  "document": {
                    "blocks": {
                      "css": ".chapter > *",
                      "variants": [{"type":"video","matches":"video"}]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        assertTrue(app.openstory.plugin.api.selector.SelectorDefinitionDecoder().decode(source).isFailure)
    }

    @Test
    fun opaqueContinuationTokenRejectsUrlBinding() {
        val selector = ContentSearchSelector(
            request = documentRequest(),
            items = ListBinding(
                css = "article",
                item = ObjectBinding(
                    fields = mapOf(
                        "sourceStoryId" to AttributeBinding(attribute = "data-id"),
                        "title" to TextBinding(".title"),
                        "contentType" to EnumBinding(TextBinding(".type")),
                        "languageTags" to TextSetBinding(".language"),
                    ),
                ),
            ),
            nextToken = UrlBinding(AttributeBinding("a.next", "href")),
            nextTokenKind = SelectorTokenKind.OPAQUE,
        )

        assertEquals(
            SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
            ContentSelectorValidation.validateSearch(selector).validationCode(),
        )
    }

    @Test
    fun chapterAllowsMultipleSelectorsForTheSameClosedBlockType() {
        val selector = ContentChapterSelector(
            request = documentRequest(),
            document = ChapterDocumentBinding(
                blocks = ChapterBlockListBinding(
                    css = ".chapter > *",
                    variants = listOf(
                        DividerBlockBinding(matches = "hr"),
                        DividerBlockBinding(matches = ".separator"),
                    ),
                ),
            ),
        )

        assertTrue(ContentSelectorValidation.validateChapter(selector).isSuccess)
    }

    @Test
    fun contentEndpointsRoundTrip() {
        val release = ListBinding(
            css = ".chapter",
            item = ObjectBinding(
                fields = mapOf(
                    "sourceReleaseId" to AttributeBinding(attribute = "data-id"),
                    "sourceUrl" to UrlBinding(AttributeBinding("a", "href")),
                    "languageTag" to TextBinding(".language"),
                    "rawTitle" to TextBinding(".title"),
                ),
            ),
        )
        val definition = SelectorDefinition(
            content = ContentSelectorEndpoints(
                search = ContentSearchSelector(
                    request = documentRequest(),
                    items = ListBinding(
                        css = "article",
                        item = ObjectBinding(
                            fields = mapOf(
                                "sourceStoryId" to AttributeBinding(attribute = "data-id"),
                                "title" to TextBinding(".title"),
                                "contentType" to EnumBinding(TextBinding(".type")),
                                "languageTags" to TextSetBinding(".language"),
                            ),
                        ),
                    ),
                ),
                latest = ContentReleasesSelector(documentRequest(), release),
                allChapters = ContentReleasesSelector(documentRequest(), release),
                chapter = ContentChapterSelector(
                    request = documentRequest(),
                    document = ChapterDocumentBinding(
                        title = TextBinding("h1"),
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
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertDefinitionRoundTrips(definition)
    }

    private fun assertDefinitionRoundTrips(definition: SelectorDefinition) {
        val encoded = SELECTOR_JSON.encodeToString(
            SelectorDefinition.serializer(),
            definition,
        )
        assertEquals(
            definition,
            SELECTOR_JSON.decodeFromString(
                SelectorDefinition.serializer(),
                encoded,
            ),
        )
    }

    private fun documentRequest() = SelectorRequestPlan(
        operations = listOf(HttpGet("https://allowed.example/index")),
    )

    private fun Result<Unit>.validationCode() =
        (exceptionOrNull() as app.openstory.plugin.api.selector.SelectorValidationException).code
}
