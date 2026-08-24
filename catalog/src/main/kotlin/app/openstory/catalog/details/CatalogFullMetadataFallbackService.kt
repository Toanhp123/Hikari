package app.openstory.catalog.details

import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.fusion.CatalogFusionEngine
import app.openstory.catalog.fusion.CatalogSourceAvailabilityResolver
import app.openstory.catalog.fusion.FusionInput
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.metadata.CatalogMetadataAccess
import app.openstory.catalog.metadata.CatalogMetadataCoordinator
import app.openstory.catalog.metadata.CatalogMetadataFailure
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.metadata.CatalogMetadataResult
import app.openstory.catalog.model.CatalogEntry
import app.openstory.common.id.StoryId
import javax.inject.Inject

sealed interface CatalogFullFallbackResult {
    data class Ready(
        val storyId: StoryId,
        val sourceKey: SourceKey,
        val entry: CatalogEntry,
    ) : CatalogFullFallbackResult

    data class Failure(
        val attempts: List<CatalogFullAttemptFailure>,
    ) : CatalogFullFallbackResult
}

data class CatalogFullAttemptFailure(
    val sourceKey: SourceKey,
    val failure: CatalogMetadataFailure,
)

class CatalogFullMetadataFallbackService internal constructor(
    private val canonical: CanonicalCatalogRepository,
    private val metadata: CatalogMetadataAccess,
    private val fusion: CatalogFusionEngine,
    private val availability: CatalogSourceAvailabilityResolver,
    private val identity: StoryIdentityRepository,
) {
    @Inject
    constructor(
        canonical: CanonicalCatalogRepository,
        metadata: CatalogMetadataCoordinator,
        fusion: CatalogFusionEngine,
        availability: CatalogSourceAvailabilityResolver,
        identity: StoryIdentityRepository,
    ) : this(canonical, metadata as CatalogMetadataAccess, fusion, availability, identity)

    suspend fun requireFull(storyId: StoryId): CatalogFullFallbackResult {
        val resolvedStoryId = identity.resolve(storyId)
        val state = canonical.state(resolvedStoryId)
        val records = canonical.sourceRecords(resolvedStoryId)
        if (state == null || records.isEmpty()) return CatalogFullFallbackResult.Failure(emptyList())

        val rankedSourceKeys = fusion.rankedEligibleSourceKeys(
            FusionInput(
                story = state.story,
                sources = availability.resolve(records),
                previousGeneration = canonical.activeGeneration(resolvedStoryId),
                preference = canonical.sourcePreference(resolvedStoryId),
                evaluatedAtEpochMillis = 0L,
            ),
        )
        return if (rankedSourceKeys.isEmpty()) {
            CatalogFullFallbackResult.Failure(emptyList())
        } else {
            requireFromRankedSources(rankedSourceKeys)
        }
    }

    private suspend fun requireFromRankedSources(
        rankedSourceKeys: List<SourceKey>,
    ): CatalogFullFallbackResult {
        val failures = mutableListOf<CatalogFullAttemptFailure>()
        for (sourceKey in rankedSourceKeys) {
            when (
                val result = metadata.require(
                    CatalogMetadataKey(sourceKey.pluginId, sourceKey.sourceId),
                    CatalogMetadataLevel.Full,
                )
            ) {
                is CatalogMetadataResult.Ready -> {
                    val currentStoryId = identity.resolve(result.storyId)
                    return CatalogFullFallbackResult.Ready(
                        storyId = currentStoryId,
                        sourceKey = sourceKey,
                        entry = result.entry.copy(storyId = currentStoryId),
                    )
                }

                is CatalogMetadataResult.Failure -> failures += CatalogFullAttemptFailure(sourceKey, result.failure)
                CatalogMetadataResult.Missing -> failures += CatalogFullAttemptFailure(
                    sourceKey,
                    CatalogMetadataFailure.SourceFailure(MISSING_FULL_CODE, retryable = false),
                )
            }
        }
        return CatalogFullFallbackResult.Failure(failures)
    }

    private companion object {
        const val MISSING_FULL_CODE = "catalog.metadata.full_missing"
    }
}
