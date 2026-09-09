package app.openstory.catalog.runtime.trace

object CatalogTrace {
    const val ACTIVATION_START = "HikariV2:catalog-activation-start"
    const val STORAGE_READY = "HikariV2:catalog-storage-ready"
    const val DISCOVER_FIRST_SNAPSHOT = "HikariV2:discover-first-snapshot"
    const val DISCOVER_FIRST_COVER = "HikariV2:discover-first-cover"
    const val DISCOVER_CONTENT_READY = "HikariV2:discover-content-ready"
    const val STORY_DETAIL_REQUESTED = "HikariV2:story-detail-requested"
    const val STORY_DETAIL_CONTENT_READY = "HikariV2:story-detail-content-ready"

    @JvmField
    val labels = listOf(
        ACTIVATION_START,
        STORAGE_READY,
        DISCOVER_FIRST_SNAPSHOT,
        DISCOVER_FIRST_COVER,
        DISCOVER_CONTENT_READY,
        STORY_DETAIL_REQUESTED,
        STORY_DETAIL_CONTENT_READY,
    )
}

fun interface CatalogTraceSink {
    fun mark(name: String)
}
