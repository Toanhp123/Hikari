package app.openstory.catalog.feature.trace

import app.openstory.catalog.runtime.trace.CatalogTrace
import app.openstory.catalog.runtime.trace.CatalogTraceSink
import java.util.concurrent.atomic.AtomicBoolean

internal class CatalogUiTrace(
    private val sink: CatalogTraceSink,
) {
    private val contentReady = AtomicBoolean(false)
    private val coverReady = AtomicBoolean(false)
    private val storyUiPublished = AtomicBoolean(false)
    private val storyHeroMaterialized = AtomicBoolean(false)
    private val storyBodyMaterialized = AtomicBoolean(false)

    fun discoverContentReady() {
        if (contentReady.compareAndSet(false, true)) sink.mark(CatalogTrace.DISCOVER_CONTENT_READY)
    }

    fun discoverCoverReady() {
        if (coverReady.compareAndSet(false, true)) sink.mark(CatalogTrace.DISCOVER_FIRST_COVER)
    }

    fun storyUiPublished() {
        if (storyUiPublished.compareAndSet(false, true)) sink.mark(CatalogTrace.STORY_UI_PUBLISHED)
    }

    fun storyHeroMaterialized() {
        if (storyUiPublished.get() && storyHeroMaterialized.compareAndSet(false, true)) {
            sink.mark(CatalogTrace.STORY_HERO_MATERIALIZATION)
        }
    }

    fun storyBodyMaterialized() {
        if (storyUiPublished.get() && storyBodyMaterialized.compareAndSet(false, true)) {
            sink.mark(CatalogTrace.STORY_BODY_MATERIALIZATION)
        }
    }
}
