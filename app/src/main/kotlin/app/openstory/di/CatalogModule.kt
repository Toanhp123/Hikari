package app.openstory.di

import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.diagnostics.CanonicalDiagnostics
import app.openstory.catalog.fusion.CanonicalFusionService
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.fusion.CanonicalGenerationValidator
import app.openstory.catalog.fusion.CatalogFusionEngine
import app.openstory.catalog.fusion.CatalogSourceAvailabilityResolver
import app.openstory.catalog.identity.CatalogStoryIdFactory
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.reconciliation.CatalogCandidateIndex
import app.openstory.catalog.reconciliation.CatalogReconciliationEngine
import app.openstory.catalog.reconciliation.CatalogReconciliationMaintenance
import app.openstory.catalog.reconciliation.CatalogReconciliationMaintenanceService
import app.openstory.catalog.reconciliation.CatalogReconciliationService
import app.openstory.catalog.reconciliation.InMemoryCatalogCandidateIndex
import app.openstory.catalog.reconciliation.ReconciliationCaseRepository
import app.openstory.catalog.reconciliation.ReconciliationExecutionMode
import app.openstory.catalog.reconciliation.ReconciliationPolicy
import app.openstory.catalog.reconciliation.StoryMergeLineageReader
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.metadata.CatalogMetadataPolicy
import app.openstory.catalog.orchestration.CanonicalEngineWorkRepository
import app.openstory.catalog.orchestration.CanonicalMaintenanceHealthMarker
import app.openstory.catalog.ranking.AggregateRanking
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.PluginCatalogSourceRegistry
import app.openstory.common.Clock
import app.openstory.common.SystemClock
import app.openstory.plugins.runtime.PluginRuntime
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    fun provideClock(): Clock = SystemClock

    @Provides
    fun provideReconciliationPolicy(): ReconciliationPolicy = ReconciliationPolicy()

    @Provides
    fun provideReconciliationExecutionMode(): ReconciliationExecutionMode =
        ReconciliationExecutionMode.APPLY_ELIGIBLE_AUTO_MERGES

    @Provides
    @Singleton
    fun provideCatalogCandidateIndex(): CatalogCandidateIndex = InMemoryCatalogCandidateIndex()

    @Provides
    fun provideCatalogReconciliationEngine(
        policy: ReconciliationPolicy,
    ): CatalogReconciliationEngine = CatalogReconciliationEngine(policy)

    @Provides
    fun provideCatalogStoryIdFactory(): CatalogStoryIdFactory = CatalogStoryIdFactory()

    @Provides
    @Singleton
    fun provideCatalogReconciliationService(
        catalog: CatalogRepository,
        identity: StoryIdentityRepository,
        candidateIndex: CatalogCandidateIndex,
        engine: CatalogReconciliationEngine,
        cases: ReconciliationCaseRepository,
        clock: Clock,
        executionMode: ReconciliationExecutionMode,
        mergeExecutor: StoryMergeExecutor,
        work: CanonicalEngineWorkRepository,
        lineageReader: StoryMergeLineageReader,
        diagnostics: CanonicalDiagnostics,
    ): CatalogReconciliationService = CatalogReconciliationService(
        catalog = catalog,
        identity = identity,
        candidateIndex = candidateIndex,
        engine = engine,
        cases = cases,
        clock = clock,
        executionMode = executionMode,
        mergeExecutor = mergeExecutor,
        work = work,
        lineageReader = lineageReader,
        diagnostics = diagnostics,
    )

    @Provides
    fun provideCatalogReconciliationMaintenance(
        service: CatalogReconciliationService,
        catalog: CatalogRepository,
        identity: StoryIdentityRepository,
        engine: CatalogReconciliationEngine,
    ): CatalogReconciliationMaintenance = CatalogReconciliationMaintenanceService(
        reconciliation = service,
        catalog = catalog,
        identity = identity,
        engine = engine,
    )

    @Provides
    fun provideAggregateRanking(): AggregateRanking = AggregateRanking()

    @Provides
    fun provideCatalogSourceAvailabilityResolver(
        registry: CatalogSourceRegistry,
        metadataPolicy: CatalogMetadataPolicy,
    ): CatalogSourceAvailabilityResolver = CatalogSourceAvailabilityResolver(registry, metadataPolicy)

    @Provides
    fun provideCatalogFusionEngine(): CatalogFusionEngine = CatalogFusionEngine()

    @Provides
    fun provideCanonicalGenerationValidator(): CanonicalGenerationValidator = CanonicalGenerationValidator()

    @Provides
    @Singleton
    fun provideCanonicalFusionService(
        canonical: CanonicalCatalogRepository,
        engine: CatalogFusionEngine,
        validator: CanonicalGenerationValidator,
        availability: CatalogSourceAvailabilityResolver,
        clock: Clock,
        diagnostics: CanonicalDiagnostics,
    ): CanonicalFusionService = CanonicalFusionService(
        canonical,
        engine,
        validator,
        availability,
        clock,
        diagnostics,
    )

    @Provides
    fun provideCanonicalGenerationRebuilder(service: CanonicalFusionService): CanonicalGenerationRebuilder = service

    @Provides
    fun provideCanonicalMaintenanceHealthMarker(
        canonical: CanonicalCatalogRepository,
    ): CanonicalMaintenanceHealthMarker = object : CanonicalMaintenanceHealthMarker {
        override suspend fun markDegraded(storyId: app.openstory.common.id.StoryId) {
            canonical.markHealth(storyId, CanonicalHealth.DEGRADED)
        }
    }

    @Provides
    fun provideCanonicalBootstrapUseCase(
        canonical: CanonicalCatalogRepository,
        rebuilder: CanonicalGenerationRebuilder,
    ): CanonicalBootstrapUseCase = CanonicalBootstrapUseCase(canonical, rebuilder)

    @Provides
    @Singleton
    fun provideCatalogSourceRegistry(
        runtime: PluginRuntime,
        json: Json,
    ): CatalogSourceRegistry = PluginCatalogSourceRegistry(runtime, json)
}
