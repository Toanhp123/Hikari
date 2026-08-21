package app.openstory.storage.room.merge

import app.openstory.catalog.fusion.FUSION_POLICY_VERSION
import app.openstory.catalog.orchestration.CanonicalEngineWorkType
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.CanonicalEngineWorkEntity
import app.openstory.storage.room.chapters.ChapterAggregationOverrideEntity
import app.openstory.storage.room.chapters.ChapterSyncStateEntity
import app.openstory.storage.room.library.ContentMappingEntity
import app.openstory.storage.room.library.ContentMappingRejectionEntity
import app.openstory.storage.room.library.LibraryEntity
import app.openstory.storage.room.reader.ReadingProgressEntity

internal class RoomStoryMergeApplier(
    private val database: OpenStoryDatabase,
) {
    suspend fun applyPreparedDomainState(plan: PreparedStoryGraphMerge) {
        val ids = listOf(plan.survivorStoryId.value, plan.retiredStoryId.value)
        val catalogDao = database.catalogDao()
        val canonicalDao = database.canonicalCatalogDao()
        val libraryDao = database.libraryDao()
        val chapterDao = database.chapterDao()
        val syncDao = database.chapterSyncDao()
        val progressDao = database.readingProgressDao()

        val state = requireNotNull(canonicalDao.canonicalState(plan.survivorStoryId.value))
        canonicalDao.upsertCanonicalState(
            state.copy(
                health = "REEVALUATING",
                preferenceMode = plan.sourcePreference.mode.name,
                pinnedPluginId = plan.sourcePreference.pinnedSource?.pluginId?.value,
                pinnedSourceId = plan.sourcePreference.pinnedSource?.sourceId,
                preferenceRevision = plan.sourcePreference.revision,
            ),
        )
        catalogDao.moveEntries(plan.retiredStoryId.value, plan.survivorStoryId.value)

        ids.forEach { storyId -> libraryDao.delete(storyId) }
        plan.libraryPlan.entry?.let { entry ->
            libraryDao.upsertLibrary(
                LibraryEntity(entry.storyId.value, entry.status.name, entry.addedAt, entry.updatedAt),
            )
        }

        libraryDao.deleteMappingsForStories(ids)
        libraryDao.upsertMappings(
            plan.mappingPlan.mappings.map { mapping ->
                ContentMappingEntity(
                    mapping.storyId.value,
                    mapping.pluginId.value,
                    mapping.sourceStoryId,
                    mapping.origin.name,
                    mapping.policyVersion,
                    mapping.updatedAt,
                )
            },
        )
        libraryDao.deleteRejectionsForStories(ids)
        libraryDao.upsertRejections(
            plan.mappingPlan.rejections.map { rejection ->
                ContentMappingRejectionEntity(
                    rejection.storyId.value,
                    rejection.pluginId.value,
                    rejection.sourceStoryId,
                    rejection.policyVersion,
                    rejection.rejectedAt,
                )
            },
        )

        chapterDao.moveChapterOwnership(plan.retiredStoryId.value, plan.survivorStoryId.value)
        chapterDao.moveReleaseOwnership(plan.retiredStoryId.value, plan.survivorStoryId.value)
        chapterDao.deleteOverridesForStories(ids)
        chapterDao.upsertOverrides(
            plan.chapterPlan.preservedOverrides.map { override ->
                ChapterAggregationOverrideEntity(
                    storyId = plan.survivorStoryId.value,
                    chapterReleaseId = override.releaseId.value,
                    canonicalChapterId = override.canonicalChapterId?.value,
                    kind = override.kind.name,
                )
            },
        )
        syncDao.deleteStatesForStory(plan.retiredStoryId.value)
        plan.chapterPlan.syncKeysToInvalidate.forEach { key ->
            syncDao.deleteState(plan.survivorStoryId.value, key.pluginId.value, key.sourceStoryId)
        }
        syncDao.upsertAll(
            plan.chapterPlan.syncStatesToMove.map { stateToMove ->
                ChapterSyncStateEntity(
                    storyId = stateToMove.storyId.value,
                    pluginId = stateToMove.pluginId.value,
                    sourceStoryId = stateToMove.sourceStoryId,
                    phase = stateToMove.phase.name,
                    cursor = stateToMove.cursor,
                    checkpoint = stateToMove.checkpoint,
                    fingerprint = stateToMove.fingerprint,
                    updatedAtEpochMillis = stateToMove.updatedAtEpochMillis,
                )
            },
        )

        progressDao.deleteForStories(ids)
        progressDao.upsertAll(
            plan.progressPlan.progressRows.map { progress ->
                ReadingProgressEntity(
                    storyId = progress.storyId.value,
                    canonicalChapterId = progress.canonicalChapterId.value,
                    chapterReleaseId = progress.releaseId.value,
                    contentFingerprint = progress.contentFingerprint,
                    blockId = progress.position.blockId,
                    characterOffset = progress.position.characterOffset,
                    fraction = progress.position.fraction,
                    completedAtEpochMillis = progress.completedAtEpochMillis,
                    updatedAtEpochMillis = progress.updatedAtEpochMillis,
                )
            },
        )
    }

    suspend fun validatePostMoveState(plan: PreparedStoryGraphMerge) {
        val retired = plan.retiredStoryId.value
        check(database.catalogDao().entriesForStory(retired).isEmpty())
        check(database.libraryDao().find(retired) == null)
        check(database.libraryDao().mappingsForStory(retired).isEmpty())
        check(database.libraryDao().rejectionsForStory(retired).isEmpty())
        check(database.chapterDao().groups(retired).isEmpty())
        check(database.chapterDao().releases(retired).isEmpty())
        check(database.chapterDao().overrides(retired).isEmpty())
        check(database.chapterSyncDao().statesForStory(retired).isEmpty())
        check(database.readingProgressDao().progressForStory(retired).isEmpty())

        val survivor = plan.survivorStoryId.value
        val ownedSources = database.catalogDao().entriesForStory(survivor)
            .mapTo(hashSetOf()) { it.pluginId to it.sourceId }
        plan.sourcePreference.pinnedSource?.let { pinned ->
            check((pinned.pluginId.value to pinned.sourceId) in ownedSources) {
                "Merged pinned source is not owned by survivor"
            }
        }
        val chapters = database.chapterDao().groups(survivor).associateBy { it.chapter.canonicalChapterId }
        database.chapterDao().releases(survivor).forEach { release ->
            release.canonicalChapterId?.let { chapterId ->
                check(chapterId in chapters) {
                    "Release points to a chapter outside survivor graph: ${release.chapterReleaseId}"
                }
            }
        }
        database.readingProgressDao().progressForStory(survivor).forEach { progress ->
            check(progress.canonicalChapterId in chapters) {
                "Reading progress points to a chapter outside survivor graph: ${progress.canonicalChapterId}"
            }
            val release = database.chapterDao().findRelease(progress.chapterReleaseId)
            check(release != null && release.storyId == survivor) {
                "Reading progress points to a release outside survivor graph: ${progress.chapterReleaseId}"
            }
        }
    }

    suspend fun markPostMergeWork(plan: PreparedStoryGraphMerge) {
        val dao = database.canonicalCatalogDao()
        dao.upsertWork(
            CanonicalEngineWorkEntity(
                storyId = plan.survivorStoryId.value,
                workType = CanonicalEngineWorkType.FUSION_REBUILD.name,
                reason = "story-merged",
                attemptCount = 0,
                nextAttemptAtEpochMillis = 0,
                lastErrorCode = null,
                requiredPolicyVersion = FUSION_POLICY_VERSION,
            ),
        )
        dao.upsertWork(
            CanonicalEngineWorkEntity(
                storyId = plan.survivorStoryId.value,
                workType = CanonicalEngineWorkType.RECONCILIATION_REEVALUATION.name,
                reason = "story-merged",
                attemptCount = 0,
                nextAttemptAtEpochMillis = 0,
                lastErrorCode = null,
                requiredPolicyVersion = plan.request.reconciliationPolicyVersion,
            ),
        )
        val needsDerived = plan.mappingPlan.pluginsToRecompute.isNotEmpty() ||
            plan.chapterPlan.syncKeysToInvalidate.isNotEmpty() ||
            plan.chapterPlan.requiresDerivedReaggregation
        if (needsDerived) {
            dao.upsertWork(
                CanonicalEngineWorkEntity(
                    storyId = plan.survivorStoryId.value,
                    workType = CanonicalEngineWorkType.POST_MERGE_DERIVED.name,
                    reason = "story-merge-derived-state",
                    attemptCount = 0,
                    nextAttemptAtEpochMillis = 0,
                    lastErrorCode = null,
                    requiredPolicyVersion = null,
                ),
            )
        }
    }

}
