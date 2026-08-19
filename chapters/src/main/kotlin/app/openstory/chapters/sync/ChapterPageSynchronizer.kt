package app.openstory.chapters.sync

import app.openstory.chapters.aggregation.AggregationPlan
import app.openstory.chapters.aggregation.ChapterAggregationEngine
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.normalization.ChapterLabelParser
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.repository.ChapterSyncPhase
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.chapters.source.ChapterListMode
import app.openstory.chapters.source.ChapterSource
import app.openstory.chapters.source.ChapterSourcePage
import app.openstory.chapters.source.ChapterSourceRequest
import app.openstory.chapters.source.ChapterSourceResult
import app.openstory.common.Clock
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.library.mapping.ContentMapping
import java.security.MessageDigest

internal class ChapterPageSynchronizer(
    private val chapters: ChapterRepository,
    private val aggregation: ChapterAggregationEngine,
    private val parser: ChapterLabelParser,
    private val clock: Clock,
) {
    private val releaseNormalizer = ChapterSourceReleaseNormalizer(parser)

    suspend fun state(storyId: StoryId, mapping: ContentMapping): ChapterSyncState? =
        chapters.syncState(storyId, mapping.pluginId, mapping.sourceStoryId)

    suspend fun sync(
        storyId: StoryId,
        mapping: ContentMapping,
        source: ChapterSource,
        mode: ChapterListMode,
        initialState: ChapterSyncState?,
    ): PageSyncResult {
        var cursor = if (mode == ChapterListMode.FULL && initialState?.phase == ChapterSyncPhase.INCREMENTAL) {
            null
        } else {
            initialState?.cursor
        }
        var graph: ChapterGraphSnapshot? = null
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
                    val currentGraph = graph ?: chapters.snapshot(storyId)
                    fullReleaseIds += releases.map(ChapterRelease::id)
                    when (val committed = commitPage(
                        storyId,
                        mapping,
                        mode,
                        initialState,
                        currentGraph,
                        fetched.page,
                        releases,
                        fullReleaseIds,
                        startedFromBeginning,
                    )) {
                        is PageCommit.Failed -> outcome = PageSyncResult.Failed(committed.failure)
                        is PageCommit.Success -> {
                            graph = committed.graph
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
            checkpoint = state?.sourceCheckpoint(),
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
        graph: ChapterGraphSnapshot,
        page: ChapterSourcePage,
        releases: List<ChapterRelease>,
        fullReleaseIds: Set<ChapterReleaseId>,
        startedFromBeginning: Boolean,
    ): PageCommit {
        val merged = graph.releases.associateByTo(linkedMapOf(), ChapterRelease::id).apply {
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
        val plan = aggregation.plan(storyId, graph.chapters, activeReleases, graph.overrides)
        return when (val commit = chapters.commit(ChapterMutation(storyId, releases, plan, state))) {
            is ChapterCommitResult.Failure -> PageCommit.Failed(
                ChapterSyncFailure(mapping.pluginId, commit.code, commit.retryable),
            )
            ChapterCommitResult.Success -> PageCommit.Success(graph.afterCommit(activeReleases, plan))
        }
    }

    private fun ChapterSourcePage.toReleases(
        storyId: StoryId,
        mapping: ContentMapping,
    ): List<ChapterRelease> = releases.map { sourceRelease ->
        val normalized = releaseNormalizer.normalize(sourceRelease)
        ChapterRelease(
            id = releaseId(mapping, sourceRelease.sourceReleaseId),
            storyId = storyId,
            pluginId = mapping.pluginId,
            sourceStoryId = mapping.sourceStoryId,
            sourceReleaseId = sourceRelease.sourceReleaseId,
            displayLabel = normalized.displayLabel,
            parsedLabel = normalized.parsedLabel,
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
        val sourceCheckpoint = previous?.sourceCheckpoint()
        val phase = when {
            mode == ChapterListMode.RECENT -> ChapterSyncPhase.FULL
            mode == ChapterListMode.FULL && completed && sourceCheckpoint != null -> ChapterSyncPhase.INCREMENTAL
            mode == ChapterListMode.FULL -> ChapterSyncPhase.FULL
            sourceCheckpoint != null -> ChapterSyncPhase.INCREMENTAL
            else -> ChapterSyncPhase.FULL
        }
        return ChapterSyncState(
            storyId = storyId,
            pluginId = mapping.pluginId,
            sourceStoryId = mapping.sourceStoryId,
            phase = phase,
            cursor = if (mode == ChapterListMode.RECENT) null else page.nextToken,
            checkpoint = if (mode == ChapterListMode.RECENT) previous?.sourceCheckpoint() else sourceCheckpoint,
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
        data class Success(val graph: ChapterGraphSnapshot) : PageCommit
        data class Failed(val failure: ChapterSyncFailure) : PageCommit
    }

    private companion object {
        const val MAX_PAGES = 100
        const val UNDETERMINED_LANGUAGE = "und"
        const val PAGE_LOOP = "chapter.source_page_loop"
        const val PAGE_LIMIT = "chapter.source_page_limit"
    }
}

internal sealed interface PageSyncResult {
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

private fun ChapterGraphSnapshot.afterCommit(
    activeReleases: List<ChapterRelease>,
    plan: AggregationPlan,
): ChapterGraphSnapshot {
    val targetByRelease = plan.links.associate { link -> link.releaseId to link.canonicalChapterId }
    val linkedReleases = activeReleases.map { release ->
        targetByRelease[release.id]?.let { target -> release.copy(canonicalChapterId = target) } ?: release
    }
    val releaseIdsByChapter = linkedReleases
        .mapNotNull { release -> release.canonicalChapterId?.let { it to release.id } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, releaseIds) -> releaseIds.toSet() }
    val chaptersById = linkedMapOf<
        app.openstory.common.id.CanonicalChapterId,
        app.openstory.chapters.model.CanonicalChapter,
    >()
    chapters.forEach { chapter -> chaptersById[chapter.id] = chapter }
    plan.creates.forEach { chapter -> chaptersById[chapter.id] = chapter }
    val linkedChapterIds = releaseIdsByChapter.keys
    val updatedChapters = chaptersById.values.map { chapter ->
        chapter.copy(
            tombstoned = when {
                chapter.id in linkedChapterIds -> false
                chapter.id in plan.tombstones -> true
                else -> chapter.tombstoned
            },
            releaseIds = releaseIdsByChapter[chapter.id].orEmpty(),
        )
    }
    return ChapterGraphSnapshot(updatedChapters, linkedReleases, overrides)
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

internal fun ChapterSyncState.sourceCheckpoint(): String? = checkpoint?.takeUnless { it == fingerprint }
