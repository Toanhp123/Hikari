package app.openstory.catalog.domain.read

import app.openstory.catalog.domain.identity.StorySourceRef
import kotlinx.coroutines.flow.Flow

interface StoryDetailReadPort {
    fun observe(ref: StorySourceRef): Flow<StoryDetailProjection?>
}
