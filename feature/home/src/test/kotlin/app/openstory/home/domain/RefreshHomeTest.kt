package app.openstory.home.domain

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.common.dispatchers.FixedAppDispatchers
import app.openstory.database.repository.CatalogRepository
import app.openstory.home.model.HomeCatalogFreshness
import app.openstory.matching.AggregateRanking
import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogEntryId
import app.openstory.model.CatalogEntryWithStory
import app.openstory.model.CatalogHomeSection
import app.openstory.model.CatalogHomeSnapshot
import app.openstory.model.CatalogSnapshot
import app.openstory.model.CatalogSourceMetadata
import app.openstory.model.ContentType
import app.openstory.model.PluginId
import app.openstory.model.StoryId
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.catalog.CatalogScore
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.api.catalog.CatalogSection
import app.openstory.plugin.api.content.ContentPlugin
import app.openstory.plugin.host.HostedPlugin
import app.openstory.plugin.host.PluginHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshHomeTest {
    @Test
    fun oneCatalogFailureStillPersistsSuccessfulCatalog() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeCatalogRepository()
        val host = FakePluginHost(
            listOf(
                hosted("catalog.a", "2.3.4") { AppResult.Success(homeSections("catalog.a")) },
                hosted("catalog.b", "4.0.0") {
                    AppResult.Failure(AppError.Network(code = "network.timeout", retryable = true))
                },
            ),
        )
        val useCase = RefreshHome(
            host = host,
            mapper = CatalogSnapshotMapper(),
            repository = repository,
            dispatchers = FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )

        val report = useCase()

        assertEquals(setOf("catalog.a"), report.succeeded.map { it.value }.toSet())
        assertEquals(setOf("catalog.b"), report.failed.keys.map { it.value }.toSet())
        assertEquals(listOf("catalog.a"), repository.savedSnapshots.map { it.pluginId.value })
        assertEquals("2.3.4", repository.savedSnapshots.single().pluginVersion)
        assertFalse(report.freshness.getValue(PluginId("catalog.a")).stale)
        assertTrue(report.freshness.getValue(PluginId("catalog.b")).stale)
    }

    @Test
    fun failedCatalogReportsPreviousCachedTimestampWithoutReplacingIt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cached = cachedHome("catalog.b", refreshedAt = 42L)
        val repository = FakeCatalogRepository(initialHomes = listOf(cached))
        val host = FakePluginHost(
            listOf(
                hosted("catalog.b", "2.0.0") {
                    AppResult.Failure(AppError.Plugin(code = "plugin.refresh_failed", retryable = false))
                },
            ),
        )
        val report = RefreshHome(
            host,
            CatalogSnapshotMapper(),
            repository,
            FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )()

        assertEquals(
            HomeCatalogFreshness(refreshedAtEpochMillis = 42L, stale = true),
            report.freshness.getValue(PluginId("catalog.b")),
        )
        assertEquals(cached, repository.observeCatalogHome(PluginId("catalog.b")).first())
        assertTrue(repository.savedSnapshots.isEmpty())
    }

    @Test
    fun thrownPluginFailureIsIsolatedFromSiblingCatalogs() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeCatalogRepository()
        val host = FakePluginHost(
            listOf(
                hosted("catalog.throwing", "1.0.0") { error("fixture failure") },
                hosted("catalog.ok", "1.0.0") { AppResult.Success(homeSections("catalog.ok")) },
            ),
        )

        val report = RefreshHome(
            host,
            CatalogSnapshotMapper(),
            repository,
            FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )()

        assertEquals(setOf("catalog.ok"), report.succeeded.map { it.value }.toSet())
        assertEquals("catalog.refresh_failed", report.failed.getValue(PluginId("catalog.throwing")).code)
        assertEquals(listOf("catalog.ok"), repository.savedSnapshots.map { it.pluginId.value })
    }

    @Test
    fun successfulCatalogPersistsBeforeSlowSiblingCompletes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val releaseSlow = CompletableDeferred<Unit>()
        val repository = FakeCatalogRepository()
        val useCase = RefreshHome(
            FakePluginHost(
                listOf(
                    hosted("catalog.fast", "1.0.0") {
                        AppResult.Success(homeSections("catalog.fast"))
                    },
                    hosted("catalog.slow", "1.0.0") {
                        releaseSlow.await()
                        AppResult.Success(homeSections("catalog.slow"))
                    },
                ),
            ),
            CatalogSnapshotMapper(),
            repository,
            FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )

        val refresh = async { useCase() }
        runCurrent()

        assertFalse(refresh.isCompleted)
        assertEquals(listOf("catalog.fast"), repository.savedSnapshots.map { it.pluginId.value })

        releaseSlow.complete(Unit)
        refresh.await()
        assertEquals(setOf("catalog.fast", "catalog.slow"), repository.savedSnapshots.map { it.pluginId.value }.toSet())
    }

    @Test
    fun repositoryFailureReportsStaleCachedSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pluginId = PluginId("catalog.storage")
        val cached = cachedHome(pluginId.value, refreshedAt = 77L)
        val repository = FakeCatalogRepository(
            initialHomes = listOf(cached),
            ingestFailures = mapOf(
                pluginId to AppError.Storage(code = "storage.write_failed", retryable = false),
            ),
        )
        val useCase = RefreshHome(
            FakePluginHost(
                listOf(
                    hosted(pluginId.value, "2.0.0") {
                        AppResult.Success(homeSections(pluginId.value))
                    },
                ),
            ),
            CatalogSnapshotMapper(),
            repository,
            FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )

        val report = useCase()

        assertEquals("storage.write_failed", report.failed.getValue(pluginId).code)
        assertEquals(HomeCatalogFreshness(77L, stale = true), report.freshness.getValue(pluginId))
        assertEquals(cached, repository.observeCatalogHome(pluginId).first())
    }

    @Test
    fun refreshConcurrencyNeverExceedsConfiguredBound() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val release = CompletableDeferred<Unit>()
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val plugins = (1..4).map { index ->
            hosted("catalog.$index", "1.0.0") {
                val nowActive = active.incrementAndGet()
                maximum.updateAndGet { current -> max(current, nowActive) }
                try {
                    release.await()
                    AppResult.Success(homeSections("catalog.$index"))
                } finally {
                    active.decrementAndGet()
                }
            }
        }
        val useCase = RefreshHome(
            FakePluginHost(plugins),
            CatalogSnapshotMapper(),
            FakeCatalogRepository(),
            FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
            maxConcurrentCatalogs = 2,
        )

        val refresh = async { useCase() }
        runCurrent()
        assertEquals(2, maximum.get())
        release.complete(Unit)
        refresh.await()
        assertEquals(2, maximum.get())
    }

    @Test
    fun cachedCombinedHomeEmitsWhileNetworkRefreshIsStillSuspended() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val release = CompletableDeferred<Unit>()
        val repository = FakeCatalogRepository(
            initialHomes = listOf(
                cachedHome(
                    "catalog.a",
                    refreshedAt = 10L,
                    storyId = "story-shared",
                    sectionTitle = "Trending",
                    score = 8.0,
                    scale = 10.0,
                ),
                cachedHome(
                    "catalog.b",
                    refreshedAt = 20L,
                    storyId = "story-shared",
                    sectionTitle = "Popular",
                    score = 90.0,
                    scale = 100.0,
                ),
            ),
        )
        val refresh = RefreshHome(
            FakePluginHost(
                listOf(
                    hosted("catalog.a", "2.0.0") {
                        release.await()
                        AppResult.Success(homeSections("catalog.a"))
                    },
                ),
            ),
            CatalogSnapshotMapper(),
            repository,
            FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )
        val observe = ObserveCombinedHome(repository, AggregateRanking())

        val refreshJob = async { refresh() }
        runCurrent()
        val cached = observe().first()

        assertFalse(refreshJob.isCompleted)
        assertEquals(1, cached.combined.size)
        val combined = cached.combined.single()
        assertEquals(StoryId("story-shared"), combined.storyId)
        assertEquals(setOf("catalog.a", "catalog.b"), combined.sources.map { it.pluginId.value }.toSet())
        assertEquals(setOf("Trending", "Popular"), combined.sources.flatMap { it.sections }.map { it.title }.toSet())
        assertEquals(setOf(10.0, 100.0), combined.sources.mapNotNull { it.scoreScale }.toSet())
        assertEquals(2, cached.catalogs.size)

        release.complete(Unit)
        refreshJob.await()
    }
}

