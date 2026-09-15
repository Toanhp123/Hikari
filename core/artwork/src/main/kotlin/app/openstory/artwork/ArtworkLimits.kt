package app.openstory.artwork

object ArtworkLimits {
    const val DECODED_MEMORY_BYTES = 32L * 1024 * 1024
    const val ENCODED_DISK_BYTES = 128L * 1024 * 1024
    const val MAX_ENCODED_BYTES = 8L * 1024 * 1024
    const val MANUAL_OFFSCREEN_PREFETCH = 0
    const val MAX_REDIRECTS = 3
    const val CONNECT_TIMEOUT_MILLIS = 5_000L
    const val READ_TIMEOUT_MILLIS = 10_000L
    const val CALL_TIMEOUT_MILLIS = 15_000L
    const val MAX_SOURCE_DIMENSION = 8_192
    const val MAX_SOURCE_PIXELS = 32_000_000L
    const val MAX_LOCATOR_CHARS = 4_096
}
