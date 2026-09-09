package app.openstory.catalog.feature.trace

import android.os.Trace
import app.openstory.catalog.runtime.trace.CatalogTraceSink

internal object AndroidCatalogTraceSink : CatalogTraceSink {
    override fun mark(name: String) {
        Trace.beginSection(name)
        Trace.endSection()
    }
}
