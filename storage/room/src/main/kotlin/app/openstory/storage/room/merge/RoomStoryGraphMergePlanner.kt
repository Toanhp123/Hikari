package app.openstory.storage.room.merge

import app.openstory.catalog.canonical.CanonicalSourcePreferenceMergePolicy
import app.openstory.catalog.identity.ProtectedContentMappingConflict
import app.openstory.catalog.identity.StoryIdentityInvariantException
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.identity.StoryMergeCandidate
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResolution
import app.openstory.catalog.identity.StorySurvivorSelector
import app.openstory.chapters.merge.ChapterStoryMergeInput
import app.openstory.chapters.merge.ChapterStoryMergePolicy
import app.openstory.common.id.StoryId
import app.openstory.common.merge.DomainMergeDecision
import app.openstory.library.merge.ContentMappingMergeDecision
import app.openstory.library.merge.ContentMappingMergeResolution
import app.openstory.library.merge.ContentMappingStoryMergePolicy
import app.openstory.library.merge.LibraryStoryMergePolicy
import app.openstory.reader.progress.ReadingProgressMergePolicy

const val STORY_MERGE_CONTENT_TYPE_CONFLICT = "story_merge.content_type_conflict"

internal class RoomStoryGraphMergePlanner(
    private val identity: StoryIdentityRepository,
    private val reader: StoryMergeSnapshotReader,
    private val survivorSelector: StorySurvivorSelector = StorySurvivorSelector(),
    private val sourcePreferencePolicy: CanonicalSourcePreferenceMergePolicy = CanonicalSourcePreferenceMergePolicy(),
    private val libraryPolicy: LibraryStoryMergePolicy = LibraryStoryMergePolicy(),
    private val mappingPolicy: ContentMappingStoryMergePolicy = ContentMappingStoryMergePolicy(),
    private val chapterPolicy: ChapterStoryMergePolicy = ChapterStoryMergePolicy(),
    private val progressPolicy: ReadingProgressMergePolicy = ReadingProgressMergePolicy(),
) {
    suspend fun prepare(request: StoryMergeRequest): StoryGraphMergePreparation {
        val resolvedLeft = identity.resolve(request.leftStoryId)
        val resolvedRight = identity.resolve(request.rightStoryId)
        return if (resolvedLeft == resolvedRight) {
            StoryGraphMergePreparation.AlreadyCanonical(resolvedLeft)
        } else {
            prepareDistinct(request, resolvedLeft, resolvedRight)
        }
    }

    private suspend fun prepareDistinct(
        request: StoryMergeRequest,
        resolvedLeft: StoryId,
        resolvedRight: StoryId,
    ): StoryGraphMergePreparation {
        val left = readRequired(resolvedLeft)
        val right = readRequired(resolvedRight)
        return if (left.contentType != right.contentType) {
            StoryGraphMergePreparation.ReviewRequired(setOf(STORY_MERGE_CONTENT_TYPE_CONFLICT))
        } else {
            prepareCompatible(request, left, right)
        }
    }

    private fun prepareCompatible(
        request: StoryMergeRequest,
        left: StoryMergeSnapshot,
        right: StoryMergeSnapshot,
    ): StoryGraphMergePreparation {
        val selection = survivorSelector.select(left.toCandidate(), right.toCandidate())
        val survivor = if (selection.survivor.storyId == left.storyId) left else right
        val retired = if (selection.retired.storyId == left.storyId) left else right
        val reasons = linkedSetOf<String>()
        val protectedConflicts = mutableListOf<ProtectedContentMappingConflict>()

        val preference = when (
            val decision = sourcePreferencePolicy.plan(
                selection.survivor.storyId,
                left.sourcePreference,
                right.sourcePreference,
            )
        ) {
            is DomainMergeDecision.Ready -> decision.value
            is DomainMergeDecision.RequiresReview -> {
                reasons += decision.reasons
                null
            }
        }
        val libraryPlan = libraryPolicy.plan(selection.survivor.storyId, left.libraryEntry, right.libraryEntry)
        val mappingDecision = mappingPolicy.plan(
            survivorId = selection.survivor.storyId,
            leftMappings = left.mappings,
            rightMappings = right.mappings,
            leftRejections = left.rejections,
            rightRejections = right.rejections,
            resolutions = request.resolutions.mapNotNull { resolution ->
                when (resolution) {
                    is StoryMergeResolution.ContentMappingTarget -> ContentMappingMergeResolution(
                        resolution.pluginId,
                        resolution.sourceStoryId,
                    )
                }
            },
        )
        val mappingPlan = when (mappingDecision) {
            is ContentMappingMergeDecision.Ready -> mappingDecision.plan
            is ContentMappingMergeDecision.RequiresReview -> {
                reasons += mappingDecision.reasons
                protectedConflicts += mappingDecision.protectedConflicts.map { conflict ->
                    ProtectedContentMappingConflict(conflict.pluginId, conflict.candidateSourceStoryIds)
                }
                null
            }
        }
        val chapterPlan = when (
            val decision = chapterPolicy.plan(
                ChapterStoryMergeInput(
                    survivorStoryId = selection.survivor.storyId,
                    retiredStoryId = selection.retired.storyId,
                    survivorGraph = survivor.chapterGraph,
                    retiredGraph = retired.chapterGraph,
                    syncStates = survivor.syncStates + retired.syncStates,
                ),
            )
        ) {
            is DomainMergeDecision.Ready -> decision.value
            is DomainMergeDecision.RequiresReview -> {
                reasons += decision.reasons
                null
            }
        }
        val progressPlan = when (
            val decision = progressPolicy.plan(
                selection.survivor.storyId,
                left.readingProgress,
                right.readingProgress,
            )
        ) {
            is DomainMergeDecision.Ready -> decision.value
            is DomainMergeDecision.RequiresReview -> {
                reasons += decision.reasons
                null
            }
        }

        return if (reasons.isNotEmpty()) {
            StoryGraphMergePreparation.ReviewRequired(
                reasons = reasons,
                protectedContentMappingConflicts = protectedConflicts.sortedBy { it.pluginId.value },
            )
        } else {
            readyPreparation(
                request = request,
                selection = selection,
                survivor = survivor,
                retired = retired,
                preference = requireNotNull(preference),
                libraryPlan = libraryPlan,
                mappingPlan = requireNotNull(mappingPlan),
                chapterPlan = requireNotNull(chapterPlan),
                progressPlan = requireNotNull(progressPlan),
            )
        }
    }

    private fun readyPreparation(
        request: StoryMergeRequest,
        selection: app.openstory.catalog.identity.StoryMergeSelection,
        survivor: StoryMergeSnapshot,
        retired: StoryMergeSnapshot,
        preference: app.openstory.catalog.canonical.CanonicalSourcePreference,
        libraryPlan: app.openstory.library.merge.LibraryMergePlan,
        mappingPlan: app.openstory.library.merge.ContentMappingMergePlan,
        chapterPlan: app.openstory.chapters.merge.ChapterStoryMergePlan,
        progressPlan: app.openstory.reader.progress.ReadingProgressMergePlan,
    ): StoryGraphMergePreparation.Ready = StoryGraphMergePreparation.Ready(
        PreparedStoryGraphMerge(
            request = request,
            survivorStoryId = selection.survivor.storyId,
            retiredStoryId = selection.retired.storyId,
            expectedVersion = StoryGraphVersion(
                survivorIdentityRevision = survivor.identityRevision,
                retiredIdentityRevision = retired.identityRevision,
                survivorAuthoritativeFingerprint = survivor.authoritativeFingerprint(),
                retiredAuthoritativeFingerprint = retired.authoritativeFingerprint(),
            ),
            sourceKeysToMove = retired.sourceKeys,
            sourcePreference = preference,
            libraryPlan = libraryPlan,
            mappingPlan = mappingPlan,
            chapterPlan = chapterPlan,
            progressPlan = progressPlan,
            footprintBeforeMerge = linkedMapOf(
                selection.survivor.storyId to survivor.footprint(),
                selection.retired.storyId to retired.footprint(),
            ),
        ),
    )

    private suspend fun readRequired(storyId: StoryId): StoryMergeSnapshot =
        reader.read(storyId) ?: throw StoryIdentityInvariantException("Missing active Story graph for ${storyId.value}")

    private fun StoryMergeSnapshot.toCandidate() = StoryMergeCandidate(
        storyId = storyId,
        identityRevision = identityRevision,
        createdAtEpochMillis = createdAtEpochMillis,
        footprint = footprint(),
    )
}
