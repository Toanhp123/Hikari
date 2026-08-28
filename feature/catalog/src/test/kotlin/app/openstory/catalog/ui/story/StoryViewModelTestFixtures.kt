package app.openstory.catalog.ui.story

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalFieldContributor
import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.canonical.CanonicalFieldProvenance
import app.openstory.catalog.canonical.CanonicalFieldStrategy
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalSourceSummary
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.CanonicalIdentityState
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.catalog.orchestration.CatalogEvidenceChange
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.reconciliation.ReconciliationAssessment
import app.openstory.catalog.reconciliation.ReconciliationCase
import app.openstory.catalog.reconciliation.ReconciliationCaseKey
import app.openstory.catalog.reconciliation.ReconciliationCaseRepository
import app.openstory.catalog.reconciliation.ReconciliationCaseStatus
import app.openstory.catalog.reconciliation.ReconciliationMergeEligibility
import app.openstory.catalog.reconciliation.ReconciliationReasonCode
import app.openstory.catalog.reconciliation.ReconciliationResolutionOrigin
import app.openstory.catalog.reconciliation.ReconciliationSemanticDecision
import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryMappingScheduler
import app.openstory.library.LibraryRepository
import app.openstory.library.LibraryStatus
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal fun StoryUiState.requireStory(): StoryUiModel =
    (content as ContentState.Ready).value

internal class StoryViewModelIdentityRepository(
    private val canonical: FakeCanonicalRepository,
) : StoryIdentityRepository {
    override fun observeResolved(storyId: StoryId): Flow<StoryId> = flowOf(canonical.current().story.id)
    override suspend fun resolve(storyId: StoryId): StoryId = canonical.current().story.id
    override suspend fun identityState(storyId: StoryId): CanonicalIdentityState? = null
}

internal class RecordingStoryEngineEventSink : CanonicalEngineEventSink {
    val evidenceChanges = mutableListOf<CatalogEvidenceChange>()
    val preferenceChanges = mutableListOf<StoryId>()
    val merged = mutableListOf<StoryId>()
    var preferenceResult: CanonicalFusionResult? = null

    override suspend fun onEvidenceChanged(change: CatalogEvidenceChange) {
        evidenceChanges += change
    }

    override suspend fun onSourceLinked(storyId: StoryId, sourceKey: SourceKey) = Unit

    override suspend fun onSourceUnlinked(storyId: StoryId, sourceKey: SourceKey) = Unit

    override suspend fun onSourcePreferenceChanged(storyId: StoryId): CanonicalFusionResult {
        preferenceChanges += storyId
        return preferenceResult ?: CanonicalFusionResult.Preparing(storyId)
    }

    override suspend fun onStoryMerged(storyId: StoryId): CanonicalFusionResult {
        merged += storyId
        return CanonicalFusionResult.Preparing(storyId)
    }
}

