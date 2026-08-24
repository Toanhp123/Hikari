package app.openstory.storage.room.catalog

import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

internal fun StoryIdentityRepository.observeResolvedSet(storyIds: Set<StoryId>): Flow<Set<StoryId>> {
    if (storyIds.isEmpty()) return flowOf(emptySet())
    val ordered = storyIds.sortedBy(StoryId::value)
    return combine(ordered.map { storyId -> observeResolved(storyId) }) { resolved -> resolved.toSet() }
}
