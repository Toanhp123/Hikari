package app.openstory.catalog.fusion

import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.identity.SourceKey
import app.openstory.common.Clock
import app.openstory.common.id.StoryId

private const val GENERATION_FINGERPRINT_PREFIX_LENGTH = 16

class CanonicalFusionService(
    private val canonical: CanonicalCatalogRepository,
    private val engine: CatalogFusionEngine,
    private val validator: CanonicalGenerationValidator,
    private val availability: CatalogSourceAvailabilityResolver,
    private val clock: Clock,
) : CanonicalGenerationRebuilder {
    override suspend fun rebuild(storyId: StoryId, reason: CanonicalFusionReason): CanonicalFusionResult {
        val state = canonical.state(storyId)
        return if (state == null) {
            CanonicalFusionResult.Failed(storyId, "canonical.story.missing", retryable = false)
        } else {
            val sources = canonical.sourceRecords(storyId)
            if (sources.isEmpty()) {
                canonical.markHealth(storyId, CanonicalHealth.DEGRADED)
                CanonicalFusionResult.Preparing(storyId)
            } else {
                buildAndPromote(state, sources.map { availability.resolve(it) }, retryPromotion = true)
            }
        }
    }

    private suspend fun buildAndPromote(
        state: CanonicalStoryState,
        sources: List<FusionSource>,
        retryPromotion: Boolean,
    ): CanonicalFusionResult {
        val storyId = state.story.id
        val active = canonical.activeGeneration(storyId)
        val preference = canonical.sourcePreference(storyId)
        val evaluatedAt = clock.nowEpochMillis()
        val candidate = engine.fuse(
            FusionInput(
                story = state.story,
                sources = sources,
                previousGeneration = active,
                preference = preference,
                evaluatedAtEpochMillis = evaluatedAt,
            ),
        )
        val owned = sources.mapTo(linkedSetOf(), FusionSource::sourceKey)
        val errors = validator.validate(state.story, owned, candidate)
        return when {
            errors.isNotEmpty() -> {
                canonical.markHealth(storyId, CanonicalHealth.DEGRADED)
                CanonicalFusionResult.Failed(
                    storyId,
                    "canonical.generation.invalid:${errors.sorted().joinToString(",")}",
                    retryable = false,
                )
            }

            active != null && candidate.meaningfullyEquals(active) -> {
                if (state.health != active.health) {
                    canonical.markHealth(storyId, active.health)
                }
                CanonicalFusionResult.Unchanged(active)
            }

            else -> persistOrRetry(state, sources, active, candidate, retryPromotion)
        }
    }

    private suspend fun persistOrRetry(
        state: CanonicalStoryState,
        sources: List<FusionSource>,
        active: CanonicalGeneration?,
        candidate: CanonicalGenerationCandidate,
        retryPromotion: Boolean,
    ): CanonicalFusionResult {
        val storyId = state.story.id
        val generation = candidate.toGeneration(generationId(candidate))
        return when {
            canonical.persistCandidate(generation, active?.id) -> {
                canonical.cleanupObsoleteGenerations(storyId)
                CanonicalFusionResult.Promoted(generation)
            }

            !retryPromotion -> CanonicalFusionResult.Failed(
                storyId,
                "canonical.promotion.race",
                retryable = true,
            )

            else -> canonical.state(storyId)?.let { reread ->
                buildAndPromote(reread, sources, retryPromotion = false)
            } ?: CanonicalFusionResult.Failed(
                storyId,
                "canonical.story.disappeared",
                retryable = true,
            )
        }
    }

    private fun generationId(candidate: CanonicalGenerationCandidate): String =
        "gen:${candidate.storyId.value}:${candidate.createdAtEpochMillis}:" +
            candidate.fusionFingerprint.take(GENERATION_FINGERPRINT_PREFIX_LENGTH)
}

private fun CanonicalGenerationCandidate.toGeneration(id: String) = CanonicalGeneration(
    id = id,
    storyId = storyId,
    fusionPolicyVersion = fusionPolicyVersion,
    primarySelectionPolicyVersion = primarySelectionPolicyVersion,
    fusionFingerprint = fusionFingerprint,
    effectivePrimary = effectivePrimary,
    metadata = metadata,
    health = health,
    provenance = provenance,
    createdAtEpochMillis = createdAtEpochMillis,
)

private fun CanonicalGenerationCandidate.meaningfullyEquals(active: CanonicalGeneration): Boolean =
    fusionPolicyVersion == active.fusionPolicyVersion &&
        primarySelectionPolicyVersion == active.primarySelectionPolicyVersion &&
        effectivePrimary == active.effectivePrimary &&
        metadata == active.metadata &&
        health == active.health &&
        provenance == active.provenance
