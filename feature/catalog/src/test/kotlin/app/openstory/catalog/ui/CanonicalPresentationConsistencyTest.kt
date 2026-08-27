package app.openstory.catalog.ui

import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.canonical.CanonicalScore
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.fusion.CanonicalFusionService
import app.openstory.catalog.fusion.CatalogFusionEngine
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.projection.toProjection
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.catalog.ui.discover.DiscoverViewModel
import app.openstory.catalog.ui.discover.projectSemanticDiscoverState
import app.openstory.catalog.ui.library.LibrarySourceState
import app.openstory.catalog.ui.library.LibraryViewModel
import app.openstory.catalog.ui.library.toLibraryItemUiModel
import app.openstory.catalog.ui.search.SearchViewModel
import app.openstory.catalog.ui.story.StoryViewModel
import app.openstory.catalog.ui.story.toStoryUiModel
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanonicalPresentationConsistencyTest {
    @Test
    fun canonicalGenerationOwnsSharedPresentationAcrossFeatureSurfaces() {
        val ready = canonicalReadyState()
        val projection = ready.toProjection()
        val story = ready.toStoryUiModel(emptyList())
        val search = CatalogSearchStory(ready.story, projection, emptyList()).presentation
        val discover = projectSemanticDiscoverState(
            homes = listOf(rawHome(ready.story.id)),
            projections = listOf(projection),
            selectedContentType = ContentType.MANGA,
            loading = false,
            refreshing = false,
            refreshReport = null,
        ).popular.single()
        val library = LibraryEntry(
            storyId = ready.story.id,
            status = LibraryStatus.READING,
            addedAt = 1L,
            updatedAt = 2L,
        ).toLibraryItemUiModel(
            projection = projection,
            sourceState = LibrarySourceState.NO_MAPPING,
            progress = null,
        )

        assertEquals("Canonical title", story.preferredTitle)
        assertEquals(story.preferredTitle, search.title)
        assertEquals(story.preferredTitle, discover.title)
        assertEquals(story.preferredTitle, library.title)

        assertEquals("https://example.test/canonical.jpg", story.coverUrl)
        assertEquals(story.coverUrl, search.coverUrl)
        assertEquals(story.coverUrl, discover.coverUrl)
        assertEquals(story.coverUrl, library.coverUrl)

        assertEquals(PublicationStatus.COMPLETED, story.publicationStatus)
        assertEquals(story.publicationStatus, search.publicationStatus)
        assertEquals(story.publicationStatus, discover.publicationStatus)
        assertEquals(story.publicationStatus, library.publicationStatus)

        assertEquals(8.2, story.score?.value)
        assertEquals(story.score?.value, discover.score?.value)
        assertEquals(story.score?.value, library.score?.value)
        assertEquals(story.score?.value, search.score?.normalizedValue?.times(PRESENTATION_SCORE_SCALE))
    }

    @Test
    fun featureViewModelsDoNotOwnFusionEngineOrFusionService() {
        val forbidden = setOf(CatalogFusionEngine::class.java.name, CanonicalFusionService::class.java.name)
        val viewModels = listOf(
            StoryViewModel::class.java,
            SearchViewModel::class.java,
            DiscoverViewModel::class.java,
            LibraryViewModel::class.java,
        )

        viewModels.forEach { type ->
            val dependencies = type.declaredConstructors.flatMap { constructor ->
                constructor.parameterTypes.map(Class<*>::getName)
            }
            assertTrue(
                dependencies.none(forbidden::contains),
                "${type.simpleName} must not own canonical fusion",
            )
        }
    }

    private fun canonicalReadyState(): CanonicalStoryState.Ready {
        val storyId = StoryId("story:canonical")
        val source = SourceKey(PluginId("catalog.a"), "source-a")
        return CanonicalStoryState.Ready(
            story = Story(storyId, ContentType.MANGA),
            health = CanonicalHealth.FRESH,
            preference = CanonicalSourcePreference(
                storyId = storyId,
                mode = CanonicalSourcePreferenceMode.AUTO,
                pinnedSource = null,
                revision = 0L,
            ),
            sources = emptyList(),
            generation = CanonicalGeneration(
                id = "gen:canonical",
                storyId = storyId,
                fusionPolicyVersion = 1,
                primarySelectionPolicyVersion = 1,
                fusionFingerprint = "fusion:canonical",
                effectivePrimary = source,
                metadata = CanonicalMetadata(
                    title = "Canonical title",
                    description = "Canonical description",
                    coverUrl = "https://example.test/canonical.jpg",
                    sourceUrl = "https://example.test/canonical",
                    popularityRank = 1L,
                    aliases = listOf("Canonical alias"),
                    authors = listOf("Canonical author"),
                    genres = listOf("Drama"),
                    languageTags = listOf("en"),
                    publicationStatus = PublicationStatus.COMPLETED,
                    latestUpdate = null,
                    score = CanonicalScore(0.82, 2),
                ),
                health = CanonicalHealth.FRESH,
                provenance = emptyMap(),
                createdAtEpochMillis = 10L,
            ),
        )
    }

    private fun rawHome(storyId: StoryId): CatalogHomeSnapshot {
        val pluginId = PluginId("catalog.raw")
        val raw = CatalogEntry(
            storyId = storyId,
            pluginId = pluginId,
            sourceId = "raw-source",
            title = "Raw provider title",
            contentType = ContentType.MANGA,
            coverUrl = "https://example.test/raw.jpg",
            score = Score(2.0, 10.0),
            publicationStatus = PublicationStatus.ONGOING,
            popularityRank = 1L,
        )
        return CatalogHomeSnapshot(
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = 1L,
            sections = listOf(
                CatalogHomeSection(
                    sourceId = "popular",
                    title = "Popular",
                    items = listOf(raw),
                    kind = CatalogFeedKind.POPULAR,
                ),
            ),
        )
    }

    private companion object {
        const val PRESENTATION_SCORE_SCALE = 10.0
    }
}