internal class FakeCanonicalRepository(
    initial: CanonicalStoryState,
    aliases: Set<StoryId> = emptySet(),
    var observeFails: Boolean = false,
) : CanonicalCatalogRepository {
    private val state = MutableStateFlow<CanonicalStoryState?>(initial)
    private val acceptedIds = aliases + initial.story.id
    val requestId: StoryId = initial.story.id
    val observedIds = mutableListOf<StoryId>()
    val preferenceRequestStoryIds = mutableListOf<StoryId>()
    var observeAttempts: Int = 0
        private set

    fun current(): CanonicalStoryState = requireNotNull(state.value)

    fun emit(value: CanonicalStoryState?) {
        state.value = value
    }

    override fun observeStory(storyId: StoryId): Flow<CanonicalStoryState?> = flow {
        observedIds += storyId
        observeAttempts += 1
        if (observeFails) error("test canonical observation failure")
        if (storyId in acceptedIds) emitAll(state) else emit(null)
    }

    override fun observeReadyStories(): Flow<List<CanonicalStoryState.Ready>> = flowOf(emptyList())

    override suspend fun state(storyId: StoryId): CanonicalStoryState? =
        state.value.takeIf { storyId in acceptedIds }

    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = emptyList()

    override suspend fun activeGeneration(storyId: StoryId): CanonicalGeneration? =
        (state(storyId) as? CanonicalStoryState.Ready)?.generation

    override suspend fun sourcePreference(storyId: StoryId): CanonicalSourcePreference =
        requireNotNull(state(storyId)).preference

    override suspend fun setSourcePreference(preference: CanonicalSourcePreference) {
        preferenceRequestStoryIds += preference.storyId
        val current = current()
        val resolved = preference.copy(storyId = current.story.id)
        state.value = when (current) {
            is CanonicalStoryState.Preparing -> current.copy(preference = resolved)
            is CanonicalStoryState.Ready -> current.copy(preference = resolved)
        }
    }

    override suspend fun persistCandidate(
        candidate: CanonicalGeneration,
        expectedActiveGenerationId: String?,
    ): Boolean = false

    override suspend fun markHealth(storyId: StoryId, health: CanonicalHealth) = Unit
    override suspend fun cleanupObsoleteGenerations(storyId: StoryId) = Unit
}

internal class RecordingRebuilder(
    private val canonical: FakeCanonicalRepository,
    private val gate: CompletableDeferred<Unit>? = null,
    private val failure: Exception? = null,
) : CanonicalGenerationRebuilder {
    val reasons = mutableListOf<CanonicalFusionReason>()

    override suspend fun rebuild(storyId: StoryId, reason: CanonicalFusionReason): CanonicalFusionResult {
        reasons += reason
        gate?.await()
        failure?.let { throw it }
        val current = canonical.current()
        return when (current) {
            is CanonicalStoryState.Ready -> CanonicalFusionResult.Unchanged(current.generation)
            is CanonicalStoryState.Preparing -> CanonicalFusionResult.Preparing(current.story.id)
        }
    }
}

internal class EmptyCatalogRepository : CatalogRepository {
    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = flowOf(emptyList())
    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = flowOf(null)
    override suspend fun matchSnapshot(): CatalogMatchSnapshot = CatalogMatchSnapshot(emptyList())
    override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? = null
    override suspend fun sourceRecord(key: CatalogMetadataKey): CatalogSourceRecord? = null
    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = emptyList()
    override suspend fun sourceRecords(): List<CatalogSourceRecord> = emptyList()
    override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<app.openstory.catalog.repository.CatalogHomeCommitResult, CatalogStoreFailure> =
        Outcome.Success(app.openstory.catalog.repository.CatalogHomeCommitResult(emptyList()))

    override suspend fun commitSearchSummaries(
        mutation: app.openstory.catalog.repository.CatalogSearchSummaryMutation,
    ) = app.openstory.common.Outcome.Failure(
        app.openstory.catalog.CatalogStoreFailure("test.search.unsupported", retryable = false),
    )

    override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<app.openstory.catalog.repository.CatalogDetailsCommitResult, CatalogStoreFailure> =
        Outcome.Success(app.openstory.catalog.repository.CatalogDetailsCommitResult(mutation.storyId, emptyList()))
}

internal object EmptySourceRegistry : CatalogSourceRegistry {
    override suspend fun enabled(): List<CatalogSource> = emptyList()
    override suspend fun source(pluginId: PluginId): CatalogSource? = null
}

internal class FakeLibraryRepository : LibraryRepository {
    override fun observe(): Flow<List<LibraryEntry>> = flowOf(emptyList())
    override suspend fun add(storyId: StoryId, status: LibraryStatus, addedAt: Long): LibraryEntry =
        LibraryEntry(storyId, status, addedAt, addedAt)
    override suspend fun remove(storyId: StoryId) = Unit
    override suspend fun changeStatus(storyId: StoryId, status: LibraryStatus, updatedAt: Long): LibraryEntry? = null
}

internal object NoOpMappingScheduler : LibraryMappingScheduler {
    override fun schedule(storyId: StoryId) = Unit
}

