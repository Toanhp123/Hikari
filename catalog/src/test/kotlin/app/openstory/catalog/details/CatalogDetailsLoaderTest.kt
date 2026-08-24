package app.openstory.catalog.details

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.RecordingCanonicalEngineEventSink
import app.openstory.catalog.orchestration.CatalogEvidenceLevel
import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.metadata.CatalogMetadataFailure
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Story
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceFailure
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceLatestUpdate
import app.openstory.catalog.source.SourcePublicationStatus
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.catalog.evidence.toSourceRecord
import app.openstory.catalog.reconciliation.CatalogReconciliationEngine
import app.openstory.catalog.reconciliation.ReconciliationPolicy
import app.openstory.catalog.repository.CatalogCommitChange
import app.openstory.catalog.repository.CatalogDetailsCommitResult
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CatalogDetailsLoaderTest {
    @Test
    fun sourceUnavailableReturnsTypedFailureWithoutAttemptedVersion() = runTest {
        val repository = FakeRepository()
        val loader = loader(Registry(null), repository)

        val result = loader.load(CatalogMetadataKey(PluginId("a"), "source"))

        val failure = assertIs<CatalogDetailsLoadResult.Failure>(result)
        assertEquals(CatalogMetadataFailure.SourceUnavailable(PluginId("a")), failure.failure)
        assertEquals(null, failure.attemptedPluginVersion)
    }

    @Test
    fun sourceExceptionIsRetryableAndRetainsAttemptedVersion() = runTest {
        val repository = FakeRepository()
        val source = Source("a", detailsResult = { throw IllegalStateException("boom") })

        val result = loader(Registry(source), repository)
            .load(CatalogMetadataKey(PluginId("a"), "source"))

        val failure = assertIs<CatalogDetailsLoadResult.Failure>(result)
        assertEquals(CatalogMetadataFailure.SourceFailure("catalog.source.exception", true), failure.failure)
        assertEquals("1.0.0", failure.attemptedPluginVersion)
        assertEquals(0, repository.detailCommits)
    }

    @Test
    fun sourceReturnedFailurePreservesCodeAndRetryable() = runTest {
        val repository = FakeRepository()
        val source = Source(
            "a",
            detailsResult = {
                CatalogSourceResult.Failure(CatalogSourceFailure("source.denied", retryable = false))
            },
        )

        val result = loader(Registry(source), repository)
            .load(CatalogMetadataKey(PluginId("a"), "source"))

        val failure = assertIs<CatalogDetailsLoadResult.Failure>(result)
        assertEquals(CatalogMetadataFailure.SourceFailure("source.denied", false), failure.failure)
        assertEquals("1.0.0", failure.attemptedPluginVersion)
    }

    @Test
    fun sourceIdMismatchFailsWithoutPersistence() = runTest {
        val repository = FakeRepository()
        val source = Source("a", details("different"))

        val result = loader(Registry(source), repository)
            .load(CatalogMetadataKey(PluginId("a"), "requested"))

        val failure = assertIs<CatalogDetailsLoadResult.Failure>(result)
        assertEquals(CatalogMetadataFailure.SourceIdMismatch("requested", "different"), failure.failure)
        assertEquals("1.0.0", failure.attemptedPluginVersion)
        assertEquals(0, repository.detailCommits)
    }

    @Test
    fun existingSourceReferenceSkipsMatchingAndKeepsPersistedProposal() = runTest {
        val persistedStoryId = StoryId("story:persisted")
        val repository = FakeRepository(
            persisted = snapshot(persistedStoryId),
            durableStoryId = persistedStoryId,
        )
        val source = Source("a", details("source"))

        val result = loader(Registry(source), repository)
            .load(CatalogMetadataKey(PluginId("a"), "source"))

        val success = assertIs<CatalogDetailsLoadResult.Success>(result)
        assertEquals(persistedStoryId, success.storyId)
        assertEquals(persistedStoryId, repository.lastMutation?.storyId)
        assertEquals(0, repository.matchSnapshotCalls)
    }

    @Test
    fun newSourceReferenceUsesReconciliationIndexBeforePersistence() = runTest {
        val matchedStoryId = StoryId("story:matched")
        val repository = FakeRepository(
            records = listOf(sourceRecord(matchedStoryId, "other", "other-source", "Title", setOf("Author"))),
            durableStoryId = matchedStoryId,
        )
        val source = Source("a", details("source"))

        val result = loader(Registry(source), repository)
            .load(CatalogMetadataKey(PluginId("a"), "source"))

        val success = assertIs<CatalogDetailsLoadResult.Success>(result)
        assertEquals(matchedStoryId, success.storyId)
        assertEquals(0, repository.matchSnapshotCalls)
        assertEquals(1, repository.globalSourceRecordCalls)
        assertEquals(matchedStoryId, repository.lastMutation?.storyId)
    }

    @Test
    fun commitReturnedStoryIdBecomesDurableLoaderResult() = runTest {
        val durableStoryId = StoryId("story:durable")
        val repository = FakeRepository(durableStoryId = durableStoryId)
        val source = Source("a", details("source"))

        val result = loader(Registry(source), repository)
            .load(CatalogMetadataKey(PluginId("a"), "source"))

        val success = assertIs<CatalogDetailsLoadResult.Success>(result)
        assertEquals(durableStoryId, success.storyId)
        assertEquals(durableStoryId, success.entry.storyId)
        assertEquals("1.0.0", success.pluginVersion)
        assertEquals(42L, success.resolvedAtEpochMillis)
    }

    @Test
    fun storeFailureBecomesTypedFailureWithAttemptedVersion() = runTest {
        val repository = FakeRepository(storeFailure = CatalogStoreFailure("store.down", true))
        val source = Source("a", details("source"))

        val result = loader(Registry(source), repository)
            .load(CatalogMetadataKey(PluginId("a"), "source"))

        val failure = assertIs<CatalogDetailsLoadResult.Failure>(result)
        assertEquals(CatalogMetadataFailure.StoreFailure("store.down", true), failure.failure)
        assertEquals("1.0.0", failure.attemptedPluginVersion)
    }

    @Test
    fun richerFullMetadataRoutesPersistedIdentityChangeThroughFullEvidenceEvent() = runTest {
        val leftStory = StoryId("story:left")
        val rightStory = StoryId("story:right")
        val leftKey = SourceKey(PluginId("a"), "source")
        val rightRecord = sourceRecord(rightStory, "b", "other", "Title", setOf("Author"))
        val repository = RetroactiveRepository(snapshot(leftStory), rightRecord)
        val engine = RecordingCanonicalEngineEventSink()
        val source = Source("a", details("source"))

        val result = loader(Registry(source), repository, engine)
            .load(CatalogMetadataKey(leftKey.pluginId, leftKey.sourceId))

        val success = assertIs<CatalogDetailsLoadResult.Success>(result)
        assertEquals(leftStory, success.storyId)
        val change = engine.evidenceChanges.single()
        assertEquals(leftStory, change.storyId)
        assertEquals(leftKey, change.sourceKey)
        assertEquals(CatalogEvidenceLevel.FULL, change.level)
        assertEquals(true, change.identityFingerprintChanged)
        assertEquals(leftStory, repository.lastMutation?.storyId)
    }

    @Test
    fun normalizedDetailsCarryPublicationStatusAndLatestUpdate() = runTest {
        val repository = FakeRepository()
        val source = Source(
            "a",
            details("source").copy(
                publicationStatus = SourcePublicationStatus.COMPLETED,
                latestUpdate = SourceLatestUpdate(700L, "200"),
            ),
        )

        loader(Registry(source), repository)
            .load(CatalogMetadataKey(PluginId("a"), "source"))

        val entry = requireNotNull(repository.lastMutation).entry
        assertEquals(PublicationStatus.COMPLETED, entry.publicationStatus)
        assertEquals(CatalogLatestUpdate(700L, "200"), entry.latestUpdate)
    }

    @Test
    fun normalizedDetailsCarryExternalIdentifiersIntoCatalogEntry() = runTest {
        val repository = FakeRepository()
        val identifier = ExternalIdentifier(
            namespace = "openlibrary.work",
            value = "OL123W",
            scope = ExternalIdentifierScope.WORK,
        )
        val source = Source(
            "a",
            details("source").copy(externalIdentifiers = setOf(identifier)),
        )

        loader(Registry(source), repository)
            .load(CatalogMetadataKey(PluginId("a"), "source"))

        val entry = requireNotNull(repository.lastMutation).entry
        assertEquals(setOf(identifier), entry.externalIdentifiers)
    }

    private fun loader(
        registry: CatalogSourceRegistry,
        repository: CatalogRepository,
        engine: RecordingCanonicalEngineEventSink = RecordingCanonicalEngineEventSink(),
    ): CatalogDetailsLoader {
        val clock = Clock { 42L }
        return CatalogDetailsLoader(
            sources = registry,
            repository = repository,
            reconciliationEngine = app.openstory.catalog.reconciliation.CatalogReconciliationEngine(
                app.openstory.catalog.reconciliation.ReconciliationPolicy(),
            ),
            storyIdFactory = app.openstory.catalog.identity.CatalogStoryIdFactory(),
            orchestrator = engine,
            clock = clock,
        )
    }

    private fun details(sourceId: String) = SourceDetails(
        sourceId = sourceId,
        sourceUrl = "url",
        title = "Title",
        aliases = setOf("Alias"),
        authors = setOf("Author"),
        description = "Description",
        genres = setOf("Drama"),
        contentType = SourceContentType.MANGA,
        languageTags = setOf("en"),
        coverUrl = null,
        scoreValue = null,
        scoreScale = null,
        popularityRank = 4,
    )

    private fun snapshot(storyId: StoryId) = CatalogMetadataSnapshot(
        entry = CatalogEntry(
            storyId = storyId,
            pluginId = PluginId("a"),
            sourceId = "source",
            title = "Persisted",
            contentType = ContentType.MANGA,
        ),
        summary = CatalogMetadataStamp("1.0.0", 1),
        full = null,
    )

    private fun sourceRecord(
        storyId: StoryId,
        pluginId: String,
        sourceId: String,
        title: String,
        authors: Set<String>,
    ): CatalogSourceRecord {
        val entry = CatalogEntry(
            storyId = storyId,
            pluginId = PluginId(pluginId),
            sourceId = sourceId,
            title = title,
            authors = authors,
            contentType = ContentType.MANGA,
        )
        return CatalogSourceRecord(
            key = SourceKey(entry.pluginId, entry.sourceId),
            storyId = storyId,
            entry = entry,
            summary = CatalogMetadataStamp("1.0.0", 1),
            full = null,
            identityFingerprint = "identity:${pluginId}:${sourceId}:${title}:${authors.sorted()}",
            fusionFingerprint = "fusion:${pluginId}:${sourceId}",
        )
    }


    private class Registry(private val source: CatalogSource?) : CatalogSourceRegistry {
        override suspend fun enabled(): List<CatalogSource> = listOfNotNull(source)
        override suspend fun source(pluginId: PluginId): CatalogSource? = source?.takeIf { it.pluginId == pluginId }
    }

    private open class Source(
        id: String,
        private val detailsResult: suspend (String) -> CatalogSourceResult<SourceDetails>,
    ) : CatalogSource {
        constructor(id: String, details: SourceDetails) : this(
            id,
            detailsResult = { CatalogSourceResult.Success(details) },
        )

        override val pluginId = PluginId(id)
        override val version = "1.0.0"
        override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> = error("unused")
        override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> = error("unused")
        override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> = detailsResult(sourceId)
        override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = error("unused")
    }

    private class RetroactiveRepository(
        private val persisted: CatalogMetadataSnapshot,
        candidate: CatalogSourceRecord,
    ) : CatalogRepository {
        private val records = linkedMapOf<SourceKey, CatalogSourceRecord>(candidate.key to candidate)
        var lastMutation: CatalogDetailsMutation? = null

        init {
            val initial = persisted.toSourceRecord()
            records[initial.key] = initial
        }

        override fun observeHomes() = emptyFlow<List<CatalogHomeSnapshot>>()
        override fun observeStory(storyId: StoryId) = emptyFlow<StoryCatalogSnapshot?>()
        override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())
        override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? =
            persisted.takeIf { it.entry.pluginId == key.pluginId && it.entry.sourceId == key.sourceId }
        override suspend fun sourceRecord(key: CatalogMetadataKey): CatalogSourceRecord? =
            records[SourceKey(key.pluginId, key.sourceId)]
        override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> =
            records.values.filter { it.storyId == storyId }
        override suspend fun sourceRecords(): List<CatalogSourceRecord> = records.values.toList()
        override suspend fun commitHomeRefresh(
            mutation: CatalogHomeMutation,
        ): Outcome<app.openstory.catalog.repository.CatalogHomeCommitResult, CatalogStoreFailure> =
            Outcome.Success(app.openstory.catalog.repository.CatalogHomeCommitResult(emptyList()))
        override suspend fun commitSearchSummaries(
            mutation: app.openstory.catalog.repository.CatalogSearchSummaryMutation,
        ) = Outcome.Failure(CatalogStoreFailure("unsupported", false))
        override suspend fun commitDetails(
            mutation: CatalogDetailsMutation,
        ): Outcome<CatalogDetailsCommitResult, CatalogStoreFailure> {
            lastMutation = mutation
            val key = SourceKey(mutation.entry.pluginId, mutation.entry.sourceId)
            val before = records[key]
            val snapshot = CatalogMetadataSnapshot(
                entry = mutation.entry.copy(storyId = persisted.entry.storyId),
                summary = CatalogMetadataStamp(mutation.pluginVersion, mutation.resolvedAtEpochMillis),
                full = CatalogMetadataStamp(mutation.pluginVersion, mutation.resolvedAtEpochMillis),
            )
            val after = snapshot.toSourceRecord()
            records[key] = after
            return Outcome.Success(
                CatalogDetailsCommitResult(
                    storyId = persisted.entry.storyId,
                    changes = listOf(
                        CatalogCommitChange(
                            storyId = persisted.entry.storyId,
                            sourceKey = key,
                            identityFingerprintChanged = before?.identityFingerprint != after.identityFingerprint,
                            fusionFingerprintChanged = true,
                        ),
                    ),
                ),
            )
        }
    }

    private class FakeRepository(
        private val persisted: CatalogMetadataSnapshot? = null,
        private val records: List<CatalogSourceRecord> = emptyList(),
        private val durableStoryId: StoryId? = null,
        private val storeFailure: CatalogStoreFailure? = null,
    ) : CatalogRepository {
        var detailCommits = 0
        var matchSnapshotCalls = 0
        var globalSourceRecordCalls = 0
        var lastMutation: CatalogDetailsMutation? = null

        override fun observeHomes() = emptyFlow<List<CatalogHomeSnapshot>>()
        override fun observeStory(storyId: StoryId) = emptyFlow<StoryCatalogSnapshot?>()
        override suspend fun matchSnapshot(): CatalogMatchSnapshot {
            matchSnapshotCalls++
            return CatalogMatchSnapshot(emptyList())
        }
        override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? = persisted
        override suspend fun sourceRecord(key: CatalogMetadataKey): app.openstory.catalog.evidence.CatalogSourceRecord? = null

        override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> =
            records.filter { it.storyId == storyId }

        override suspend fun sourceRecords(): List<CatalogSourceRecord> {
            globalSourceRecordCalls++
            return records
        }

        override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<app.openstory.catalog.repository.CatalogHomeCommitResult, CatalogStoreFailure> =
            Outcome.Success(app.openstory.catalog.repository.CatalogHomeCommitResult(emptyList()))


        override suspend fun commitSearchSummaries(
            mutation: app.openstory.catalog.repository.CatalogSearchSummaryMutation,
        ) = app.openstory.common.Outcome.Failure(
            app.openstory.catalog.CatalogStoreFailure("test.search.unsupported", retryable = false),
        )

        override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<app.openstory.catalog.repository.CatalogDetailsCommitResult, CatalogStoreFailure> {
            detailCommits++
            lastMutation = mutation
            return storeFailure?.let { Outcome.Failure(it) }
                ?: Outcome.Success(
                    app.openstory.catalog.repository.CatalogDetailsCommitResult(
                        durableStoryId ?: mutation.storyId,
                        emptyList(),
                    ),
                )
        }
    }
}
