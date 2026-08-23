package app.openstory.storage.room.catalog

import androidx.room.withTransaction
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.orchestration.CatalogChangeOutboxRepository
import app.openstory.catalog.orchestration.CatalogEvidenceChange
import app.openstory.catalog.orchestration.CatalogEvidenceLevel
import app.openstory.catalog.orchestration.toDeferredCanonicalWorkRequests
import app.openstory.common.Clock
import app.openstory.common.SystemClock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase

class RoomCatalogChangeOutboxRepository(
    private val database: OpenStoryDatabase,
    private val clock: Clock = SystemClock,
) : CatalogChangeOutboxRepository {
    private val dao = database.canonicalCatalogDao()
    override val persistsCatalogChanges: Boolean = true

    override suspend fun materializePending(limit: Int): Int {
        require(limit > 0)
        return database.withTransaction {
            val events = dao.pendingOutbox(limit)
            if (events.isEmpty()) return@withTransaction 0
            val nowEpochMillis = clock.nowEpochMillis()
            val requests = events.map(CatalogChangeOutboxEntity::toEvidenceChange)
                .toDeferredCanonicalWorkRequests()
            val currentByKey = dao.workForStoryIdsChunked(requests.map { it.storyId.value })
                .associateByTo(mutableMapOf()) { it.storyId to it.workType }
            requests.forEach { request ->
                val key = request.storyId.value to request.type.name
                val item = coalesceDirtyCanonicalEngineWork(
                    current = currentByKey[key],
                    storyId = request.storyId,
                    type = request.type,
                    reason = request.reason,
                    requiredPolicyVersion = request.requiredPolicyVersion,
                    nowEpochMillis = nowEpochMillis,
                )
                dao.upsertWork(item)
                currentByKey[key] = item
            }
            check(dao.deleteOutboxThrough(events.last().eventId) == events.size)
            events.size
        }
    }
}

private fun CatalogChangeOutboxEntity.toEvidenceChange() = CatalogEvidenceChange(
    storyId = StoryId(storyId),
    sourceKey = SourceKey(PluginId(pluginId), sourceId),
    identityFingerprintChanged = identityFingerprintChanged,
    fusionFingerprintChanged = fusionFingerprintChanged,
    availabilityChanged = availabilityChanged,
    level = CatalogEvidenceLevel.valueOf(evidenceLevel),
)
