package app.openstory.catalog

import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.identity.CanonicalIdentityState
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.reconciliation.CatalogReconciliationEngine
import app.openstory.catalog.reconciliation.CatalogReconciliationService
import app.openstory.catalog.reconciliation.InMemoryCatalogCandidateIndex
import app.openstory.catalog.reconciliation.ReconciliationCase
import app.openstory.catalog.reconciliation.ReconciliationCaseKey
import app.openstory.catalog.reconciliation.ReconciliationCaseRepository
import app.openstory.catalog.reconciliation.ReconciliationPolicy
import app.openstory.catalog.reconciliation.ReconciliationResolutionOrigin
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal fun featureTestReconciliationService(
    repository: CatalogRepository,
    clock: Clock,
): CatalogReconciliationService = CatalogReconciliationService(
    catalog = repository,
    identity = FeaturePassthroughStoryIdentityRepository,
    candidateIndex = InMemoryCatalogCandidateIndex(),
    engine = CatalogReconciliationEngine(ReconciliationPolicy()),
    cases = FeatureNoOpReconciliationCaseRepository,
    clock = clock,
)

internal val featureNoOpCanonicalRebuilder = CanonicalGenerationRebuilder { storyId, _ ->
    CanonicalFusionResult.Preparing(storyId)
}

private object FeaturePassthroughStoryIdentityRepository : StoryIdentityRepository {
    override fun observeResolved(storyId: StoryId): Flow<StoryId> = flowOf(storyId)
    override suspend fun resolve(storyId: StoryId): StoryId = storyId
    override suspend fun identityState(storyId: StoryId): CanonicalIdentityState? = null
}

private object FeatureNoOpReconciliationCaseRepository : ReconciliationCaseRepository {
    override fun observePending(): Flow<List<ReconciliationCase>> = flowOf(emptyList())
    override fun observeForStory(storyId: StoryId): Flow<List<ReconciliationCase>> = flowOf(emptyList())
    override suspend fun find(caseId: String): ReconciliationCase? = null
    override suspend fun findActive(key: ReconciliationCaseKey): ReconciliationCase? = null
    override suspend fun recordAssessment(
        key: ReconciliationCaseKey,
        assessment: app.openstory.catalog.reconciliation.ReconciliationAssessment,
        evaluatedAtEpochMillis: Long,
    ): ReconciliationCase? = null
    override suspend fun resolveSeparate(
        caseId: String,
        expectedRevision: Long,
        origin: ReconciliationResolutionOrigin,
        resolvedAtEpochMillis: Long,
    ): Boolean = false
    override suspend fun defer(
        caseId: String,
        expectedRevision: Long,
        suppressUntilEpochMillis: Long,
    ): Boolean = false
}
