package app.openstory.catalog.feature.trace

import app.openstory.catalog.runtime.trace.CatalogTrace
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal data class CatalogTraceSpan(val name: String, val cookie: Int)

internal data class CatalogTraceSpanTransition(
    val started: List<CatalogTraceSpan> = emptyList(),
    val ended: List<CatalogTraceSpan> = emptyList(),
)

internal class CatalogTraceSpanController {
    private val active = ConcurrentHashMap<String, CatalogTraceSpan>()
    private val nextCookie = AtomicInteger(1)

    fun onMark(name: String): CatalogTraceSpanTransition = when (name) {
        CatalogTrace.ACTIVATION_START -> CatalogTraceSpanTransition(
            started = listOf(
                CatalogTrace.STORAGE_READY,
                CatalogTrace.DISCOVER_FIRST_SNAPSHOT,
                CatalogTrace.DISCOVER_FIRST_COVER,
                CatalogTrace.DISCOVER_CONTENT_READY,
            ).mapNotNull(::start),
        )
        CatalogTrace.STORAGE_READY,
        CatalogTrace.DISCOVER_FIRST_SNAPSHOT,
        CatalogTrace.DISCOVER_FIRST_COVER,
        CatalogTrace.DISCOVER_CONTENT_READY,
        CatalogTrace.STORY_DETAIL_CONTENT_READY,
        -> CatalogTraceSpanTransition(ended = listOfNotNull(active.remove(name)))
        CatalogTrace.STORY_DETAIL_REQUESTED -> {
            val ended = active.remove(CatalogTrace.STORY_DETAIL_CONTENT_READY)
            CatalogTraceSpanTransition(
                started = listOfNotNull(start(CatalogTrace.STORY_DETAIL_CONTENT_READY)),
                ended = listOfNotNull(ended),
            )
        }
        else -> CatalogTraceSpanTransition()
    }

    private fun start(name: String): CatalogTraceSpan? {
        val candidate = CatalogTraceSpan(name, nextCookie.getAndIncrement())
        return candidate.takeIf { active.putIfAbsent(name, candidate) == null }
    }
}
