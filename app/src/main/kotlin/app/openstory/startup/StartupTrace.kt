package app.openstory.startup

import android.os.Trace

internal const val TRACE_APPLICATION_CREATED = "HikariV2:application-created"
internal const val TRACE_ACTIVITY_CREATED = "HikariV2:activity-created"
internal const val TRACE_CONTENT_REQUESTED = "HikariV2:content-requested"
internal const val TRACE_FIRST_FRAME = "HikariV2:first-frame"
internal const val TRACE_LAUNCH_STATE_RESOLVED = "HikariV2:launch-state-resolved"
internal const val TRACE_DESTINATION_READY = "HikariV2:destination-ready"

internal val startupTraceLabels: List<String> = listOf(
    TRACE_APPLICATION_CREATED,
    TRACE_ACTIVITY_CREATED,
    TRACE_CONTENT_REQUESTED,
    TRACE_FIRST_FRAME,
    TRACE_LAUNCH_STATE_RESOLVED,
    TRACE_DESTINATION_READY,
)

internal inline fun <T> startupTraceSection(
    name: String,
    block: () -> T,
): T {
    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

internal fun startupTraceMark(name: String) {
    Trace.beginSection(name)
    Trace.endSection()
}
