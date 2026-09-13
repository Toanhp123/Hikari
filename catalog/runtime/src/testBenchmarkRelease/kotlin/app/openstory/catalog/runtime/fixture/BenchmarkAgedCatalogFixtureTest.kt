package app.openstory.catalog.runtime.fixture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BenchmarkAgedCatalogFixtureTest {
    @Test
    fun unrelatedRowIdentitiesAreDeterministicAndUnique() {
        assertEquals("aged-story-00000", agedStoryIdentity(0).storyId)
        assertEquals("aged-source-04999", agedStoryIdentity(LAST_FIXTURE_INDEX).sourceKey)
        assertNotEquals(agedStoryIdentity(FIRST_DISTINCT_INDEX), agedStoryIdentity(SECOND_DISTINCT_INDEX))
    }

    private companion object {
        const val LAST_FIXTURE_INDEX = 4_999
        const val FIRST_DISTINCT_INDEX = 41
        const val SECOND_DISTINCT_INDEX = 42
    }
}
