package app.openstory.catalog

import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.catalog.orchestration.CatalogEvidenceChange
import app.openstory.common.id.StoryId

internal class RecordingCanonicalEngineEventSink : CanonicalEngineEventSink {
    val evidenceChanges = mutableListOf<CatalogEvidenceChange>()
    val linked = mutableListOf<Pair<StoryId, SourceKey>>()
    val unlinked = mutableListOf<Pair<StoryId, SourceKey>>()
    val preferenceChanges = mutableListOf<StoryId>()
    val merged = mutableListOf<StoryId>()
    var preferenceResult: CanonicalFusionResult? = null
    var mergeResult: CanonicalFusionResult? = null

    override suspend fun onEvidenceChanged(change: CatalogEvidenceChange) {
        evidenceChanges += change
    }

    override suspend fun onSourceLinked(storyId: StoryId, sourceKey: SourceKey) {
        linked += storyId to sourceKey
    }

    override suspend fun onSourceUnlinked(storyId: StoryId, sourceKey: SourceKey) {
        unlinked += storyId to sourceKey
    }

    override suspend fun onSourcePreferenceChanged(storyId: StoryId): CanonicalFusionResult {
        preferenceChanges += storyId
        return preferenceResult ?: CanonicalFusionResult.Preparing(storyId)
    }

    override suspend fun onStoryMerged(storyId: StoryId): CanonicalFusionResult {
        merged += storyId
        return mergeResult ?: CanonicalFusionResult.Preparing(storyId)
    }
}
