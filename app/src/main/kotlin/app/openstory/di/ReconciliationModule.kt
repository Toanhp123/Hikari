package app.openstory.di

import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.catalog.orchestration.CanonicalEngineOrchestrator
import app.openstory.catalog.reconciliation.CatalogReconciliationRunner
import app.openstory.catalog.reconciliation.CatalogReconciliationService
import app.openstory.catalog.reconciliation.ReconciliationCaseRepository
import app.openstory.catalog.reconciliation.ReconciliationReviewService
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
    ): ReconciliationReviewService = ReconciliationReviewService(cases, mergeExecutor, clock, orchestrator)
}