private class FakePluginHost(
    private val catalogs: List<HostedPlugin<CatalogPlugin>>,
) : PluginHost {
    override suspend fun catalog(id: PluginId): HostedPlugin<CatalogPlugin> =
        catalogs.single { it.id == id }

    override suspend fun content(id: PluginId): HostedPlugin<ContentPlugin> = error("Not used")

    override suspend fun enabledCatalogs(): List<HostedPlugin<CatalogPlugin>> = catalogs

    override suspend fun enabledContentSources(): List<HostedPlugin<ContentPlugin>> = emptyList()
}

private fun hosted(
    id: String,
    version: String,
    home: suspend () -> AppResult<List<CatalogSection>>,
): HostedPlugin<CatalogPlugin> = HostedPlugin(
    id = PluginId(id),
    version = version,
    instance = object : CatalogPlugin {
        override suspend fun home(request: CatalogHomeRequest): AppResult<List<CatalogSection>> = home()
        override suspend fun search(request: CatalogSearchRequest): AppResult<Page<CatalogCard>> =
            AppResult.Success(Page(emptyList(), null))
        override suspend fun details(sourceId: String): AppResult<CatalogDetails> = error("Not used")
        override suspend fun filters(): AppResult<List<CatalogFilterDefinition>> = AppResult.Success(emptyList())
    },
)

