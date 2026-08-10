package app.openstory.plugins.runtime.execution

private const val DEFAULT_TIMEOUT_MILLIS = 15_000L
private const val DEFAULT_MAX_HEAP_BYTES = 16L * 1024L * 1024L
private const val DEFAULT_MAX_BRIDGE_MESSAGE_BYTES = 256 * 1024
private const val DEFAULT_MAX_OUTPUT_JSON_BYTES = 2 * 1024 * 1024

data class RuntimeLimits(
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val maxHeapBytes: Long = DEFAULT_MAX_HEAP_BYTES,
    val maxBridgeMessageBytes: Int = DEFAULT_MAX_BRIDGE_MESSAGE_BYTES,
    val maxOutputJsonBytes: Int = DEFAULT_MAX_OUTPUT_JSON_BYTES,
) {
    init {
        require(timeoutMillis > 0 && maxHeapBytes > 0) { "Runtime time/heap limits must be positive" }
        require(maxBridgeMessageBytes > 0 && maxOutputJsonBytes > 0) { "Runtime byte limits must be positive" }
    }

}
