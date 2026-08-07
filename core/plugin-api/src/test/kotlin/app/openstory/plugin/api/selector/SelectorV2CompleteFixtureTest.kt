package app.openstory.plugin.api.selector

import app.openstory.plugin.api.PluginApiVersion
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SelectorV2CompleteFixtureTest {
    @Test
    fun completeCatalogAndContentFixtureDecodesValidatesAndRoundTrips() {
        val source = checkNotNull(
            javaClass.getResource("/plugin-selector-v2/selector-v2.json"),
        ).readText()
        val decoded = SelectorDefinitionDecoder().decode(source).getOrThrow()
        val definition = assertIs<DecodedSelectorDefinition.V2>(decoded).definition

        assertTrue(SelectorValidation.validate(definition, manifest()).isSuccess)

        val encoded = SELECTOR_JSON.encodeToString(
            SelectorPluginDefinitionV2.serializer(),
            definition,
        )
        assertEquals(
            definition,
            SelectorDefinitionDecoder()
                .decode(encoded)
                .getOrThrow()
                .let { assertIs<DecodedSelectorDefinition.V2>(it).definition },
        )
    }

    @Test
    fun completeFixtureCoversEveryCurrentPluginEndpoint() {
        val source = checkNotNull(
            javaClass.getResource("/plugin-selector-v2/selector-v2.json"),
        ).readText()
        val definition = assertIs<DecodedSelectorDefinition.V2>(
            SelectorDefinitionDecoder().decode(source).getOrThrow(),
        ).definition

        val catalog = checkNotNull(definition.catalog)
        val content = checkNotNull(definition.content)
        assertTrue(
            listOf(catalog.home, catalog.search, catalog.details, catalog.filters)
                .all { it != null },
        )
        assertTrue(
            listOf(
                content.search,
                content.story,
                content.latest,
                content.allChapters,
                content.sync,
                content.chapter,
            ).all { it != null },
        )
    }

    private fun manifest() = PluginManifest(
        id = "fixture.selector-v2",
        name = "Selector V2 Contract Fixture",
        version = "1.0.0",
        packageChecksumSha256 = "a".repeat(64),
        minimumHostVersion = "1.0.0",
        updateUrl = "https://fixture.example/manifest.json",
        api = PluginApiVersion(1, 0),
        kinds = setOf(PluginKind.CATALOG, PluginKind.CONTENT),
        languages = setOf("en"),
        allowedHosts = setOf("fixture.example"),
        declarativeOrigin = "https://fixture.example/",
        capabilities = setOf(PluginCapability.NETWORK),
        runtime = PluginRuntime.DECLARATIVE,
        entry = "selector.json",
    )
}