private fun homeSections(pluginId: String): List<CatalogSection> = listOf(
    CatalogSection(
        sourceId = "home",
        title = "Home",
        items = listOf(
            CatalogCard(
                sourceId = "$pluginId-story",
                title = "$pluginId Story",
                contentType = ContentType.WEB_NOVEL,
                authors = listOf("Author"),
                image = null,
                score = CatalogScore(8.0, 10.0),
            ),
        ),
    ),
)

private class FakeCatalogRepository(
    initialHomes: List<CatalogHomeSnapshot> = emptyList(),
    private val ingestFailures: Map<PluginId, AppError> = emptyMap(),
) : CatalogRepository {
    private val homes = MutableStateFlow(initialHomes)
    val savedSnapshots = mutableListOf<CatalogSnapshot>()
    private var nextTimestamp = 100L

    override suspend fun ingest(snapshot: CatalogSnapshot): AppResult<Unit> {
        ingestFailures[snapshot.pluginId]?.let { return AppResult.Failure(it) }
        savedSnapshots += snapshot
        val home = snapshot.toCachedHome(nextTimestamp++)
        homes.value = homes.value.filterNot { it.pluginId == snapshot.pluginId } + home
        return AppResult.Success(Unit)
    }

    override suspend fun upsertSourceMetadata(
        pluginId: PluginId,
        pluginVersion: String,
        metadata: CatalogSourceMetadata,
    ): AppResult<CatalogEntryWithStory> = error("Not used")

    override suspend fun catalogEntry(
        pluginId: PluginId,
        sourceId: String,
    ): AppResult<CatalogEntryWithStory?> = error("Not used")

    override fun observeCatalogHome(pluginId: PluginId): Flow<CatalogHomeSnapshot?> =
        homes.map { current -> current.firstOrNull { it.pluginId == pluginId } }

    override fun observeCatalogHomes(): Flow<List<CatalogHomeSnapshot>> = homes
}

private fun CatalogSnapshot.toCachedHome(timestamp: Long): CatalogHomeSnapshot = CatalogHomeSnapshot(
    pluginId = pluginId,
    pluginVersion = pluginVersion,
    refreshedAtEpochMillis = timestamp,
    sections = sections.map { section ->
        CatalogHomeSection(
            sourceId = section.sourceId,
            title = section.title,
            items = section.items.map { item ->
                CatalogEntryWithStory(
                    storyId = StoryId("story:${pluginId.value}:${item.sourceId}"),
                    entry = catalogEntry(
                        pluginId = pluginId.value,
                        version = pluginVersion,
                        sourceId = item.sourceId,
                        storyTitle = item.title,
                        score = item.score,
                        scale = item.scoreScale,
                    ),
                )
            },
        )
    },
)

private fun cachedHome(
    pluginId: String,
    refreshedAt: Long,
    storyId: String = "story-$pluginId",
    sectionTitle: String = "Home",
    score: Double? = 8.0,
    scale: Double? = 10.0,
): CatalogHomeSnapshot {
    val plugin = PluginId(pluginId)
    val sourceId = "source-$pluginId"
    return CatalogHomeSnapshot(
        pluginId = plugin,
        pluginVersion = "1.0.0",
        refreshedAtEpochMillis = refreshedAt,
        sections = listOf(
            CatalogHomeSection(
                sourceId = "section-$pluginId",
                title = sectionTitle,
                items = listOf(
                    CatalogEntryWithStory(
                        storyId = StoryId(storyId),
                        entry = catalogEntry(pluginId, "1.0.0", sourceId, "$pluginId Story", score, scale),
                    ),
                ),
            ),
        ),
    )
}

private fun catalogEntry(
    pluginId: String,
    version: String,
    sourceId: String,
    storyTitle: String,
    score: Double?,
    scale: Double?,
) = CatalogEntry(
    id = CatalogEntryId("$pluginId:$sourceId"),
    catalogPluginId = PluginId(pluginId),
    externalStoryId = sourceId,
    sourceUrl = null,
    title = storyTitle,
    aliases = emptySet(),
    authors = setOf("Author"),
    description = null,
    genres = emptySet(),
    contentType = ContentType.WEB_NOVEL,
    languageTags = emptySet(),
    coverReference = null,
    publicationStatus = null,
    score = score,
    scoreScale = scale,
    popularityRank = null,
    pluginVersion = version,
    fetchedAtEpochMillis = 1L,
)
