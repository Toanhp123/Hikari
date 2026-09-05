package app.openstory.cache

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.downloads.cache.AutomaticCachePublicationResult
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.settings.AppSettings
import app.openstory.settings.AppSettingsRepository
import app.openstory.settings.SettingsDefaults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticCachePolicyCoordinatorTest {
    @Test
    fun `settings project initial quota and later reduction reconciles shared usage`() = runTest {
        val settings = MutableStateFlow(SettingsDefaults().defaultSettings())
        val fixture = AutomaticCacheTestFixture(initialQuotaBytes = 1L, reconciliationScope = backgroundScope)
        fixture.addDocument(ChapterReleaseId("release:large"), bytes = 65L * MIB)
        val coordinator = AutomaticCachePolicyCoordinator(
            FakeSettingsRepository(settings),
            FakeReadingProgressRepository(MutableStateFlow(emptyList())),
            fixture.coordinator,
        )

        coordinator.start(backgroundScope)
        runCurrent()
        assertEquals(256L * MIB, fixture.coordinator.snapshot().quotaBytes)

        settings.value = settings.value.copy(automaticCacheQuotaBytes = 64L * MIB)
        runCurrent()

        assertEquals(64L * MIB, fixture.coordinator.snapshot().quotaBytes)
        assertEquals(0L, fixture.coordinator.snapshot().committedBytes)
    }

    @Test
    fun `quota zero revokes authority and pending publication captured before settings change`() = runTest {
        val settings = MutableStateFlow(SettingsDefaults().defaultSettings())
        val fixture = AutomaticCacheTestFixture(initialQuotaBytes = 1L, reconciliationScope = backgroundScope)
        val coordinator = AutomaticCachePolicyCoordinator(
            FakeSettingsRepository(settings),
            FakeReadingProgressRepository(MutableStateFlow(emptyList())),
            fixture.coordinator,
        )
        coordinator.start(backgroundScope)
        runCurrent()
        val authority = assertNotNull(fixture.coordinator.captureWriteAuthority())
        val reservation = assertNotNull(fixture.coordinator.reserve(1L, authority))
        var published = false

        settings.value = settings.value.copy(automaticCacheQuotaBytes = 0L)
        runCurrent()
        val result = fixture.coordinator.publishIfCurrent(authority, reservation) {
            published = true
        }

        assertIs<AutomaticCachePublicationResult.Revoked>(result)
        assertFalse(published)
        assertNull(fixture.coordinator.captureWriteAuthority())
    }

    @Test
    fun `only incomplete progress rows protect releases during reconciliation`() = runTest {
        val incompleteRelease = ChapterReleaseId("release:incomplete")
        val completedRelease = ChapterReleaseId("release:completed")
        val settings = MutableStateFlow(
            SettingsDefaults().defaultSettings().copy(automaticCacheQuotaBytes = 100L),
        )
        val progress = MutableStateFlow(
            listOf(
                progress(incompleteRelease, completedAt = null),
                progress(completedRelease, completedAt = 10L),
            ),
        )
        val fixture = AutomaticCacheTestFixture(initialQuotaBytes = 100L, reconciliationScope = backgroundScope)
        fixture.addDocument(incompleteRelease, bytes = 10L)
        fixture.addDocument(completedRelease, bytes = 11L)
        val coordinator = AutomaticCachePolicyCoordinator(
            FakeSettingsRepository(settings),
            FakeReadingProgressRepository(progress),
            fixture.coordinator,
        )

        coordinator.start(backgroundScope)
        runCurrent()
        settings.value = settings.value.copy(automaticCacheQuotaBytes = 20L)
        runCurrent()

        assertEquals(listOf(incompleteRelease), fixture.documents.entries().map { it.key.releaseId })
    }

    @Test
    fun `start is idempotent`() = runTest {
        val settings = MutableStateFlow(SettingsDefaults().defaultSettings())
        val progress = MutableStateFlow(emptyList<ReadingProgress>())
        val fixture = AutomaticCacheTestFixture(initialQuotaBytes = 1L, reconciliationScope = backgroundScope)
        val coordinator = AutomaticCachePolicyCoordinator(
            FakeSettingsRepository(settings),
            FakeReadingProgressRepository(progress),
            fixture.coordinator,
        )

        coordinator.start(backgroundScope)
        coordinator.start(backgroundScope)
        runCurrent()

        assertEquals(1, settings.subscriptionCount.value)
        assertEquals(1, progress.subscriptionCount.value)
    }

    private fun progress(releaseId: ChapterReleaseId, completedAt: Long?) = ReadingProgress(
        storyId = StoryId("story"),
        canonicalChapterId = CanonicalChapterId("chapter:${releaseId.value}"),
        releaseId = releaseId,
        contentFingerprint = "fingerprint:${releaseId.value}",
        position = ReadingPosition("block", 0, 0.5f),
        completedAtEpochMillis = completedAt,
        updatedAtEpochMillis = 10L,
    )

    private companion object {
        const val MIB = 1024L * 1024L
    }
}

internal class FakeSettingsRepository(
    override val settings: Flow<AppSettings>,
) : AppSettingsRepository {
    override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    override suspend fun resetToDefaults() = Unit
}

internal class FakeReadingProgressRepository(
    private val values: MutableStateFlow<List<ReadingProgress>>,
) : ReadingProgressRepository {
    override fun observeAll(): Flow<List<ReadingProgress>> = values
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> =
        values.map { rows -> rows.firstOrNull { it.storyId == storyId && it.canonicalChapterId == chapterId } }
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? =
        values.value.firstOrNull { it.storyId == storyId && it.canonicalChapterId == chapterId }
    override suspend fun save(progress: ReadingProgress) {
        values.value = values.value.filterNot {
            it.storyId == progress.storyId && it.canonicalChapterId == progress.canonicalChapterId
        } + progress
    }
}
