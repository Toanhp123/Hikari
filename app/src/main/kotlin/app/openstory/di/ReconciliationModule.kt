package app.openstory.di

import app.openstory.catalog.identity.StoryMergeExecutor
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
    @Singleton
    fun provideReconciliationReviewService(
        cases: ReconciliationCaseRepository,
        mergeExecutor: StoryMergeExecutor,
        clock: Clock,
    ): ReconciliationReviewService = ReconciliationReviewService(cases, mergeExecutor, clock)
}
