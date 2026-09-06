package app.openstory.catalog.model

import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogModelBoundaryTest {
    @Test
    fun stableValuesRemainConstructibleWithoutRuntimeDependencies() {
        assertEquals(ContentType.MANGA, Story(StoryId("story:model"), ContentType.MANGA).contentType)
    }
}
