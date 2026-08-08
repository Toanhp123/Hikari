package app.openstory.plugin.host.selector.validation

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.api.catalog.CatalogSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginWireDtoValidatorTest {
    private val validator = PluginWireDtoValidator(
        urlPolicy = PluginUrlPolicy(setOf("allowed.example")),
    )

    @Test
    fun catalogHomeRejectsTheSecondDuplicateSectionId() {
        val sections = listOf(
            CatalogSection(sourceId = "same", title = "First", items = emptyList()),
            CatalogSection(sourceId = "same", title = "Second", items = emptyList()),
        )

        val result = validator.validateCatalogHome(sections)

        val failure = assertIs<AppResult.Failure>(result)
        val error = assertIs<AppError.Plugin>(failure.error)
        assertEquals("plugin.output_duplicate_id", error.code)
        assertEquals(
            AppError.Diagnostic.of("field_path" to "sections.1.sourceId"),
            error.diagnostic,
        )
    }
}
