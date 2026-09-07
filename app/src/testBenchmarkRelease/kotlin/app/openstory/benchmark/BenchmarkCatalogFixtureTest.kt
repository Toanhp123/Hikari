package app.openstory.benchmark

import app.openstory.catalog.model.Score
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkCatalogFixtureTest {
    @Test
    fun `aged catalog keeps every generated score within its scale`() {
        val scores = List(BenchmarkFixtureProfile.AGED.catalogStories, ::benchmarkBrowseScore)

        assertEquals(Score(10.0, 10.0), scores.first())
        assertEquals(Score(0.0, 10.0), scores.last())
        assertTrue(scores.all { score -> score.value in 0.0..score.scale })
    }
}