internal class FakeProgressRepository : ReadingProgressRepository {
    override fun observeAll(): Flow<List<ReadingProgress>> = flowOf(emptyList())
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = flowOf(null)
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = null
    override suspend fun save(progress: ReadingProgress) = Unit
}

internal class MutableLibraryRepository(initial: List<LibraryEntry>) : LibraryRepository {
    private val entries = MutableStateFlow(initial)
    var mutationCalls: Int = 0
        private set
    val mutatedStoryIds = mutableListOf<StoryId>()

    override fun observe(): Flow<List<LibraryEntry>> = entries

    override suspend fun add(storyId: StoryId, status: LibraryStatus, addedAt: Long): LibraryEntry {
        mutationCalls += 1
        mutatedStoryIds += storyId
        val entry = LibraryEntry(storyId, status, addedAt, addedAt)
        entries.value = entries.value.filterNot { it.storyId == storyId } + entry
        return entry
    }

    override suspend fun remove(storyId: StoryId) {
        mutationCalls += 1
        mutatedStoryIds += storyId
        entries.value = entries.value.filterNot { it.storyId == storyId }
    }

    override suspend fun changeStatus(storyId: StoryId, status: LibraryStatus, updatedAt: Long): LibraryEntry? {
        mutationCalls += 1
        mutatedStoryIds += storyId
        val current = entries.value.firstOrNull { it.storyId == storyId } ?: return null
        val updated = current.copy(status = status, updatedAt = updatedAt)
        entries.value = entries.value.map { if (it.storyId == storyId) updated else it }
        return updated
    }
}

internal class NeverLibraryRepository : LibraryRepository {
    var mutationCalls: Int = 0
        private set

    override fun observe(): Flow<List<LibraryEntry>> = flow { awaitCancellation() }

    override suspend fun add(storyId: StoryId, status: LibraryStatus, addedAt: Long): LibraryEntry {
        mutationCalls += 1
        return LibraryEntry(storyId, status, addedAt, addedAt)
    }

    override suspend fun remove(storyId: StoryId) { mutationCalls += 1 }

    override suspend fun changeStatus(storyId: StoryId, status: LibraryStatus, updatedAt: Long): LibraryEntry? {
        mutationCalls += 1
        return null
    }
}

internal class MutableProgressRepository(initial: List<ReadingProgress>) : ReadingProgressRepository {
    private val records = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<ReadingProgress>> = records
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> =
        records.map { list -> list.firstOrNull { it.storyId == storyId && it.canonicalChapterId == chapterId } }
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? =
        records.value.firstOrNull { it.storyId == storyId && it.canonicalChapterId == chapterId }
    override suspend fun save(progress: ReadingProgress) {
        records.value = records.value.filterNot {
            it.storyId == progress.storyId && it.canonicalChapterId == progress.canonicalChapterId
        } + progress
    }
}

internal object NeverProgressRepository : ReadingProgressRepository {
    override fun observeAll(): Flow<List<ReadingProgress>> = flow { awaitCancellation() }
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = flow { awaitCancellation() }
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = null
    override suspend fun save(progress: ReadingProgress) = Unit
}

internal fun progressFor(storyId: StoryId): ReadingProgress = ReadingProgress(
    storyId = storyId,
    canonicalChapterId = CanonicalChapterId("chapter:resume"),
    releaseId = ChapterReleaseId("release:resume"),
    contentFingerprint = "fingerprint",
    position = ReadingPosition("block:1", 1, 0.1f),
    completedAtEpochMillis = null,
    updatedAtEpochMillis = 10L,
)

