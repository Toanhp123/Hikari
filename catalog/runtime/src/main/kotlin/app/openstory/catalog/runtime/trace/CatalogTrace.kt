package app.openstory.catalog.runtime.trace

object CatalogTrace {
    const val ACTIVATION_START = "HikariV2:catalog-activation-start"
    const val STORAGE_READY = "HikariV2:catalog-storage-ready"
    const val DISCOVER_FIRST_SNAPSHOT = "HikariV2:discover-first-snapshot"
    const val DISCOVER_FIRST_COVER = "HikariV2:discover-first-cover"
    const val DISCOVER_CONTENT_READY = "HikariV2:discover-content-ready"
    const val STORY_DETAIL_REQUESTED = "HikariV2:story-detail-requested"
    const val STORY_PROJECTION_RECEIVED = "HikariV2:story-projection-received"
    const val STORY_DETAIL_CONTENT_READY = "HikariV2:story-detail-content-ready"
    const val STORY_UI_PUBLISHED = "HikariV2:story-ui-published"
    const val STORY_HERO_MATERIALIZATION = "HikariV2:story-hero-materialization"
    const val STORY_BODY_MATERIALIZATION = "HikariV2:story-body-materialization"

    @JvmField
    val labels = listOf(
        ACTIVATION_START,
        STORAGE_READY,
        DISCOVER_FIRST_SNAPSHOT,
        DISCOVER_FIRST_COVER,
        DISCOVER_CONTENT_READY,
        STORY_DETAIL_REQUESTED,
        STORY_PROJECTION_RECEIVED,
        STORY_DETAIL_CONTENT_READY,
        STORY_UI_PUBLISHED,
        STORY_HERO_MATERIALIZATION,
        STORY_BODY_MATERIALIZATION,
    )
}

fun interface CatalogTraceSink {
    fun mark(name: String)
}
