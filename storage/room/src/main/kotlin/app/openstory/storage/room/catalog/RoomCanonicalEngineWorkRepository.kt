package app.openstory.storage.room.catalog

import androidx.room.withTransaction
import app.openstory.catalog.orchestration.CanonicalEngineWorkItem
import app.openstory.catalog.orchestration.CanonicalEngineWorkReasons
import app.openstory.catalog.orchestration.CanonicalEngineWorkRepository
import app.openstory.catalog.orchestration.CanonicalEngineWorkType
import app.openstory.common.Clock
import app.openstory.common.SystemClock
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase

private const val COALESCED_INVARIANT_FAILURE_CODE = "canonical.maintenance.coalesced_invariant"

class RoomCanonicalEngineWorkRepository(
    private val database: OpenStoryDatabase,
    private val clock: Clock = SystemClock,
) : CanonicalEngineWorkRepository {
    private val dao: CanonicalCatalogDao = database.canonicalCatalogDao()

    override suspend fun markDirty(
        storyId: StoryId,
        type: CanonicalEngineWorkType,
        reason: String,
        requiredPolicyVersion: Int?,
    ): CanonicalEngineWorkItem {
        require(reason.isNotBlank())
        return database.withTransaction {
            val item = coalesceDirtyCanonicalEngineWork(
                current = dao.work(storyId.value, type.name),
                storyId = storyId,
                type = type,
                reason = reason,
                requiredPolicyVersion = requiredPolicyVersion,
                nowEpochMillis = clock.nowEpochMillis(),
            )
            dao.upsertWork(item)
            item.toModel()
        }
    }

    override suspend fun claimReady(nowEpochMillis: Long, limit: Int): List<CanonicalEngineWorkItem> {
        require(nowEpochMillis >= 0L)
        require(limit > 0)
        return dao.readyWork(nowEpochMillis, limit).map(CanonicalEngineWorkEntity::toModel)
    }

    override suspend fun complete(item: CanonicalEngineWorkItem): Boolean = database.withTransaction {
        val current = dao.work(item.storyId.value, item.type.name)
        if (current?.toModel() != item) return@withTransaction false
        dao.deleteWork(item.storyId.value, item.type.name)
        true
    }

    override suspend fun retry(
        item: CanonicalEngineWorkItem,
        failureCode: String,
        nextAttemptAtEpochMillis: Long,
    ) {
        require(failureCode.isNotBlank())
        require(nextAttemptAtEpochMillis >= 0L)
        database.withTransaction {
            val current = dao.work(item.storyId.value, item.type.name)
            if (current?.toModel() == item) {
                dao.upsertWork(
                    current.copy(
                        attemptCount = item.attemptCount + 1,
                        nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
                        lastErrorCode = failureCode,
                    ),
                )
            }
        }
    }

    override suspend fun blockInvariant(item: CanonicalEngineWorkItem, failureCode: String) {
        require(failureCode.isNotBlank())
        database.withTransaction {
            val current = dao.work(item.storyId.value, item.type.name)
            if (current?.toModel() == item) {
                dao.upsertWork(
                    current.copy(
                        nextAttemptAtEpochMillis = Long.MAX_VALUE,
                        lastErrorCode = failureCode,
                    ),
                )
            }
        }
    }

    override suspend fun blocked(
        failureCodes: Set<String>,
        limit: Int,
    ): List<CanonicalEngineWorkItem> {
        require(failureCodes.isNotEmpty())
        require(failureCodes.all(String::isNotBlank))
        require(limit > 0)
        return dao.blockedWork(
            blockedEpochMillis = Long.MAX_VALUE,
            failureCodes = failureCodes.sorted(),
            limit = limit,
        ).map(CanonicalEngineWorkEntity::toModel)
    }

    override suspend fun requeueBlocked(item: CanonicalEngineWorkItem): CanonicalEngineWorkItem? =
        database.withTransaction {
            val current = dao.work(item.storyId.value, item.type.name)
            if (current?.toModel() != item || current.nextAttemptAtEpochMillis != Long.MAX_VALUE) {
                return@withTransaction null
            }
            val requeued = current.copy(
                attemptCount = 0,
                nextAttemptAtEpochMillis = clock.nowEpochMillis(),
                lastErrorCode = null,
            )
            dao.upsertWork(requeued)
            requeued.toModel()
        }

    override suspend fun nextAttemptAtEpochMillis(): Long? =
        dao.earliestRunnableWorkAttempt(Long.MAX_VALUE)

    override suspend fun supersede(storyId: StoryId, type: CanonicalEngineWorkType) {
        dao.deleteWork(storyId.value, type.name)
    }
}

