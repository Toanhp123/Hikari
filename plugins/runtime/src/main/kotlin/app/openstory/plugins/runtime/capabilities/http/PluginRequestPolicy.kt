package app.openstory.plugins.runtime.capabilities.http

import app.openstory.common.id.PluginId

private const val DEFAULT_MAX_REQUESTS = 8
private const val DEFAULT_MAX_REDIRECTS = 5
private const val DEFAULT_MAX_REQUEST_BYTES = 256L * 1024L
private const val DEFAULT_MAX_COMPRESSED_RESPONSE_BYTES = 2L * 1024L * 1024L
private const val DEFAULT_MAX_DECOMPRESSED_RESPONSE_BYTES = 4L * 1024L * 1024L
private const val DEFAULT_TIMEOUT_MILLIS = 20_000L

data class PluginRequestPolicy(
    val pluginId: PluginId,
    val allowedHosts: Set<String>,
    val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
    val maxRequestBytes: Long = DEFAULT_MAX_REQUEST_BYTES,
    val maxCompressedResponseBytes: Long = DEFAULT_MAX_COMPRESSED_RESPONSE_BYTES,
    val maxDecompressedResponseBytes: Long = DEFAULT_MAX_DECOMPRESSED_RESPONSE_BYTES,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    init {
        require(maxRequests > 0 && maxRedirects >= 0) { "HTTP request counts are invalid" }
        require(maxRequestBytes > 0 && maxCompressedResponseBytes > 0) { "HTTP byte budgets are invalid" }
        require(maxDecompressedResponseBytes > 0 && timeoutMillis > 0) { "HTTP budgets are invalid" }
    }

}
