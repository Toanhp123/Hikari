package app.openstory.storage.room.merge

import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.reconciliation.StoryMergeLineage
import app.openstory.catalog.reconciliation.StoryMergeLineageReader
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.StoryMergeEventEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RoomStoryMergeLineageReader(
    database: OpenStoryDatabase,
) : StoryMergeLineageReader {
    private val dao = database.canonicalCatalogDao()

    override suspend fun lineagesFor(storyId: StoryId): List<StoryMergeLineage> {
        val pendingStoryIds = ArrayDeque<String>().apply { add(storyId.value) }
        val visitedStoryIds = linkedSetOf<String>()
        val events = linkedMapOf<String, StoryMergeEventEntity>()
        while (pendingStoryIds.isNotEmpty()) {
            val current = pendingStoryIds.removeFirst()
            if (!visitedStoryIds.add(current)) continue
            dao.mergeEventsForStory(current).forEach { event ->
                if (events.putIfAbsent(event.mergeEventId, event) == null) {
                    pendingStoryIds.add(event.survivorStoryId)
                    pendingStoryIds.add(event.retiredStoryId)
                }
            }
        }
        return events.values.map(::toLineage)
            .sortedWith(
                compareByDescending<StoryMergeLineage> { it.mergedAtEpochMillis }
                    .thenBy { it.mergeEventId },
            )
    }

    private fun toLineage(event: StoryMergeEventEntity): StoryMergeLineage {
        require(event.reversalPayloadVersion == STORY_MERGE_REVERSAL_PAYLOAD_VERSION) {
            "Unsupported merge reversal payload version ${event.reversalPayloadVersion}"
        }
        val root = Json.parseToJsonElement(event.reversalPayload).jsonObject
        return StoryMergeLineage(
            mergeEventId = event.mergeEventId,
            survivorStoryId = StoryId(event.survivorStoryId),
            retiredStoryId = StoryId(event.retiredStoryId),
            reconciliationCaseId = event.reconciliationCaseId,
            survivorSourceKeysBefore = root.sourceKeys("survivorBefore"),
            retiredSourceKeysBefore = root.sourceKeys("retiredBefore"),
            mergedAtEpochMillis = event.mergedAtEpochMillis,
        )
    }

    private fun JsonObject.sourceKeys(side: String): Set<SourceKey> {
        val sideObject = requireNotNull(get(side)) { "Missing merge lineage side $side" }.jsonObject
        val sourceKeys = requireNotNull(sideObject["sourceKeys"]) {
            "Missing merge lineage sourceKeys for $side"
        }.jsonArray
        return sourceKeys.mapTo(linkedSetOf()) { element ->
            val value = element.jsonObject
            val pluginId = requireNotNull(value["pluginId"]?.jsonPrimitive?.contentOrNull) {
                "Missing merge lineage pluginId for $side"
            }
            val sourceId = requireNotNull(value["sourceId"]?.jsonPrimitive?.contentOrNull) {
                "Missing merge lineage sourceId for $side"
            }
            require(pluginId.isNotBlank() && sourceId.isNotBlank()) { "Blank merge lineage source key for $side" }
            SourceKey(PluginId(pluginId), sourceId)
        }
    }
}