internal fun coalesceDirtyCanonicalEngineWork(
    current: CanonicalEngineWorkEntity?,
    storyId: StoryId,
    type: CanonicalEngineWorkType,
    reason: String,
    requiredPolicyVersion: Int?,
    nowEpochMillis: Long,
): CanonicalEngineWorkEntity {
    require(reason.isNotBlank())
    require(nowEpochMillis >= 0L)
    val coalescedReason = if (type == CanonicalEngineWorkType.POST_MERGE_DERIVED) {
        CanonicalEngineWorkReasons.coalescePostMergeDerived(
            existingReason = current?.reason,
            requested = CanonicalEngineWorkReasons.postMergeDerivedRequirements(reason),
        )
    } else {
        reason
    }
    val requiredVersion = maxPolicyVersion(current?.requiredPolicyVersion, requiredPolicyVersion)
    return if (current?.nextAttemptAtEpochMillis == Long.MAX_VALUE) {
        current.copy(
            reason = coalescedReason,
            requiredPolicyVersion = requiredVersion,
        )
    } else {
        CanonicalEngineWorkEntity(
            storyId = storyId.value,
            workType = type.name,
            reason = coalescedReason,
            attemptCount = 0,
            nextAttemptAtEpochMillis = nextDirtyReadyAt(current, nowEpochMillis),
            lastErrorCode = null,
            requiredPolicyVersion = requiredVersion,
        )
    }
}


private fun nextDirtyReadyAt(
    current: CanonicalEngineWorkEntity?,
    nowEpochMillis: Long,
): Long = when {
    current == null -> nowEpochMillis
    current.lastErrorCode != null -> {
        val previous = current.nextAttemptAtEpochMillis
        if (previous == nowEpochMillis) incrementReadyEpoch(previous) else nowEpochMillis
    }

    current.nextAttemptAtEpochMillis < nowEpochMillis -> nowEpochMillis
    else -> incrementReadyEpoch(current.nextAttemptAtEpochMillis)
}

private fun incrementReadyEpoch(value: Long): Long {
    require(value < Long.MAX_VALUE - 1) { "Runnable engine work timestamp exhausted" }
    return value + 1L
}

internal fun coalesceRekeyedCanonicalEngineWork(
    source: CanonicalEngineWorkEntity,
    target: CanonicalEngineWorkEntity?,
    survivorStoryId: String,
    nowEpochMillis: Long,
): CanonicalEngineWorkEntity {
    require(nowEpochMillis >= 0L)
    if (target == null) {
        return if (source.nextAttemptAtEpochMillis == Long.MAX_VALUE) {
            source.copy(storyId = survivorStoryId)
        } else {
            source.copy(
                storyId = survivorStoryId,
                attemptCount = 0,
                nextAttemptAtEpochMillis = nowEpochMillis,
                lastErrorCode = null,
            )
        }
    }
    require(source.workType == target.workType)
    val type = CanonicalEngineWorkType.valueOf(source.workType)
    val reason = if (type == CanonicalEngineWorkType.POST_MERGE_DERIVED) {
        CanonicalEngineWorkReasons.coalescePostMergeDerived(
            existingReason = target.reason,
            requested = CanonicalEngineWorkReasons.postMergeDerivedRequirements(source.reason),
        )
    } else {
        maxOf(target.reason, source.reason)
    }
    val parked = source.nextAttemptAtEpochMillis == Long.MAX_VALUE ||
        target.nextAttemptAtEpochMillis == Long.MAX_VALUE
    val parkedErrors = if (parked) {
        listOfNotNull(
            target.lastErrorCode.takeIf { target.nextAttemptAtEpochMillis == Long.MAX_VALUE },
            source.lastErrorCode.takeIf { source.nextAttemptAtEpochMillis == Long.MAX_VALUE },
        ).distinct()
    } else {
        emptyList()
    }
    val lastError = when (parkedErrors.size) {
        0 -> null
        1 -> parkedErrors.single()
        else -> COALESCED_INVARIANT_FAILURE_CODE
    }
    return target.copy(
        reason = reason,
        attemptCount = if (parked) maxOf(target.attemptCount, source.attemptCount) else 0,
        nextAttemptAtEpochMillis = if (parked) {
            Long.MAX_VALUE
        } else {
            nextDirtyReadyAt(target, nowEpochMillis)
        },
        lastErrorCode = lastError,
        requiredPolicyVersion = maxPolicyVersion(target.requiredPolicyVersion, source.requiredPolicyVersion),
    )
}

private fun maxPolicyVersion(left: Int?, right: Int?): Int? = when {
    left == null -> right
    right == null -> left
    else -> maxOf(left, right)
}

private fun CanonicalEngineWorkEntity.toModel() = CanonicalEngineWorkItem(
    storyId = StoryId(storyId),
    type = CanonicalEngineWorkType.valueOf(workType),
    reason = reason,
    requiredPolicyVersion = requiredPolicyVersion,
    attemptCount = attemptCount,
    nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
    lastFailureCode = lastErrorCode,
)
