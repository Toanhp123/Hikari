package app.openstory.plugins.api.protocol.catalog

import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.PluginProtocolValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CatalogProtocolTest {
    @Test
    fun catalogSectionRejectsDuplicateSourceIds() {
        val item = CatalogItemDto(sourceId = "1", title = "One", contentType = WireContentType.MANGA)
        assertFailsWith<IllegalArgumentException> {
            CatalogSectionDto("top", "Top", listOf(item, item))
        }
    }

    @Test
    fun scoreMustCarryPositiveScale() {
        assertFailsWith<IllegalArgumentException> { ScoreDto(value = 8.0, scale = 0.0) }
    }

    @Test
    fun validatorRejectsReturnedUrlOutsideDeclaredHosts() {
        val output = CatalogSearchOutputDto(
            items = listOf(
                CatalogItemDto(
                    sourceId = "1",
                    title = "One",
                    contentType = WireContentType.MANGA,
                    coverUrl = "https://cdn.example/one.jpg",
                ),
            ),
        )
        val payload = Json.parseToJsonElement(Json.encodeToString(output))

        assertEquals(
            listOf("protocol.remote_host_denied"),
            PluginProtocolValidator.validateOutput(
                PluginOperation.CATALOG_SEARCH,
                payload,
                allowedNetworkHosts = setOf("api.example"),
            ).map { it.code },
        )
    }
}
