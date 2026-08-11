package app.openstory.chapters.sync

import app.openstory.chapters.aggregation.ChapterAggregationEngine
import app.openstory.chapters.normalization.ChapterLabelParser
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.repository.ChapterSyncPhase
import app.openstory.chapters.source.ChapterListMode
import app.openstory.chapters.source.ChapterSource
import app.openstory.chapters.source.ChapterSourceRegistry
import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope

class ChapterSyncService(
    private val mappings: ContentMappingRepository,
    private val sources: ChapterSourceRegistry,
    chapters: ChapterRepository,
    aggregation: ChapterAggregationEngine,
    parser: ChapterLabelParser,
    clock: Clock,
) {
    private val pageSync = ChapterPageSynchronizer(chapters, aggregation, parser, clock)

    suspend fun sync(storyId: StoryId): ChapterSyncReport = try {
        syncProtectedMappings(storyId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ChapterSyncReport.Failure(listOf(ChapterSyncFailure(null, SYNC_FAILED, true)))
    }

    private suspend fun syncProtectedMappings(storyId: StoryId): ChapterSyncReport {
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
        val state = pageSync.state(storyId, mapping)
        var releaseCount = 0
        if (state == null) {
            when (val recent = pageSync.sync(storyId, mapping, source, ChapterListMode.RECENT, null)) {
                is PageSyncResult.Failed -> return SyncResult.Failed(recent.failure)
                is PageSyncResult.Completed -> releaseCount += recent.releaseCount
            }
        }
        val currentState = pageSync.state(storyId, mapping)
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