internal object EmptyStoryPromptCases : ReconciliationCaseRepository {
    override fun observePending(): Flow<List<ReconciliationCase>> = flowOf(emptyList())
    override fun observeForStory(storyId: StoryId): Flow<List<ReconciliationCase>> = flowOf(emptyList())
    override suspend fun find(caseId: String): ReconciliationCase? = null
    override suspend fun findActive(key: ReconciliationCaseKey): ReconciliationCase? = null
    override suspend fun recordAssessment(
        key: ReconciliationCaseKey,
        assessment: ReconciliationAssessment,
        evaluatedAtEpochMillis: Long,
    ): ReconciliationCase? = null
    override suspend fun resolveSeparate(
        caseId: String,
        expectedRevision: Long,
        origin: ReconciliationResolutionOrigin,
        resolvedAtEpochMillis: Long,
    ): Boolean = false
    override suspend fun defer(caseId: String, expectedRevision: Long, suppressUntilEpochMillis: Long): Boolean = false
}

internal object EmptyStoryPromptProjections : CatalogStoryProjectionRepository {
    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(emptyList())
}

internal object StoryPromptProjections : CatalogStoryProjectionRepository {
    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(
        listOf(
            CatalogStoryProjection(StoryId("story:1"), "Canonical title", ContentType.MANGA, null),
            CatalogStoryProjection(StoryId("story:2"), "Other canonical title", ContentType.MANGA, "other.jpg"),
        ),
    )
}

internal class StoryPromptCases(initial: List<ReconciliationCase>) : ReconciliationCaseRepository {
    private val cases = MutableStateFlow(initial)
    fun current(): List<ReconciliationCase> = cases.value

    override fun observePending(): Flow<List<ReconciliationCase>> = cases.map { list ->
        list.filter { it.status == ReconciliationCaseStatus.PENDING }
    }
    override fun observeForStory(storyId: StoryId): Flow<List<ReconciliationCase>> = cases.map { list ->
        list.filter { it.key.left == storyId || it.key.right == storyId }
    }
    override suspend fun find(caseId: String): ReconciliationCase? = cases.value.firstOrNull { it.id == caseId }
    override suspend fun findActive(key: ReconciliationCaseKey): ReconciliationCase? = cases.value.firstOrNull { it.key == key }
    override suspend fun recordAssessment(
        key: ReconciliationCaseKey,
        assessment: ReconciliationAssessment,
        evaluatedAtEpochMillis: Long,
    ): ReconciliationCase? = findActive(key)

    override suspend fun resolveSeparate(
        caseId: String,
        expectedRevision: Long,
        origin: ReconciliationResolutionOrigin,
        resolvedAtEpochMillis: Long,
    ): Boolean {
        val current = find(caseId) ?: return false
        if (current.revision != expectedRevision || current.status != ReconciliationCaseStatus.PENDING) return false
        cases.value = cases.value.map { item ->
            if (item.id == caseId) item.copy(
                status = ReconciliationCaseStatus.RESOLVED_SEPARATE,
                resolutionOrigin = origin,
                revision = item.revision + 1,
                lastEvaluatedAtEpochMillis = resolvedAtEpochMillis,
            ) else item
        }
        return true
    }

    override suspend fun defer(caseId: String, expectedRevision: Long, suppressUntilEpochMillis: Long): Boolean {
        val current = find(caseId) ?: return false
        if (current.revision != expectedRevision || current.status != ReconciliationCaseStatus.PENDING) return false
        cases.value = cases.value.map { item ->
            if (item.id == caseId) item.copy(contextualPromptSuppressedUntilEpochMillis = suppressUntilEpochMillis) else item
        }
        return true
    }
}

internal class StoryPromptMergeExecutor(private val result: StoryMergeResult) : StoryMergeExecutor {
    val requests = mutableListOf<StoryMergeRequest>()
    override suspend fun execute(request: StoryMergeRequest): StoryMergeResult {
        requests += request
        return result
    }
}

internal class MutableStoryClock(var now: Long) : Clock {
    override fun nowEpochMillis(): Long = now
}

