package app.openstory.plugin.host.js

data class JsRuntimeLimits(
    val maxSourceBytes: Int = DEFAULT_MAX_SOURCE_BYTES,
    val maxInputJsonBytes: Int = DEFAULT_MAX_INPUT_JSON_BYTES,
    val maxOutputJsonBytes: Int = DEFAULT_MAX_OUTPUT_JSON_BYTES,
    val maxBridgeMessageBytes: Int = DEFAULT_MAX_BRIDGE_MESSAGE_BYTES,
    val maxHostCalls: Int = 8,
    val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
    val maxDurationMillis: Long = 30_000L,
) {
    init {
        require(maxSourceBytes > 0)
        require(maxInputJsonBytes > 0)
        require(maxOutputJsonBytes > 0)
        require(maxBridgeMessageBytes > 0)
        require(maxHostCalls > 0)
        require(maxResponseBytes in 1..Int.MAX_VALUE.toLong())
        require(maxDurationMillis > 0)
    }

    private companion object {
        const val KIBIBYTE = 1024
        const val MEBIBYTE = KIBIBYTE * KIBIBYTE
        const val DEFAULT_MAX_SOURCE_BYTES = 512 * KIBIBYTE
        const val DEFAULT_MAX_INPUT_JSON_BYTES = 256 * KIBIBYTE
        const val DEFAULT_MAX_OUTPUT_JSON_BYTES = 2 * MEBIBYTE
        const val DEFAULT_MAX_BRIDGE_MESSAGE_BYTES = 256 * KIBIBYTE
        const val DEFAULT_MAX_RESPONSE_BYTES = 8L * MEBIBYTE
    }
}

class JsOperationBudget(
    val limits: JsRuntimeLimits = JsRuntimeLimits(),
) {
    private var hostCalls = 0

    fun consumeHostCall(): Boolean {
        hostCalls += 1
        return hostCalls <= limits.maxHostCalls
    }
}
