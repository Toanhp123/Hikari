package app.openstory.home.domain

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogSnapshotMapperTest {
    @Test
    fun mapsCatalogProvenanceAndEntryFieldsToHomeUi() {
        val snapshot = CatalogHomeSnapshot(
            pluginId = PluginId("catalog.a"),
            pluginVersion = "2.3.4",
            refreshedAtEpochMillis = 100L,
            sections = listOf(
                CatalogHomeSection(
                    sourceId = "trending",
                    title = "Trending",
                    items = listOf(
                        CatalogEntry(
                            storyId = StoryId("story-1"),
                            pluginId = PluginId("catalog.a"),
                            sourceId = "source-1",
                            title = "Story One",
                            contentType = ContentType.WEB_NOVEL,
                        ),
                    ),
                ),
            ),
        )

        val mapped = CatalogSnapshotMapper().map(snapshot)

        assertEquals("2.3.4", mapped.pluginVersion)
        assertEquals("Story One", mapped.sections.single().items.single().title)
    }
}
