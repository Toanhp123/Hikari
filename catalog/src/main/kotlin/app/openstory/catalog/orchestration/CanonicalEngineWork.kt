package app.openstory.catalog.orchestration

import app.openstory.common.id.StoryId

enum class CanonicalEngineWorkType {
    FUSION_REBUILD,
    RECONCILIATION_REEVALUATION,
    POST_MERGE_DERIVED,
    POLICY_REEVALUATION,
}

object CanonicalEngineWorkReasons {
    const val SOURCE_SUMMARY_CHANGED = "source_summary_changed"
    const val SOURCE_FULL_CHANGED = "source_full_changed"
    const val SOURCE_AVAILABILITY_CHANGED = "source_availability_changed"
    const val SOURCE_LINKED = "source_linked"
    const val SOURCE_UNLINKED = "source_unlinked"
    const val SOURCE_PREFERENCE_CHANGED = "source_preference_changed"
    const val REVIEW_RESOLVED = "review_resolved"
    const val STORY_MERGED = "story_merged"
    const val STORY_MERGE_REVERSED = "story_merge_reversed"
    const val POLICY_VERSION_CHANGED = "policy_version_changed"
    const val EVIDENCE_REVISION_CHANGED = "evidence_revision_changed"
    const val RETRY = "retry"

    private const val LEGACY_POST_MERGE_DERIVED = "story-merge-derived-state"
    private const val POST_MERGE_DERIVED_PREFIX = "story-merge-derived:"
    private const val CHAPTER_REAGGREGATION_TOKEN = "chapter-reaggregation"
    private const val MAPPING_RECOMPUTE_TOKEN = "mapping-recompute"
    private const val CHAPTER_SYNC_REFRESH_TOKEN = "chapter-sync-refresh"
    private val postMergeTokens = setOf(
        CHAPTER_REAGGREGATION_TOKEN,
        MAPPING_RECOMPUTE_TOKEN,
        CHAPTER_SYNC_REFRESH_TOKEN,
    )

    fun postMergeDerived(
        reaggregateChapters: Boolean,
        recomputeMappings: Boolean,
        refreshChapterSync: Boolean,
    ): String = postMergeDerived(
        PostMergeDerivedRequirements(
            reaggregateChapters = reaggregateChapters,
            recomputeMappings = recomputeMappings,
            refreshChapterSync = refreshChapterSync,
        ),
    )

    fun postMergeDerived(requirements: PostMergeDerivedRequirements): String {
        val tokens = buildList {
            if (requirements.reaggregateChapters) add(CHAPTER_REAGGREGATION_TOKEN)
            if (requirements.recomputeMappings) add(MAPPING_RECOMPUTE_TOKEN)
            if (requirements.refreshChapterSync) add(CHAPTER_SYNC_REFRESH_TOKEN)
        }
        return POST_MERGE_DERIVED_PREFIX + tokens.joinToString(",")
    }

    fun postMergeDerivedRequirements(reason: String): PostMergeDerivedRequirements {
        val isLegacyOrUnknown = reason == LEGACY_POST_MERGE_DERIVED || !reason.startsWith(POST_MERGE_DERIVED_PREFIX)
        val tokens = if (isLegacyOrUnknown) {
            emptySet()
        } else {
            reason.removePrefix(POST_MERGE_DERIVED_PREFIX)
                .split(',')
                .filter(String::isNotBlank)
                .toSet()
        }
        val hasInvalidTokens = tokens.isEmpty() || tokens.any { it !in postMergeTokens }
        return if (isLegacyOrUnknown || hasInvalidTokens) {
            allPostMergeDerivedRequirements()
        } else {
            PostMergeDerivedRequirements(
                reaggregateChapters = CHAPTER_REAGGREGATION_TOKEN in tokens,
                recomputeMappings = MAPPING_RECOMPUTE_TOKEN in tokens,
                refreshChapterSync = CHAPTER_SYNC_REFRESH_TOKEN in tokens,
            )
        }
    }

    fun coalescePostMergeDerived(
        existingReason: String?,
        requested: PostMergeDerivedRequirements,
    ): String {
        if (existingReason == null) return postMergeDerived(requested)
        val existing = postMergeDerivedRequirements(existingReason)
        return postMergeDerived(
            PostMergeDerivedRequirements(
                reaggregateChapters = existing.reaggregateChapters || requested.reaggregateChapters,
                recomputeMappings = existing.recomputeMappings || requested.recomputeMappings,
                refreshChapterSync = existing.refreshChapterSync || requested.refreshChapterSync,
            ),
        )
    }

    private fun allPostMergeDerivedRequirements() = PostMergeDerivedRequirements(
        reaggregateChapters = true,
        recomputeMappings = true,
        refreshChapterSync = true,
    )
}

data class CanonicalEngineWorkItem(
    val storyId: StoryId,
    val type: CanonicalEngineWorkType,
    val reason: String,
    val requiredPolicyVersion: Int?,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long,
    val lastFailureCode: String?,
) {
    init {
        require(reason.isNotBlank())
        require(requiredPolicyVersion == null || requiredPolicyVersion > 0)
        require(attemptCount >= 0)
        require(nextAttemptAtEpochMillis >= 0L)
        require(lastFailureCode == null || lastFailureCode.isNotBlank())
    }
}

interface CanonicalEngineWorkRepository {
    /** Marks dirty and returns the exact persisted snapshot for race-safe completion/retry. */
    suspend fun markDirty(
        storyId: StoryId,
        type: CanonicalEngineWorkType,
        reason: String,
        requiredPolicyVersion: Int? = null,
    ): CanonicalEngineWorkItem

    suspend fun claimReady(nowEpochMillis: Long, limit: Int): List<CanonicalEngineWorkItem>

    /** Completes only the exact snapshot that was processed; a newer coalesced row must survive. */
    suspend fun complete(item: CanonicalEngineWorkItem): Boolean

    /** Retries only the exact snapshot that failed; a newer coalesced row must survive. */
    suspend fun retry(item: CanonicalEngineWorkItem, failureCode: String, nextAttemptAtEpochMillis: Long)

    /** Parks an invariant-failed snapshot outside the automatic retry clock. */
    suspend fun blockInvariant(item: CanonicalEngineWorkItem, failureCode: String) {
        retry(item, failureCode, Long.MAX_VALUE)
    }

    /** Bounded parked rows for selected repairable failure classes. */
    suspend fun blocked(
        failureCodes: Set<String>,
        limit: Int,
    ): List<CanonicalEngineWorkItem> = emptyList()

    /** Requeues only the exact parked snapshot; a newer row is never overwritten. */
    suspend fun requeueBlocked(item: CanonicalEngineWorkItem): CanonicalEngineWorkItem? = null

    /** Earliest non-parked queue time, including already-ready rows. */
    suspend fun nextAttemptAtEpochMillis(): Long? = null

    suspend fun supersede(storyId: StoryId, type: CanonicalEngineWorkType)
}

fun interface CanonicalEngineWorkScheduler {
    fun scheduleDrain()
}

object NoOpCanonicalEngineWorkScheduler : CanonicalEngineWorkScheduler {
    override fun scheduleDrain() = Unit
}
