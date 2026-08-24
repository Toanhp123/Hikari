package app.openstory.storage.room.merge

import app.openstory.catalog.fusion.FUSION_POLICY_VERSION
import app.openstory.catalog.orchestration.CanonicalEngineWorkReasons
import app.openstory.catalog.orchestration.CanonicalEngineWorkType
import app.openstory.catalog.reconciliation.RECONCILIATION_POLICY_VERSION
import app.openstory.catalog.reconciliation.ReconciliationResolutionOrigin
import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.StoryCanonicalStateEntity
import app.openstory.storage.room.catalog.StoryEntity
import app.openstory.storage.room.catalog.StoryMergeReversalEventEntity
import app.openstory.storage.room.catalog.RoomReconciliationCaseRepository
import app.openstory.storage.room.catalog.coalesceDirtyCanonicalEngineWork
import app.openstory.storage.room.chapters.ChapterAggregationOverrideEntity
import app.openstory.storage.room.chapters.ChapterSyncStateEntity
import app.openstory.storage.room.library.ContentMappingEntity
import app.openstory.storage.room.library.ContentMappingRejectionEntity
import app.openstory.storage.room.library.LibraryEntity
import app.openstory.storage.room.reader.ReadingProgressEntity

internal class RoomStoryMergeReversalWriter(
    private val database: OpenStoryDatabase,
    private val clock: Clock,
    private val reversalEventIdFactory: () -> String,
    private val beforeAudit: suspend () -> Unit = {},
) {
    suspend fun commit(plan: PreparedStoryMergeReversal): StoryMergeReversalWriteResult {
        val audit = plan.audit
        val survivor = audit.survivorBefore
        val retired = audit.retiredBefore
        val survivorId = survivor.storyId.value
        val retiredId = retired.storyId.value
        val canonicalDao = database.canonicalCatalogDao()
        val catalogDao = database.catalogDao()
        val currentSurvivorState = requireNotNull(canonicalDao.canonicalState(survivorId))

        catalogDao.upsertStories(
            listOf(
                StoryEntity(survivorId, survivor.contentType),
                StoryEntity(retiredId, retired.contentType),
            ),
        )
        retired.sourceKeys.forEach { source ->
            check(
                catalogDao.moveEntry(
                    pluginId = source.pluginId.value,
                    sourceId = source.sourceId,
                    expectedStoryId = survivorId,
                    newStoryId = retiredId,
                ) == 1,
            ) { "Historical retired source ownership changed: ${source.pluginId.value}:${source.sourceId}" }
        }

        restoreCanonicalState(currentSurvivorState, survivor, retired)
        restoreLibraryState(survivor, retired)
        restoreChapterState(survivor, retired)
        restoreProgressState(survivor, retired)

        check(
            canonicalDao.deleteRedirect(
                retiredStoryId = retiredId,
                canonicalStoryId = survivorId,
                mergeEventId = plan.event.mergeEventId,
            ) == 1,
        ) { "Forward merge redirect changed before reversal commit" }

        canonicalDao.deleteWork(survivorId, CanonicalEngineWorkType.POST_MERGE_DERIVED.name)
        canonicalDao.deleteWork(retiredId, CanonicalEngineWorkType.POST_MERGE_DERIVED.name)
        val now = clock.nowEpochMillis().also { check(it >= 0L) }
        resolveCorrectionCase(plan, now)
        markReversalWork(StoryId(survivorId), now)
        markReversalWork(StoryId(retiredId), now)

        validateRestoredOwnership(survivor, retired)
        beforeAudit()
        val reversalEventId = reversalEventIdFactory().also { require(it.isNotBlank()) }
        canonicalDao.insertMergeReversalEvent(
            StoryMergeReversalEventEntity(
                reversalEventId = reversalEventId,
                mergeEventId = plan.event.mergeEventId,
                restoredStoryId = retiredId,
                survivingStoryId = survivorId,
                origin = "CONTROLLED_REVIEW",
                reasonCodes = setOf(CanonicalEngineWorkReasons.STORY_MERGE_REVERSED),
                reversedAtEpochMillis = now,
            ),
        )
        return StoryMergeReversalWriteResult(reversalEventId, retired.storyId, survivor.storyId)
    }

    private suspend fun resolveCorrectionCase(
        plan: PreparedStoryMergeReversal,
        resolvedAtEpochMillis: Long,
    ) {
        val caseId = plan.request.expectedReconciliationCaseId ?: return
        val revision = requireNotNull(plan.request.expectedReconciliationCaseRevision)
        check(
            RoomReconciliationCaseRepository(database).resolveSeparateInCurrentTransaction(
                caseId = caseId,
                expectedRevision = revision,
                origin = ReconciliationResolutionOrigin.USER,
                resolvedAtEpochMillis = resolvedAtEpochMillis,
            ),
        ) { "Correction review changed before reversal commit" }
    }

    private suspend fun restoreCanonicalState(
        currentSurvivor: StoryCanonicalStateEntity,
        survivor: StoryMergeReversalAuditSnapshot,
        retired: StoryMergeReversalAuditSnapshot,
    ) {
        val dao = database.canonicalCatalogDao()
        dao.upsertCanonicalState(
            currentSurvivor.copy(
                activeGenerationId = null,
                health = "REEVALUATING",
                preferenceMode = survivor.sourcePreference.mode.name,
                pinnedPluginId = survivor.sourcePreference.pinnedSource?.pluginId?.value,
                pinnedSourceId = survivor.sourcePreference.pinnedSource?.sourceId,
                preferenceRevision = increment(
                    maxOf(currentSurvivor.preferenceRevision, survivor.sourcePreference.revision),
                ),
                identityRevision = increment(currentSurvivor.identityRevision),
                createdAtEpochMillis = survivor.createdAtEpochMillis,
            ),
        )
        dao.upsertCanonicalState(
            StoryCanonicalStateEntity(
                storyId = retired.storyId.value,
                activeGenerationId = null,
                health = "REEVALUATING",
                preferenceMode = retired.sourcePreference.mode.name,
                pinnedPluginId = retired.sourcePreference.pinnedSource?.pluginId?.value,
                pinnedSourceId = retired.sourcePreference.pinnedSource?.sourceId,
                preferenceRevision = increment(retired.sourcePreference.revision),
                identityRevision = increment(retired.identityRevision),
                createdAtEpochMillis = retired.createdAtEpochMillis,
            ),
        )
    }

    private suspend fun restoreLibraryState(
        survivor: StoryMergeReversalAuditSnapshot,
        retired: StoryMergeReversalAuditSnapshot,
    ) {
        val dao = database.libraryDao()
        val ids = listOf(survivor.storyId.value, retired.storyId.value)
        ids.forEach { dao.delete(it) }
        listOfNotNull(survivor.libraryEntry, retired.libraryEntry).forEach { entry ->
            dao.upsertLibrary(LibraryEntity(entry.storyId.value, entry.status.name, entry.addedAt, entry.updatedAt))
        }
        dao.deleteMappingsForStories(ids)
        dao.upsertMappings((survivor.mappings + retired.mappings).map { mapping ->
            ContentMappingEntity(
                storyId = mapping.storyId.value,
                pluginId = mapping.pluginId.value,
                sourceStoryId = mapping.sourceStoryId,
                origin = mapping.origin.name,
                policyVersion = mapping.policyVersion,
                updatedAtEpochMillis = mapping.updatedAt,
            )
        })
        dao.deleteRejectionsForStories(ids)
        dao.upsertRejections((survivor.rejections + retired.rejections).map { rejection ->
            ContentMappingRejectionEntity(
                storyId = rejection.storyId.value,
                pluginId = rejection.pluginId.value,
                sourceStoryId = rejection.sourceStoryId,
                policyVersion = rejection.policyVersion,
                rejectedAtEpochMillis = rejection.rejectedAt,
            )
        })
    }

    private suspend fun restoreChapterState(
        survivor: StoryMergeReversalAuditSnapshot,
        retired: StoryMergeReversalAuditSnapshot,
    ) {
        val chapterDao = database.chapterDao()
        val syncDao = database.chapterSyncDao()
        retired.chapterIds.forEach { chapterId ->
            check(chapterDao.moveChapter(chapterId.value, survivor.storyId.value, retired.storyId.value) == 1) {
                "Historical chapter ownership changed: ${chapterId.value}"
            }
        }
        retired.releaseIds.forEach { releaseId ->
            check(chapterDao.moveRelease(releaseId.value, survivor.storyId.value, retired.storyId.value) == 1) {
                "Historical release ownership changed: ${releaseId.value}"
            }
        }
        val ids = listOf(survivor.storyId.value, retired.storyId.value)
        chapterDao.deleteOverridesForStories(ids)
        chapterDao.upsertOverrides(
            listOf(survivor, retired).flatMap { snapshot ->
                snapshot.manualOverrides.map { override ->
                    ChapterAggregationOverrideEntity(
                        storyId = snapshot.storyId.value,
                        chapterReleaseId = override.releaseId.value,
                        canonicalChapterId = override.canonicalChapterId?.value,
                        kind = override.kind.name,
                    )
                }
            },
        )
        syncDao.deleteStatesForStories(ids)
        syncDao.upsertAll(
            listOf(survivor, retired).flatMap { snapshot ->
                snapshot.syncStates.map { state ->
                    ChapterSyncStateEntity(
                        storyId = snapshot.storyId.value,
                        pluginId = state.pluginId.value,
                        sourceStoryId = state.sourceStoryId,
                        phase = state.phase.name,
                        cursor = state.cursor,
                        checkpoint = state.checkpoint,
                        fingerprint = state.fingerprint,
                        updatedAtEpochMillis = state.updatedAtEpochMillis,
                    )
                }
            },
        )
    }

    private suspend fun restoreProgressState(
        survivor: StoryMergeReversalAuditSnapshot,
        retired: StoryMergeReversalAuditSnapshot,
    ) {
        val dao = database.readingProgressDao()
        dao.deleteForStories(listOf(survivor.storyId.value, retired.storyId.value))
        dao.upsertAll(
            listOf(survivor, retired).flatMap { snapshot ->
                snapshot.readingProgress.map { progress ->
                    ReadingProgressEntity(
                        storyId = snapshot.storyId.value,
                        canonicalChapterId = progress.canonicalChapterId.value,
                        chapterReleaseId = progress.releaseId.value,
                        contentFingerprint = progress.contentFingerprint,
                        blockId = progress.position.blockId,
                        characterOffset = progress.position.characterOffset,
                        fraction = progress.position.fraction,
                        completedAtEpochMillis = progress.completedAtEpochMillis,
                        updatedAtEpochMillis = progress.updatedAtEpochMillis,
                    )
                }
            },
        )
    }

    private suspend fun markReversalWork(storyId: StoryId, nowEpochMillis: Long) {
        val dao = database.canonicalCatalogDao()
        listOf(
            CanonicalEngineWorkType.FUSION_REBUILD to FUSION_POLICY_VERSION,
            CanonicalEngineWorkType.RECONCILIATION_REEVALUATION to RECONCILIATION_POLICY_VERSION,
        ).forEach { (type, policyVersion) ->
            dao.upsertWork(
                coalesceDirtyCanonicalEngineWork(
                    current = dao.work(storyId.value, type.name),
                    storyId = storyId,
                    type = type,
                    reason = CanonicalEngineWorkReasons.STORY_MERGE_REVERSED,
                    requiredPolicyVersion = policyVersion,
                    nowEpochMillis = nowEpochMillis,
                ),
            )
        }
    }

    private suspend fun validateRestoredOwnership(
        survivor: StoryMergeReversalAuditSnapshot,
        retired: StoryMergeReversalAuditSnapshot,
    ) {
        val catalogDao = database.catalogDao()
        val survivorSources = catalogDao.entriesForStory(survivor.storyId.value)
            .mapTo(linkedSetOf()) { it.pluginId to it.sourceId }
        val retiredSources = catalogDao.entriesForStory(retired.storyId.value)
            .mapTo(linkedSetOf()) { it.pluginId to it.sourceId }
        check(survivorSources == survivor.sourceKeys.mapTo(linkedSetOf()) { it.pluginId.value to it.sourceId })
        check(retiredSources == retired.sourceKeys.mapTo(linkedSetOf()) { it.pluginId.value to it.sourceId })
        check(database.canonicalCatalogDao().redirect(retired.storyId.value) == null)
        requireNotNull(RoomStoryMergeReaders(database).read(survivor.storyId))
        requireNotNull(RoomStoryMergeReaders(database).read(retired.storyId))
    }

    private fun increment(value: Long): Long = Math.addExact(value, 1L)
}

internal data class StoryMergeReversalWriteResult(
    val reversalEventId: String,
    val restoredStoryId: StoryId,
    val survivingStoryId: StoryId,
)
