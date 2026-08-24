package app.openstory.catalog.identity

import app.openstory.common.id.StoryId

/**
 * Read-only projection of the same meaningful user-state footprint used by Story merge planning.
 * Presentation may use it for priority only; it must never influence reconciliation semantics.
 */
fun interface StoryUserStateFootprintReader {
    suspend fun read(storyIds: Set<StoryId>): Map<StoryId, UserStateFootprint>
}
