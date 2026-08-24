package app.openstory.di

import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.identity.StoryMergeReversalExecutor
import app.openstory.catalog.identity.StoryMergeReversalPlanner
import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.catalog.orchestration.CanonicalEngineOrchestrator
import app.openstory.catalog.reconciliation.CatalogReconciliationRunner
import app.openstory.catalog.reconciliation.CatalogReconciliationService
import app.openstory.catalog.reconciliation.ReconciliationCaseRepository
import app.openstory.catalog.reconciliation.ReconciliationReviewService
import app.openstory.catalog.reconciliation.StoryMergeLineageReader
import app.openstory.common.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReconciliationModule {
    @Provides
    fun provideCatalogReconciliationRunner(
        service: CatalogReconciliationService,
    ): CatalogReconciliationRunner = service

    @Provides
    fun provideCanonicalEngineEventSink(
        orchestrator: CanonicalEngineOrchestrator,
    ): CanonicalEngineEventSink = orchestrator

    @Provides
    @Singleton
    fun provideReconciliationReviewService(
        cases: ReconciliationCaseRepository,
        mergeExecutor: StoryMergeExecutor,
        clock: Clock,
        orchestrator: CanonicalEngineEventSink,
        reversalPlanner: StoryMergeReversalPlanner,
        reversalExecutor: StoryMergeReversalExecutor,
        identity: StoryIdentityRepository,
        lineages: StoryMergeLineageReader,
    ): ReconciliationReviewService = ReconciliationReviewService(
        cases = cases,
        mergeExecutor = mergeExecutor,
        clock = clock,
        orchestrator = orchestrator,
        reversalPlanner = reversalPlanner,
        reversalExecutor = reversalExecutor,
        identity = identity,
        lineages = lineages,
    )
}
