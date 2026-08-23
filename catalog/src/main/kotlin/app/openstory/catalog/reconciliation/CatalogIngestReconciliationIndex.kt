package app.openstory.catalog.reconciliation

import app.openstory.catalog.identity.CatalogStoryIdFactory
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.Story
import app.openstory.common.id.StoryId

enum class IncomingSourceAction { DIRECT_OWNER, AUTO_LINK, CREATE_SEPARATE, CREATE_FOR_REVIEW }

sealed interface IncomingSourceResolution {
    val action: IncomingSourceAction

    data class Existing(
        val storyId: StoryId,
        override val action: IncomingSourceAction,
        val assessment: ReconciliationAssessment?,
    ) : IncomingSourceResolution

    data class Create(
        val story: Story,
        override val action: IncomingSourceAction,
        val reviewCandidateStoryId: StoryId?,
        val assessment: ReconciliationAssessment?,
    ) : IncomingSourceResolution
}

class CatalogIngestReconciliationIndex(
    private val engine: CatalogReconciliationEngine,
    private val storyIdFactory: CatalogStoryIdFactory,
    records: List<ReconciliationEvidence>,
) {
    private val recordsBySource = records.associateByTo(linkedMapOf(), ReconciliationEvidence::sourceKey)
    private val candidateIndex = InMemoryCatalogCandidateIndex().apply { rebuild(records) }

    fun resolve(incoming: ReconciliationEvidence): IncomingSourceResolution {
        recordsBySource[incoming.sourceKey]?.currentStoryId?.let { owner ->
            val registered = incoming.copy(currentStoryId = owner)
            register(registered)
            return IncomingSourceResolution.Existing(
                storyId = owner,
                action = IncomingSourceAction.DIRECT_OWNER,
                assessment = null,
            )
        }

        val candidateStoryIds = candidateIndex.candidatesFor(incoming).toSet()
        val candidateEvidence = candidateIndex.evidenceFor(candidateStoryIds)
        val selection = engine.rankCandidates(incoming, candidateEvidence)
        val best = selection.ranked.firstOrNull()
        val resolution = when {
            selection.semanticDecision == ReconciliationSemanticDecision.SAME_WORK &&
                selection.mergeEligibility == ReconciliationMergeEligibility.MERGEABLE && best != null ->
                IncomingSourceResolution.Existing(
                    storyId = best.storyId,
                    action = IncomingSourceAction.AUTO_LINK,
                    assessment = best.assessment.copy(
                        winningLead = selection.winningLead,
                        identityEvidenceFingerprint = requireNotNull(selection.identityEvidenceFingerprint),
                    ),
                )
            selection.semanticDecision == ReconciliationSemanticDecision.REVIEW && best != null -> create(
                incoming = incoming,
                action = IncomingSourceAction.CREATE_FOR_REVIEW,
                reviewCandidateStoryId = best.storyId,
                assessment = best.assessment.copy(
                    semanticDecision = ReconciliationSemanticDecision.REVIEW,
                    mergeEligibility = selection.mergeEligibility,
                    winningLead = selection.winningLead,
                    reasons = selection.reasons,
                    identityEvidenceFingerprint = requireNotNull(selection.identityEvidenceFingerprint),
                ),
            )
            else -> create(
                incoming = incoming,
                action = IncomingSourceAction.CREATE_SEPARATE,
                reviewCandidateStoryId = null,
                assessment = best?.assessment,
            )
        }
        when (resolution) {
            is IncomingSourceResolution.Existing -> register(incoming.copy(currentStoryId = resolution.storyId))
            is IncomingSourceResolution.Create -> register(incoming.copy(currentStoryId = resolution.story.id))
        }
        return resolution
    }

    fun applyDurableOwnership(sourceOwners: Map<SourceKey, StoryId>) {
        sourceOwners.entries
            .sortedBy { (key, _) -> "${key.pluginId.value}:${key.sourceId}" }
            .forEach { (key, storyId) ->
            val current = recordsBySource[key] ?: return@forEach
            register(current.copy(currentStoryId = storyId))
        }
    }

    fun fork(): CatalogIngestReconciliationIndex = CatalogIngestReconciliationIndex(
        engine = engine,
        storyIdFactory = storyIdFactory,
        records = recordsBySource.values.toList(),
    )

    private fun create(
        incoming: ReconciliationEvidence,
        action: IncomingSourceAction,
        reviewCandidateStoryId: StoryId?,
        assessment: ReconciliationAssessment?,
    ): IncomingSourceResolution.Create {
        val story = storyIdFactory.create(
            incoming,
            recordsBySource.values.mapNotNullTo(linkedSetOf(), ReconciliationEvidence::currentStoryId),
        )
        return IncomingSourceResolution.Create(story, action, reviewCandidateStoryId, assessment)
    }

    private fun register(record: ReconciliationEvidence) {
        recordsBySource[record.sourceKey] = record
        candidateIndex.upsert(record)
    }
}
