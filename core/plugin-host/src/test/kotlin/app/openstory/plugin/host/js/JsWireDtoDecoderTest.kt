package app.openstory.plugin.host.js

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JsWireDtoDecoderTest {
    @Test
    fun catalogDetailsUsesTheSharedOutputHostValidator() {
        val decoder = JsWireDtoDecoder(
            PluginWireDtoValidator(PluginUrlPolicy(setOf("allowed.example"))),
        )
        val source = """
            {
              "sourceId":"story-1",
              "sourceUrl":null,
              "title":"Novel",
              "aliases":[],
              "authors":[],
              "description":null,
              "genres":[],
              "contentType":"LIGHT_NOVEL",
              "languageTags":["en"],
              "image":{"url":"https://evil.example/cover.jpg","declaredHost":"evil.example"},
              "score":null,
              "popularityRank":null
            }
        """.trimIndent()

        val result = decoder.decodeCatalogDetails(source)

        val failure = assertIs<AppResult.Failure>(result)
        val error = assertIs<AppError.Plugin>(failure.error)
        assertEquals("plugin.output_undeclared_host", error.code)
        assertEquals(
            AppError.Diagnostic.of("field_path" to "details.image.url"),
            error.diagnostic,
        )
    }
}
