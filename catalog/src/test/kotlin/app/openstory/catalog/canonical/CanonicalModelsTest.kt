package app.openstory.catalog.canonical

import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanonicalModelsTest {
    private val storyId = StoryId("story:1")
    private val source = SourceKey(PluginId("plugin:one"), "source-1")

    @Test
    fun pinnedPreferenceRequiresSourceAndAutoForbidsOne() {
        assertFailsWith<IllegalArgumentException> {
            CanonicalSourcePreference(storyId, CanonicalSourcePreferenceMode.PINNED, null, revision = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalSourcePreference(storyId, CanonicalSourcePreferenceMode.AUTO, source, revision = 0)
        }
    }

    @Test
    fun canonicalScoreRequiresNormalizedBoundsAndPositiveContributorCount() {
        assertFailsWith<IllegalArgumentException> { CanonicalScore(-0.01, 1) }
        assertFailsWith<IllegalArgumentException> { CanonicalScore(1.01, 1) }
        assertFailsWith<IllegalArgumentException> { CanonicalScore(0.5, 0) }
        assertEquals(0.5, CanonicalScore(0.5, 2).normalizedValue)
    }

    @Test
    fun provenanceRequiresAtLeastOneContributor() {
        assertFailsWith<IllegalArgumentException> {
            CanonicalFieldProvenance(
                field = CanonicalFieldKey.TITLE,
                strategy = CanonicalFieldStrategy.PRIMARY_WITH_FALLBACK,
                contributors = emptyList(),
                reasonCodes = listOf("primary"),
                policyVersion = 1,
            )
        }
    }

    @Test
    fun readyStateRequiresGenerationForSameStory() {
        val story = Story(storyId, ContentType.MANGA)
        val preference = CanonicalSourcePreference(storyId, CanonicalSourcePreferenceMode.AUTO, null, 0)
        val generation = generation(StoryId("story:other"))

        assertFailsWith<IllegalArgumentException> {
            CanonicalStoryState.Ready(story, CanonicalHealth.FRESH, preference, emptyList(), generation)
        }
    }

    @Test
    fun contributorCarriesMetadataLevelAndFingerprint() {
        val contributor = CanonicalFieldContributor(source, "fusion:1", CatalogMetadataLevel.Full)

        assertEquals(CatalogMetadataLevel.Full, contributor.metadataLevel)
        assertEquals("fusion:1", contributor.fusionFingerprint)
    }

    private fun generation(id: StoryId) = CanonicalGeneration(
        id = "gen:1",
        storyId = id,
        fusionPolicyVersion = 1,
        primarySelectionPolicyVersion = 1,
        fusionFingerprint = "fusion",
        effectivePrimary = source,
        metadata = CanonicalMetadata(
            title = "Title",
            description = null,
            coverUrl = null,
            sourceUrl = null,
            popularityRank = null,
            aliases = emptyList(),
            authors = emptyList(),
            genres = emptyList(),
            languageTags = emptyList(),
            publicationStatus = null,
            latestUpdate = null,
            score = null,
        ),
        health = CanonicalHealth.FRESH,
        provenance = mapOf(
            CanonicalFieldKey.TITLE to CanonicalFieldProvenance(
                field = CanonicalFieldKey.TITLE,
                strategy = CanonicalFieldStrategy.PRIMARY_WITH_FALLBACK,
                contributors = listOf(CanonicalFieldContributor(source, "fusion", CatalogMetadataLevel.Summary)),
                reasonCodes = listOf("primary"),
                policyVersion = 1,
            ),
        ),
        createdAtEpochMillis = 1,
    )
}
