package app.openstory.storage.room.catalog

import app.openstory.catalog.orchestration.CanonicalEngineWorkItem
import app.openstory.catalog.orchestration.CanonicalEngineWorkRepository
import app.openstory.catalog.orchestration.CanonicalEngineWorkType
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase

class RoomCanonicalEngineWorkRepository internal constructor(
    private val dao: CanonicalCatalogDao,
) : CanonicalEngineWorkRepository {
    constructor(database: OpenStoryDatabase) : this(database.canonicalCatalogDao())

    override suspend fun markDirty(
        storyId: StoryId,
        type: CanonicalEngineWorkType,
        reason: String,
        requiredPolicyVersion: Int?,
    ) {
        require(reason.isNotBlank())
        dao.upsertWork(
            CanonicalEngineWorkEntity(
                storyId = storyId.value,
                workType = type.name,
                reason = reason,
                attemptCount = 0,
                nextAttemptAtEpochMillis = 0,
                lastErrorCode = null,
                requiredPolicyVersion = requiredPolicyVersion,
            ),
        )
    }

    override suspend fun claimReady(nowEpochMillis: Long, limit: Int): List<CanonicalEngineWorkItem> {
        require(nowEpochMillis >= 0L)
        require(limit > 0)
        return dao.readyWork(nowEpochMillis, limit).map(CanonicalEngineWorkEntity::toModel)
    }

    override suspend fun complete(item: CanonicalEngineWorkItem) {
        dao.deleteWork(item.storyId.value, item.type.name)
    }

    override suspend fun retry(
        item: CanonicalEngineWorkItem,
        failureCode: String,
        nextAttemptAtEpochMillis: Long,
    ) {
        require(failureCode.isNotBlank())
        require(nextAttemptAtEpochMillis >= 0L)
        dao.upsertWork(
            CanonicalEngineWorkEntity(
                storyId = item.storyId.value,
                workType = item.type.name,
                reason = item.reason,
                attemptCount = item.attemptCount + 1,
                nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
                lastErrorCode = failureCode,
                requiredPolicyVersion = item.requiredPolicyVersion,
            ),
        )
    }

    override suspend fun supersede(storyId: StoryId, type: CanonicalEngineWorkType) {
        dao.deleteWork(storyId.value, type.name)
    }
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
