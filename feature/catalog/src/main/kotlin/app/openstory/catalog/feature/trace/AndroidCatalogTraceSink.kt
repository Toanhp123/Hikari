package app.openstory.catalog.feature.trace

import android.os.Build
import android.os.Trace
import app.openstory.catalog.runtime.trace.CatalogTraceSink

internal object AndroidCatalogTraceSink : CatalogTraceSink {
    private val spans = CatalogTraceSpanController()

    override fun mark(name: String) {
        val transition = spans.onMark(name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            transition.ended.forEach { span -> Trace.endAsyncSection(span.name, span.cookie) }
            transition.started.forEach { span -> Trace.beginAsyncSection(span.name, span.cookie) }
        }
        Trace.beginSection(name)
        Trace.endSection()
    }
}
