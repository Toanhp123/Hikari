package app.openstory.catalog.model

import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogModelsTest {
    @Test
    fun storyStaysMinimal() {
        val story = Story(StoryId("story:1"), ContentType.MANGA)

        assertEquals(StoryId("story:1"), story.id)
        assertEquals(ContentType.MANGA, story.contentType)
    }

    @Test
    fun scoreRequiresPositiveScaleAndBoundedValue() {
        assertFailsWith<IllegalArgumentException> { Score(8.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { Score(11.0, 10.0) }
        assertFailsWith<IllegalArgumentException> {
            Score(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun catalogEntryExternalIdentifiersDefaultEmptyAndPreserveValues() {
        val base = CatalogEntry(
            StoryId("story:1"),
            PluginId("plugin:mal"),
            "source",
            "Title",
            contentType = ContentType.MANGA,
        )
        val identifier = ExternalIdentifier(
            namespace = "openlibrary.work",
            value = "OL123W",
            scope = ExternalIdentifierScope.WORK,
        )

        assertEquals(emptySet(), base.externalIdentifiers)
        assertEquals(setOf(identifier), base.copy(externalIdentifiers = setOf(identifier)).externalIdentifiers)
    }

    @Test
    fun entryRejectsBlankSourceIdentity() {
        assertFailsWith<IllegalArgumentException> {
            CatalogEntry(StoryId("story:1"), PluginId("plugin:mal"), " ", "Title", contentType = ContentType.MANGA)
        }
    }
}
