package app.openstory.plugin.host.selector.mapper

import app.openstory.common.AppResult
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.api.selector.SelectorTokenKind
import app.openstory.plugin.host.selector.binding.SelectorBoundValue
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CatalogSelectorMapperTest {
    private val urlPolicy = PluginUrlPolicy(setOf("allowed.example"))
    private val mapper = CatalogSelectorMapper(
        outputValidator = PluginWireDtoValidator(urlPolicy),
        urlPolicy = urlPolicy,
    )

    @Test
    fun searchMapsTypedCardAndOpaqueContinuationToken() {
        val items = SelectorBoundValue.ListValue(
            listOf(
                obj(
                    "sourceId" to text("novel-1"),
                    "title" to text("Novel One"),
                    "authors" to list(text("Author")),
                    "image" to SelectorBoundValue.Null,
                    "score" to SelectorBoundValue.Null,
                ),
            ),
        )

        val result = mapper.mapSearch(
            items = items,
            nextToken = text("cursor-2"),
            nextTokenKind = SelectorTokenKind.OPAQUE,
        )

        val page = assertIs<AppResult.Success<*>>(result).value as app.openstory.plugin.api.Page<*>
        val card = page.items.single() as app.openstory.plugin.api.catalog.CatalogCard
        assertEquals("novel-1", card.sourceId)
        assertEquals("Novel One", card.title)
        assertEquals(listOf("Author"), card.authors)
        assertEquals("cursor-2", page.nextToken)
    }

    private fun text(value: String) = SelectorBoundValue.Text(value)

    private fun list(vararg values: SelectorBoundValue) =
        SelectorBoundValue.ListValue(values.toList())

    private fun obj(vararg fields: Pair<String, SelectorBoundValue>) =
        SelectorBoundValue.ObjectValue(linkedMapOf(*fields))
}
