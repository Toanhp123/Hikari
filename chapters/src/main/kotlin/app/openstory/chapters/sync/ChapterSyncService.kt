package app.openstory.chapters.sync

import app.openstory.chapters.aggregation.ChapterAggregationEngine
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.normalization.ChapterLabelParser
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.repository.ChapterSyncPhase
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.chapters.source.ChapterListMode
import app.openstory.chapters.source.ChapterSource
import app.openstory.chapters.source.ChapterSourcePage
import app.openstory.chapters.source.ChapterSourceRegistry
import app.openstory.chapters.source.ChapterSourceRequest
import app.openstory.chapters.source.ChapterSourceResult
import app.openstory.common.Clock
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingRepository
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope

class ChapterSyncService(
    private val mappings: ContentMappingRepository,
    private val sources: ChapterSourceRegistry,
    private val chapters: ChapterRepository,
    private val aggregation: ChapterAggregationEngine,
    private val parser: ChapterLabelParser,
    private val clock: Clock,
) {
    private val pageSync = ChapterPageSynchronizer(chapters, aggregation, parser, clock)

    suspend fun sync(storyId: StoryId): ChapterSyncReport {
        val protectedMappings = mappings.observe(storyId).first()
            .filter { mapping -> mapping.origin.isProtected }
            .sortedWith(compareBy<ContentMapping> { it.pluginId.value }.thenBy { it.sourceStoryId })
        val sourceByPlugin = sources.enabled().associateBy(ChapterSource::pluginId)

        val results = supervisorScope {
            protectedMappings.map { mapping ->
                async {
                    val source = sourceByPlugin[mapping.pluginId]
                        ?: return@async SyncResult.Failed(
                            ChapterSyncFailure(mapping.pluginId, SOURCE_UNAVAILABLE, false),
                        )
                    syncMapping(storyId, mapping, source)
                }
            }.awaitAll()
        }
        val failures = results.filterIsInstance<SyncResult.Failed>().map(SyncResult.Failed::failure)
        return if (failures.isEmpty()) {
            ChapterSyncReport.Success(
                results.filterIsInstance<SyncResult.Completed>().map(SyncResult.Completed::success),
            )
        } else {
            ChapterSyncReport.Failure(failures)
        }
    }

    private suspend fun syncMapping(
        storyId: StoryId,
        mapping: ContentMapping,
        source: ChapterSource,
    ): SyncResult = try {
        val state = chapters.syncState(storyId, mapping.pluginId, mapping.sourceStoryId)
        var releaseCount = 0
        if (state == null) {
            when (val recent = pageSync.sync(storyId, mapping, source, ChapterListMode.RECENT, null)) {
                is PageSyncResult.Failed -> return SyncResult.Failed(recent.failure)
                is PageSyncResult.Completed -> releaseCount += recent.releaseCount
            }
        }

        val currentState = chapters.syncState(storyId, mapping.pluginId, mapping.sourceStoryId)
        val mode = if (currentState?.phase == ChapterSyncPhase.INCREMENTAL) {
            ChapterListMode.INCREMENTAL
        } else {
            ChapterListMode.FULL
        }
        when (val result = pageSync.sync(storyId, mapping, source, mode, currentState)) {
            is PageSyncResult.Failed -> SyncResult.Failed(result.failure)
            is PageSyncResult.Completed -> SyncResult.Completed(
                ChapterSyncSourceSuccess(mapping.pluginId, releaseCount + result.releaseCount),
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        SyncResult.Failed(ChapterSyncFailure(mapping.pluginId, SYNC_FAILED, true))
    }

    private sealed interface SyncResult {
        data class Completed(val success: ChapterSyncSourceSuccess) : SyncResult
        data class Failed(val failure: ChapterSyncFailure) : SyncResult
    }

    private companion object {
        const val SOURCE_UNAVAILABLE = "chapter.source_unavailable"
        const val SYNC_FAILED = "chapter.sync_failed"
    }
}

private class ChapterPageSynchronizer(
    private val chapters: ChapterRepository,
    private val aggregation: ChapterAggregationEngine,
    private val parser: ChapterLabelParser,
    private val clock: Clock,
) {
    suspend fun sync(
        storyId: StoryId,
        mapping: ContentMapping,
        source: ChapterSource,
        mode: ChapterListMode,
        initialState: ChapterSyncState?,
    ): PageSyncResult {
        var cursor = initialState?.cursor
        val startedFromBeginning = mode == ChapterListMode.FULL && cursor == null
        val fullReleaseIds = linkedSetOf<ChapterReleaseId>()
        var releaseCount = 0
        var pageCount = 0
        var outcome: PageSyncResult? = null

        while (outcome == null && pageCount < MAX_PAGES) {
            when (val fetched = fetchPage(mapping, source, mode, initialState, cursor)) {
                is FetchedPage.Failed -> outcome = PageSyncResult.Failed(fetched.failure)
                is FetchedPage.Success -> {
                    val releases = fetched.page.toReleases(storyId, mapping)
                    fullReleaseIds += releases.map(ChapterRelease::id)
                    when (val committed = commitPage(
                        storyId,
                        mapping,
                        mode,
                        initialState,
                        fetched.page,
                        releases,
                        fullReleaseIds,
                        startedFromBeginning,
                    )) {
                        is PageCommit.Failed -> outcome = PageSyncResult.Failed(committed.failure)
                        PageCommit.Success -> {
                            releaseCount += releases.size
                            outcome = pageOutcome(mapping, fetched.page.nextToken, cursor, releaseCount)
                            cursor = fetched.page.nextToken
                        }
                    }
                }
            }
            pageCount += 1
        }
        return outcome ?: PageSyncResult.Failed(ChapterSyncFailure(mapping.pluginId, PAGE_LIMIT, false))
    }

    private suspend fun fetchPage(
        mapping: ContentMapping,
        source: ChapterSource,
        mode: ChapterListMode,
        state: ChapterSyncState?,
        cursor: String?,
    ): FetchedPage = when (val result = source.chapters(
        ChapterSourceRequest(
            sourceStoryId = mapping.sourceStoryId,
            mode = mode,
            checkpoint = state?.checkpoint ?: state?.fingerprint,
            nextToken = cursor,
        ),
    )) {
        is ChapterSourceResult.Failure -> FetchedPage.Failed(
            ChapterSyncFailure(mapping.pluginId, result.failure.code, result.failure.retryable),
        )
        is ChapterSourceResult.Success -> FetchedPage.Success(result.page)
    }

    private suspend fun commitPage(
        storyId: StoryId,
        mapping: ContentMapping,
        mode: ChapterListMode,
        previousState: ChapterSyncState?,
        page: ChapterSourcePage,
        releases: List<ChapterRelease>,
        fullReleaseIds: Set<ChapterReleaseId>,
        startedFromBeginning: Boolean,
    ): PageCommit {
        val snapshot = chapters.snapshot(storyId)
        val merged = snapshot.releases.associateByTo(linkedMapOf(), ChapterRelease::id).apply {
            releases.forEach { release -> put(release.id, release) }
        }
        val activeReleases = authoritativeReleases(
            merged.values,
            mapping,
            mode,
            page.nextToken,
            fullReleaseIds,
            startedFromBeginning,
        )
        val fingerprint = fingerprint(activeReleases.filter { release -> release.belongsTo(mapping) })
        val state = nextState(storyId, mapping, mode, page, fingerprint, previousState)
        val plan = aggregation.plan(storyId, snapshot.chapters, activeReleases, snapshot.overrides)
        return when (val commit = chapters.commit(ChapterMutation(storyId, releases, plan, state))) {
            is ChapterCommitResult.Failure -> PageCommit.Failed(
                ChapterSyncFailure(mapping.pluginId, commit.code, commit.retryable),
            )
            ChapterCommitResult.Success -> PageCommit.Success
        }
    }

    private fun ChapterSourcePage.toReleases(
        storyId: StoryId,
        mapping: ContentMapping,
    ): List<ChapterRelease> = releases.map { sourceRelease ->
        val displayLabel = sourceRelease.title?.takeIf(String::isNotBlank)
            ?: sourceRelease.rawNumber?.takeIf(String::isNotBlank)
            ?: sourceRelease.sourceReleaseId
        ChapterRelease(
            id = releaseId(mapping, sourceRelease.sourceReleaseId),
            storyId = storyId,
            pluginId = mapping.pluginId,
            sourceStoryId = mapping.sourceStoryId,
            sourceReleaseId = sourceRelease.sourceReleaseId,
            displayLabel = displayLabel,
            parsedLabel = parser.parse(displayLabel),
            languageTag = sourceRelease.languageTag?.takeIf(String::isNotBlank) ?: UNDETERMINED_LANGUAGE,
            publishedAtEpochMillis = sourceRelease.publishedAtEpochMillis,
            canonicalChapterId = null,
        )
    }

    private fun nextState(
        storyId: StoryId,
        mapping: ContentMapping,
        mode: ChapterListMode,
        page: ChapterSourcePage,
        fingerprint: String,
        previous: ChapterSyncState?,
    ): ChapterSyncState {
        val completed = page.nextToken == null
        val phase = when {
            mode == ChapterListMode.RECENT -> ChapterSyncPhase.FULL
            mode == ChapterListMode.FULL && completed -> ChapterSyncPhase.INCREMENTAL
            mode == ChapterListMode.FULL -> ChapterSyncPhase.FULL
            else -> ChapterSyncPhase.INCREMENTAL
        }
        return ChapterSyncState(
            storyId = storyId,
            pluginId = mapping.pluginId,
            sourceStoryId = mapping.sourceStoryId,
            phase = phase,
            cursor = if (mode == ChapterListMode.RECENT) null else page.nextToken,
            checkpoint = if (completed && mode != ChapterListMode.RECENT) fingerprint else previous?.checkpoint,
            fingerprint = if (mode == ChapterListMode.RECENT) previous?.fingerprint else fingerprint,
            updatedAtEpochMillis = clock.nowEpochMillis(),
        )
    }

    private fun pageOutcome(
        mapping: ContentMapping,
        nextToken: String?,
        currentCursor: String?,
        releaseCount: Int,
    ): PageSyncResult? = when {
        nextToken == null -> PageSyncResult.Completed(releaseCount)
        nextToken == currentCursor -> PageSyncResult.Failed(
            ChapterSyncFailure(mapping.pluginId, PAGE_LOOP, false),
        )
        else -> null
    }

    private sealed interface FetchedPage {
        data class Success(val page: ChapterSourcePage) : FetchedPage
        data class Failed(val failure: ChapterSyncFailure) : FetchedPage
    }

    private sealed interface PageCommit {
        data object Success : PageCommit
        data class Failed(val failure: ChapterSyncFailure) : PageCommit
    }

    private companion object {
        const val MAX_PAGES = 100
        const val UNDETERMINED_LANGUAGE = "und"
        const val PAGE_LOOP = "chapter.source_page_loop"
        const val PAGE_LIMIT = "chapter.source_page_limit"
    }
}

private sealed interface PageSyncResult {
    data class Completed(val releaseCount: Int) : PageSyncResult
    data class Failed(val failure: ChapterSyncFailure) : PageSyncResult
}

private fun authoritativeReleases(
    merged: Collection<ChapterRelease>,
    mapping: ContentMapping,
    mode: ChapterListMode,
    nextToken: String?,
    fullReleaseIds: Set<ChapterReleaseId>,
    startedFromBeginning: Boolean,
): List<ChapterRelease> = if (startedFromBeginning && mode == ChapterListMode.FULL && nextToken == null) {
    merged.filter { release ->
        release.pluginId != mapping.pluginId ||
            release.sourceStoryId != mapping.sourceStoryId ||
            release.id in fullReleaseIds
    }
} else {
    merged.toList()
}

private fun releaseId(mapping: ContentMapping, sourceReleaseId: String): ChapterReleaseId = ChapterReleaseId(
    "chapter-release:${sha256("${mapping.pluginId.value}|${mapping.sourceStoryId}|$sourceReleaseId")}",
)

private fun ChapterRelease.belongsTo(mapping: ContentMapping): Boolean =
    pluginId == mapping.pluginId && sourceStoryId == mapping.sourceStoryId

private fun fingerprint(releases: Collection<ChapterRelease>): String = sha256(
    releases.map { release -> release.id.value }.sorted().joinToString("\n"),
)

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
