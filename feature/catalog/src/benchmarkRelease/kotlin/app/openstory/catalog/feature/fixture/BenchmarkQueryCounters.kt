package app.openstory.catalog.feature.fixture

import java.util.concurrent.atomic.AtomicInteger

internal object BenchmarkQueryCounters {
    private val discoverObservationQueries = AtomicInteger()
    private val storyObservationQueries = AtomicInteger()

    fun reset() {
        discoverObservationQueries.set(0)
        storyObservationQueries.set(0)
    }

    fun snapshot(transportRequests: Int) = BenchmarkQueryDiagnosticSnapshot(
        transportRequests,
        discoverObservationQueries.get(),
        storyObservationQueries.get(),
    )

    fun recordSqlQuery(sql: String) {
        val normalized = sql.lowercase()
        when {
            "from catalog_source_state as state" in normalized &&
                "left join discover_card as card" in normalized -> discoverObservationQueries.incrementAndGet()
            "from story_source_identity as identity" in normalized &&
                "left join story_detail as detail" in normalized -> storyObservationQueries.incrementAndGet()
            STORY_CHILD_TABLES.any { table -> "from $table" in normalized } ->
                storyObservationQueries.incrementAndGet()
        }
    }

    private val STORY_CHILD_TABLES = listOf("story_author", "story_artist", "story_genre")
}
