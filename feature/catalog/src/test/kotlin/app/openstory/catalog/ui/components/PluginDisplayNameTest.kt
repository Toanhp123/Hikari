package app.openstory.catalog.ui.components

import app.openstory.common.id.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginDisplayNameTest {
    @Test
    fun knownProviderNameIsStableAcrossCatalogAndContentPluginIds() {
        assertEquals("MangaDex", PluginId("org.openstory.catalog.mangadex").catalogDisplayName())
        assertEquals("MangaDex", PluginId("org.openstory.content.mangadex").catalogDisplayName())
    }

    @Test
    fun catalogFixtureUsesReadableFallback() {
        assertEquals("Catalog A", PluginId("catalog.a").catalogDisplayName())
    }
}
