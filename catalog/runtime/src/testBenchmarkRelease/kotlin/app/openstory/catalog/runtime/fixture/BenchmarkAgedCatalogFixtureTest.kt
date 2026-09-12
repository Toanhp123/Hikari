package app.openstory.catalog.runtime.fixture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BenchmarkAgedCatalogFixtureTest {
    @Test
    fun unrelatedRowIdentitiesAreDeterministicAndUnique() {
        assertEquals("aged-story-00000", agedStoryIdentity(0).storyId)
        assertEquals("aged-source-04999", agedStoryIdentity(4_999).sourceKey)
        assertNotEquals(agedStoryIdentity(41), agedStoryIdentity(42))
    }
}
