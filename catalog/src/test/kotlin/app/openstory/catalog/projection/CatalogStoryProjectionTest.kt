package app.openstory.catalog.projection

import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.canonical.CanonicalScore
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogStoryProjectionTest {
    @Test
    fun projectionUsesActiveCanonicalGenerationRatherThanRawSourceOrder() {
        val storyId = StoryId("story-1")
        val state = CanonicalStoryState.Ready(
            story = Story(storyId, ContentType.WEB_NOVEL),
            health = CanonicalHealth.STALE,
            preference = CanonicalSourcePreference(storyId, CanonicalSourcePreferenceMode.AUTO, null, 0),
            sources = emptyList(),
            generation = CanonicalGeneration(
                id = "gen:1",
                storyId = storyId,
                fusionPolicyVersion = 1,
                primarySelectionPolicyVersion = 1,
                fusionFingerprint = "fusion",
                effectivePrimary = SourceKey(PluginId("catalog.b"), "source-b"),
                metadata = CanonicalMetadata(
                    title = "Canonical B",
                    description = "Description",
                    coverUrl = "https://example.test/b.jpg",
                    sourceUrl = "https://example.test/b",
                    popularityRank = 4,
                    aliases = listOf("Alias"),
                    authors = listOf("Author"),
                    genres = listOf("Drama"),
                    languageTags = listOf("en"),
                    publicationStatus = PublicationStatus.ONGOING,
                    latestUpdate = CatalogLatestUpdate(10L, "Ch. 1"),
                    score = CanonicalScore(0.8, 2),
                ),
                health = CanonicalHealth.STALE,
                provenance = emptyMap(),
                createdAtEpochMillis = 10L,
            ),
        )

        val projection = state.toProjection()
        assertEquals("Canonical B", projection.title)
        assertEquals("https://example.test/b.jpg", projection.coverUrl)
        assertEquals(PublicationStatus.ONGOING, projection.publicationStatus)
        assertEquals(0.8, projection.score?.normalizedValue)
        assertEquals(CanonicalHealth.STALE, projection.health)
    }
}
