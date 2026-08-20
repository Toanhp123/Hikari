package app.openstory.catalog.metadata

import kotlinx.coroutines.ExperimentalCoroutinesApi
import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.details.CatalogDetailsLoader
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
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
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogMetadataCoordinatorTest {
    @Test
    fun summaryCachedReturnsReadyWithoutDetails() = runTest {
        val fixture = fixture(snapshot = snapshot(fullAt = null, artworkAt = null), scope = backgroundScope)

        val result = fixture.coordinator.require(KEY, CatalogMetadataLevel.Summary)

        assertIs<CatalogMetadataResult.Ready>(result)
        assertEquals(0, fixture.source.detailsCalls)
    }

    @Test
    fun summaryMissingReturnsMissing() = runTest {
        val fixture = fixture(snapshot = null, scope = backgroundScope)

        assertEquals(CatalogMetadataResult.Missing, fixture.coordinator.require(KEY, CatalogMetadataLevel.Summary))
        assertEquals(0, fixture.source.detailsCalls)
    }

    @Test
    fun freshArtworkAndFullReturnCacheWithoutDetails() = runTest {
        val fixture = fixture(snapshot = snapshot(fullAt = NOW, artworkAt = NOW), scope = backgroundScope)

        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Artwork))
        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        runCurrent()

        assertEquals(0, fixture.source.detailsCalls)
    }

    @Test
    fun unresolvedArtworkAndFullAwaitDetailsAndBecomeReady() = runTest {
        val artworkFixture = fixture(snapshot = snapshot(fullAt = null, artworkAt = null), scope = backgroundScope)
        assertIs<CatalogMetadataResult.Ready>(
            artworkFixture.coordinator.require(KEY, CatalogMetadataLevel.Artwork),
        )
        assertEquals(1, artworkFixture.source.detailsCalls)

        val fullFixture = fixture(snapshot = snapshot(fullAt = null, artworkAt = null), scope = backgroundScope)
        assertIs<CatalogMetadataResult.Ready>(fullFixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertEquals(1, fullFixture.source.detailsCalls)
    }

    @Test
    fun nullArtworkDetailsIsNegativeCachedInsideArtworkTtl() = runTest {
        val fixture = fixture(snapshot = snapshot(fullAt = null, artworkAt = null), scope = backgroundScope)

        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Artwork))
        assertEquals(null, fixture.repository.snapshot?.entry?.coverUrl)
        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Artwork))
        runCurrent()

        assertEquals(1, fixture.source.detailsCalls)
    }

    @Test
    fun staleArtworkAndFullReturnCacheBeforeBackgroundLoadCompletes() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(
            snapshot = snapshot(fullAt = 1, artworkAt = 1),
            scope = backgroundScope,
            detailsGate = gate,
        )

        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Artwork))
        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        runCurrent()

        assertEquals(1, fixture.source.detailsCalls)
        assertTrue(!gate.isCompleted)
        gate.complete(Unit)
        runCurrent()
    }

    @Test
    fun pluginVersionChangeRevalidatesInsideTtl() = runTest {
        val fixture = fixture(snapshot = snapshot(fullAt = NOW, artworkAt = NOW), scope = backgroundScope)
        fixture.source.versionValue = "2.0.0"

        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        runCurrent()

        assertEquals(1, fixture.source.detailsCalls)
    }

    @Test
    fun explicitRefreshBypassesFreshness() = runTest {
        val fixture = fixture(snapshot = snapshot(fullAt = NOW, artworkAt = NOW), scope = backgroundScope)

        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.refresh(KEY, CatalogMetadataLevel.Full))

        assertEquals(1, fixture.source.detailsCalls)
    }

    @Test
    fun retryableFailureIsSuppressedForFiveMinutesAndRefreshBypassesIt() = runTest {
        val fixture = fixture(snapshot = snapshot(fullAt = null, artworkAt = null), scope = backgroundScope)
        fixture.source.result = CatalogSourceResult.Failure(CatalogSourceFailure("temporary", true))

        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertEquals(1, fixture.source.detailsCalls)

        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.refresh(KEY, CatalogMetadataLevel.Full))
        assertEquals(2, fixture.source.detailsCalls)

        fixture.clock.now += CatalogMetadataPolicy.AUTO_RETRY_COOLDOWN_MILLIS + 1
        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertEquals(3, fixture.source.detailsCalls)
    }

    @Test
    fun sourceUnavailableUsesCooldownAndRecoversAfterBoundary() = runTest {
        val fixture = fixture(snapshot = snapshot(fullAt = null, artworkAt = null), scope = backgroundScope)
        fixture.registry.current = null

        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        fixture.registry.current = fixture.source
        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertEquals(0, fixture.source.detailsCalls)

        fixture.clock.now += CatalogMetadataPolicy.AUTO_RETRY_COOLDOWN_MILLIS + 1
        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertEquals(1, fixture.source.detailsCalls)
    }

    @Test
    fun nonRetryableSourceFailureIsSuppressedUntilPluginVersionChanges() = runTest {
        val fixture = fixture(snapshot = snapshot(fullAt = null, artworkAt = null), scope = backgroundScope)
        fixture.source.result = CatalogSourceResult.Failure(CatalogSourceFailure("permanent", false))

        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertEquals(1, fixture.source.detailsCalls)

        fixture.source.versionValue = "2.0.0"
        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertEquals(2, fixture.source.detailsCalls)
    }

    @Test
    fun sourceIdMismatchIsVersionBoundSuppression() = runTest {
        val fixture = fixture(snapshot = snapshot(fullAt = null, artworkAt = null), scope = backgroundScope)
        fixture.source.result = CatalogSourceResult.Success(details("wrong"))

        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertEquals(1, fixture.source.detailsCalls)

        fixture.source.versionValue = "2.0.0"
        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertEquals(2, fixture.source.detailsCalls)
    }

    @Test
    fun nonRetryableStoreFailureUsesBoundedCooldown() = runTest {
        val fixture = fixture(snapshot = snapshot(fullAt = null, artworkAt = null), scope = backgroundScope)
        fixture.repository.storeFailure = CatalogStoreFailure("store.locked", false)

        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertEquals(1, fixture.source.detailsCalls)

        fixture.clock.now += CatalogMetadataPolicy.AUTO_RETRY_COOLDOWN_MILLIS + 1
        assertIs<CatalogMetadataResult.Failure>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertEquals(2, fixture.source.detailsCalls)
    }

    @Test
    fun threeConcurrentCallersShareOneDetailsRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(
            snapshot = snapshot(fullAt = null, artworkAt = null),
            scope = backgroundScope,
            detailsGate = gate,
        )

        val artwork = async { fixture.coordinator.require(KEY, CatalogMetadataLevel.Artwork) }
        val full = async { fixture.coordinator.require(KEY, CatalogMetadataLevel.Full) }
        val refresh = async { fixture.coordinator.refresh(KEY, CatalogMetadataLevel.Full) }
        runCurrent()
        assertEquals(1, fixture.source.detailsCalls)

        gate.complete(Unit)
        assertIs<CatalogMetadataResult.Ready>(artwork.await())
        assertIs<CatalogMetadataResult.Ready>(full.await())
        assertIs<CatalogMetadataResult.Ready>(refresh.await())
        assertEquals(1, fixture.source.detailsCalls)
    }

    @Test
    fun cancellingOneWaiterDoesNotCancelSharedLoad() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(
            snapshot = snapshot(fullAt = null, artworkAt = null),
            scope = backgroundScope,
            detailsGate = gate,
        )

        val cancelled = async { fixture.coordinator.require(KEY, CatalogMetadataLevel.Full) }
        val survivor = async { fixture.coordinator.require(KEY, CatalogMetadataLevel.Artwork) }
        runCurrent()
        assertEquals(1, fixture.source.detailsCalls)
        cancelled.cancel()
        runCurrent()
        assertEquals(1, fixture.source.detailsCalls)

        gate.complete(Unit)
        assertIs<CatalogMetadataResult.Ready>(survivor.await())
        assertEquals(1, fixture.source.detailsCalls)
    }

    @Test
    fun queuedStaleVerificationUsesLatestPersistedStampAfterRefresh() = runTest {
        val fixture = fixture(
            snapshot = snapshot(fullAt = 1, artworkAt = 1),
            scope = backgroundScope,
        )

        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.refresh(KEY, CatalogMetadataLevel.Full))
        assertEquals(1, fixture.source.detailsCalls)

        runCurrent()
        assertEquals(1, fixture.source.detailsCalls)
    }

    @Test
    fun explicitRefreshJoinsRunningBackgroundRevalidation() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(
            snapshot = snapshot(fullAt = 1, artworkAt = 1),
            scope = backgroundScope,
            detailsGate = gate,
        )

        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.require(KEY, CatalogMetadataLevel.Full))
        runCurrent()
        assertEquals(1, fixture.source.detailsCalls)
        val refresh = async { fixture.coordinator.refresh(KEY, CatalogMetadataLevel.Full) }
        runCurrent()
        assertEquals(1, fixture.source.detailsCalls)

        gate.complete(Unit)
        assertIs<CatalogMetadataResult.Ready>(refresh.await())
    }

    @Test
    fun completedInFlightEntryIsRemovedBeforeNextRefresh() = runTest {
        val fixture = fixture(snapshot = snapshot(fullAt = NOW, artworkAt = NOW), scope = backgroundScope)

        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.refresh(KEY, CatalogMetadataLevel.Full))
        assertIs<CatalogMetadataResult.Ready>(fixture.coordinator.refresh(KEY, CatalogMetadataLevel.Full))

        assertEquals(2, fixture.source.detailsCalls)
    }

    @Test
    fun refreshRejectsSummary() = runTest {
        val fixture = fixture(snapshot = snapshot(fullAt = NOW, artworkAt = NOW), scope = backgroundScope)
        var failed = false
        try {
            fixture.coordinator.refresh(KEY, CatalogMetadataLevel.Summary)
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    private fun TestScope.fixture(
        snapshot: CatalogMetadataSnapshot?,
        scope: kotlinx.coroutines.CoroutineScope,
        detailsGate: CompletableDeferred<Unit>? = null,
    ): Fixture {
        val clock = MutableClock(NOW)
        val repository = FakeRepository(snapshot)
        val source = Source(detailsGate)
        val registry = Registry(source)
        val loader = CatalogDetailsLoader(registry, repository, StoryMatcher(), clock)
        val coordinator = CatalogMetadataCoordinator(
            repository = repository,
            sources = registry,
            loader = loader,
            policy = CatalogMetadataPolicy(clock),
            clock = clock,
            processScope = scope,
        )
        return Fixture(clock, repository, source, registry, coordinator)
    }

    private data class Fixture(
        val clock: MutableClock,
        val repository: FakeRepository,
        val source: Source,
        val registry: Registry,
        val coordinator: CatalogMetadataCoordinator,
    )

    private fun snapshot(fullAt: Long?, artworkAt: Long?) = CatalogMetadataSnapshot(
        entry = CatalogEntry(
            storyId = StoryId("story:source"),
            pluginId = PluginId("a"),
            sourceId = "source",
            title = "Cached",
            contentType = ContentType.MANGA,
        ),
        summary = CatalogMetadataStamp("1.0.0", NOW),
        artwork = artworkAt?.let { CatalogMetadataStamp("1.0.0", it) },
        full = fullAt?.let { CatalogMetadataStamp("1.0.0", it) },
    )

    private fun details(sourceId: String = "source") = SourceDetails(
        sourceId = sourceId,
        sourceUrl = "url",
        title = "Resolved",
        aliases = emptySet(),
        authors = emptySet(),
        description = "details",
        genres = emptySet(),
        contentType = SourceContentType.MANGA,
        languageTags = emptySet(),
        coverUrl = null,
        scoreValue = null,
        scoreScale = null,
        popularityRank = null,
    )

    private class MutableClock(var now: Long) : Clock {
        override fun nowEpochMillis(): Long = now
    }

    private class Registry(source: CatalogSource?) : CatalogSourceRegistry {
        var current: CatalogSource? = source
        override suspend fun enabled(): List<CatalogSource> = listOfNotNull(current)
        override suspend fun source(pluginId: PluginId): CatalogSource? = current?.takeIf { it.pluginId == pluginId }
    }

    private class Source(
        private val gate: CompletableDeferred<Unit>? = null,
    ) : CatalogSource {
        override val pluginId = PluginId("a")
        var versionValue = "1.0.0"
        override val version: String get() = versionValue
        var detailsCalls = 0
        var result: CatalogSourceResult<SourceDetails> = CatalogSourceResult.Success(
            SourceDetails(
                sourceId = "source",
                sourceUrl = "url",
                title = "Resolved",
                aliases = emptySet(),
                authors = emptySet(),
                description = "details",
                genres = emptySet(),
                contentType = SourceContentType.MANGA,
                languageTags = emptySet(),
                coverUrl = null,
                scoreValue = null,
                scoreScale = null,
                popularityRank = null,
            ),
        )

        override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> = error("unused")
        override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> = error("unused")
        override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> {
            detailsCalls++
            gate?.await()
            return result
        }
        override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = error("unused")
    }

    private class FakeRepository(initial: CatalogMetadataSnapshot?) : CatalogRepository {
        var snapshot: CatalogMetadataSnapshot? = initial
        var storeFailure: CatalogStoreFailure? = null

        override fun observeHomes() = emptyFlow<List<CatalogHomeSnapshot>>()
        override fun observeStory(storyId: StoryId) = emptyFlow<StoryCatalogSnapshot?>()
        override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())
        override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? = snapshot
        override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<Unit, CatalogStoreFailure> =
            Outcome.Success(Unit)

        override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<StoryId, CatalogStoreFailure> {
            storeFailure?.let { return Outcome.Failure(it) }
            val durableStoryId = snapshot?.entry?.storyId ?: mutation.storyId
            val entry = mutation.entry.copy(storyId = durableStoryId)
            val stamp = CatalogMetadataStamp(mutation.pluginVersion, mutation.fetchedAtEpochMillis)
            snapshot = CatalogMetadataSnapshot(entry, stamp, stamp, stamp)
            return Outcome.Success(durableStoryId)
        }
    }

    private companion object {
        val KEY = CatalogMetadataKey(PluginId("a"), "source")
        const val NOW = 1_000_000_000L
    }
}
