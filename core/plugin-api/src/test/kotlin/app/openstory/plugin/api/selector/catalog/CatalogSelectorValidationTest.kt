package app.openstory.plugin.api.selector.catalog

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
import app.openstory.plugin.api.selector.TextListBinding
import app.openstory.plugin.api.selector.TextSetBinding
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogSelectorValidationTest {
    @Test
    fun searchRequiresSourceIdAndTitleBindings() {
        val selector = CatalogSearchSelector(
            request = documentRequest(),
            items = ListBinding(
                css = "article",
                item = ObjectBinding(
                    fields = mapOf(
                        "sourceId" to AttributeBinding(
                            css = "a",
                            attribute = "href",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
            CatalogSelectorValidation.validateSearch(selector).validationCode(),
        )
    }

    @Test
    fun catalogFiltersRejectDuplicateIds() {
        val selector = CatalogFiltersSelector(
            filters = listOf(
                CatalogTextFilterBinding("query", "Query", null),
                CatalogSortFilterBinding(
                    id = "query",
                    label = "Sort",
                    options = listOf(CatalogFilterOptionBinding("latest", "Latest")),
                ),
            ),
        )

        assertEquals(
            SelectorValidationErrorCode.INVALID_CONSTANT,
            CatalogSelectorValidation.validateFilters(selector).validationCode(),
        )
    }

    @Test
    fun urlContinuationTokenRequiresUrlBinding() {
        val selector = validSearch().copy(
            nextToken = TextBinding(".next"),
            nextTokenKind = SelectorTokenKind.URL,
        )

        assertEquals(
            SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
            CatalogSelectorValidation.validateSearch(selector).validationCode(),
        )
    }

    @Test
    fun catalogEndpointsRoundTrip() {
        val definition = SelectorDefinition(
            catalog = CatalogSelectorEndpoints(
                search = validSearch(),
                details = CatalogDetailsSelector(
                    request = documentRequest(),
                    details = ObjectBinding(
                        fields = mapOf(
                            "sourceId" to TextBinding("[data-id]"),
                            "title" to TextBinding("h1"),
                            "contentType" to EnumBinding(TextBinding(".type")),
                            "languageTags" to TextSetBinding(".language"),
                        ),
                    ),
                ),
                filters = CatalogFiltersSelector(
                    filters = listOf(
                        CatalogTextFilterBinding("query", "Query", "Title"),
                    ),
                ),
            ),
        )

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

    private fun validSearch() = CatalogSearchSelector(
        request = documentRequest(),
        items = ListBinding(
            css = "article",
            item = ObjectBinding(
                fields = mapOf(
                    "sourceId" to AttributeBinding("a", "data-id"),
                    "title" to TextBinding(".title"),
                    "authors" to TextListBinding(".author"),
                ),
            ),
        ),
    )

    private fun documentRequest() = SelectorRequestPlan(
        operations = listOf(HttpGet("https://allowed.example/index")),
    )

    private fun Result<Unit>.validationCode() =
        (exceptionOrNull() as app.openstory.plugin.api.selector.SelectorValidationException).code
}