internal fun promptCase(
    confidence: Double,
    revision: Long = 1,
    eligibility: ReconciliationMergeEligibility = ReconciliationMergeEligibility.MERGEABLE,
): ReconciliationCase {
    val fingerprint = "prompt-fingerprint"
    return ReconciliationCase(
        id = "prompt-case",
        key = ReconciliationCaseKey.of(StoryId("story:1"), StoryId("story:2")),
        status = ReconciliationCaseStatus.PENDING,
        assessment = ReconciliationAssessment(
            policyVersion = 1,
            semanticDecision = ReconciliationSemanticDecision.REVIEW,
            mergeEligibility = eligibility,
            confidence = confidence,
            titleSimilarity = confidence,
            authorSimilarity = null,
            winningLead = null,
            matchedIdentifiers = emptySet(),
            conflictingIdentifiers = emptySet(),
            reasons = setOf(ReconciliationReasonCode.TITLE_SIMILAR),
            identityEvidenceFingerprint = fingerprint,
        ),
        evidenceFingerprint = fingerprint,
        policyVersion = 1,
        resolutionOrigin = null,
        contextualPromptSuppressedUntilEpochMillis = null,
        revision = revision,
        createdAtEpochMillis = 1,
        lastEvaluatedAtEpochMillis = 2,
    )
}

internal fun preparingState(storyId: StoryId = StoryId("story:1")): CanonicalStoryState.Preparing {
    val sources = sourceSummaries(storyId)
    return CanonicalStoryState.Preparing(
        Story(storyId, ContentType.MANGA),
        CanonicalHealth.REEVALUATING,
        CanonicalSourcePreference(storyId, CanonicalSourcePreferenceMode.AUTO, null, 0),
        sources,
    )
}

internal fun readyState(storyId: StoryId = StoryId("story:1")): CanonicalStoryState.Ready {
    val preparing = preparingState(storyId)
    val primary = preparing.sources.first().sourceKey
    val generation = CanonicalGeneration(
        id = "gen:ready",
        storyId = storyId,
        fusionPolicyVersion = 1,
        primarySelectionPolicyVersion = 1,
        fusionFingerprint = "fusion:ready",
        effectivePrimary = primary,
        metadata = CanonicalMetadata(
            title = "Canonical title",
            description = "Canonical description",
            coverUrl = "canonical.jpg",
            sourceUrl = "canonical-url",
            popularityRank = null,
            aliases = listOf("Canonical alias"),
            authors = listOf("Canonical author"),
            genres = listOf("Fantasy"),
            languageTags = listOf("en"),
            publicationStatus = null,
            latestUpdate = null,
            score = null,
        ),
        health = CanonicalHealth.FRESH,
        provenance = mapOf(
            CanonicalFieldKey.TITLE to CanonicalFieldProvenance(
                CanonicalFieldKey.TITLE,
                CanonicalFieldStrategy.PRIMARY_WITH_FALLBACK,
                listOf(CanonicalFieldContributor(primary, "fusion:a", CatalogMetadataLevel.Full)),
                listOf("primary"),
                1,
            ),
        ),
        createdAtEpochMillis = 1,
    )
    return CanonicalStoryState.Ready(
        preparing.story,
        CanonicalHealth.FRESH,
        preparing.preference,
        preparing.sources,
        generation,
    )
}

internal fun sourceSummaries(storyId: StoryId): List<CanonicalSourceSummary> = listOf(
    sourceSummary(storyId, "catalog.b", "source-b", "Raw B"),
    sourceSummary(storyId, "catalog.a", "source-a", "Raw A"),
)

internal fun sourceSummary(
    storyId: StoryId,
    pluginId: String,
    sourceId: String,
    title: String,
): CanonicalSourceSummary {
    val plugin = PluginId(pluginId)
    return CanonicalSourceSummary(
        sourceKey = SourceKey(plugin, sourceId),
        entry = CatalogEntry(storyId, plugin, sourceId, title, contentType = ContentType.MANGA),
        summary = CatalogMetadataStamp("1.0.0", 1),
        full = CatalogMetadataStamp("1.0.0", 2),
        identityFingerprint = "identity:$pluginId:$sourceId",
        fusionFingerprint = "fusion:$pluginId:$sourceId",
    )
}
