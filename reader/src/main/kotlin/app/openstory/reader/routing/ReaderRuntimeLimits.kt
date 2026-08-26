package app.openstory.reader.routing

internal object ReaderRuntimeLimits {
    const val MAX_TOTAL_FOREGROUND_ATTEMPTS = 7
    const val MAX_FOREGROUND_REMOTE_ATTEMPTS = 4
    const val MAX_CONCURRENT_FOREGROUND_REMOTE = 2
    const val MAX_CONCURRENT_PREFETCH_REMOTE = 1
    const val MAX_CONCURRENT_REMOTE_PER_SOURCE = 1
}
