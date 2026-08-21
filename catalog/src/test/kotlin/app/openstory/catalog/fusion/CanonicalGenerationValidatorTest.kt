package app.openstory.catalog.fusion

import app.openstory.catalog.canonical.CanonicalFieldContributor
import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.canonical.CanonicalFieldProvenance
import app.openstory.catalog.canonical.CanonicalFieldStrategy
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertTrue

class CanonicalGenerationValidatorTest {
    private val storyId = StoryId("story:validator")
    private val source = SourceKey(PluginId("provider.a"), "source")
    private val story = Story(storyId, ContentType.MANGA)

    @Test
    fun rejectsOwnershipLatestCoherenceContentTypeAndMissingTitleProvenance() {
        val foreign = SourceKey(PluginId("provider.foreign"), "source")
        val candidate = candidate(
            storyId = StoryId("story:other"),
            primary = foreign,
            provenance = mapOf(
                CanonicalFieldKey.LATEST_UPDATE to provenance(CanonicalFieldKey.LATEST_UPDATE, listOf(source, foreign)),
            ),
            sourceContentTypes = mapOf(source to ContentType.ANIME),
        )
        val errors = CanonicalGenerationValidator().validate(story, setOf(source), candidate)

        assertTrue(errors.any { "story" in it })
        assertTrue(errors.any { "primary" in it })
        assertTrue(errors.any { "contributor" in it })
        assertTrue(errors.any { "latest" in it })
        assertTrue(errors.any { "content-type" in it })
        assertTrue(errors.any { "title" in it })
    }

    @Test
    fun acceptsOwnedCoherentCandidate() {
        val candidate = candidate(
            storyId = storyId,
            primary = source,
            provenance = mapOf(CanonicalFieldKey.TITLE to provenance(CanonicalFieldKey.TITLE, listOf(source))),
            sourceContentTypes = mapOf(source to ContentType.MANGA),
        )
        assertTrue(CanonicalGenerationValidator().validate(story, setOf(source), candidate).isEmpty())
    }

    private fun candidate(
        storyId: StoryId,
        primary: SourceKey,
        provenance: Map<CanonicalFieldKey, CanonicalFieldProvenance>,
        sourceContentTypes: Map<SourceKey, ContentType>,
    ) = CanonicalGenerationCandidate(
        storyId = storyId,
        fusionPolicyVersion = 1,
        primarySelectionPolicyVersion = 1,
        fusionFingerprint = "fusion",
        effectivePrimary = primary,
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
        provenance = provenance,
        createdAtEpochMillis = 1L,
        sourceContentTypes = sourceContentTypes,
    )

    private fun provenance(field: CanonicalFieldKey, sources: List<SourceKey>) = CanonicalFieldProvenance(
        field = field,
        strategy = if (field == CanonicalFieldKey.LATEST_UPDATE) {
            CanonicalFieldStrategy.FRESHEST_COHERENT_OBJECT
        } else {
            CanonicalFieldStrategy.PRIMARY_WITH_FALLBACK
        },
        contributors = sources.map {
            CanonicalFieldContributor(it, "fusion:${it.pluginId.value}", CatalogMetadataLevel.Full)
        },
        reasonCodes = listOf("test"),
        policyVersion = 1,
    )
}
