package app.openstory.catalog.feature.plugin

import app.openstory.artwork.remote.ArtworkTransport
import app.openstory.artwork.remote.ArtworkTransportRequest
import app.openstory.artwork.remote.ArtworkTransportResponse
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

internal data class ControlledPluginRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?,
)

internal data class ControlledPluginResponse(
    val status: Int,
    val body: String,
)

internal data class ControlledCoverResponse(
    val statusCode: Int,
    val contentType: String? = null,
    val bytes: ByteArray = byteArrayOf(),
    val redirectLocation: String? = null,
    val contentLength: Long? = bytes.size.toLong(),
)

internal class ControlledPluginTransport(
    private val handler: suspend (ControlledPluginRequest) -> ControlledPluginResponse,
    private val coverHandler: suspend (ArtworkTransportRequest) -> ControlledCoverResponse = {
        error("Unexpected cover request: ${it.uri}")
    },
) : ArtworkTransport {
    val pluginRequests = mutableListOf<ControlledPluginRequest>()
    val coverRequestCount = AtomicInteger()

    suspend fun execute(request: ControlledPluginRequest): ControlledPluginResponse {
        pluginRequests += request
        return handler(request)
    }

    override suspend fun execute(request: ArtworkTransportRequest): ArtworkTransportResponse {
        coverRequestCount.incrementAndGet()
        val response = coverHandler(request)
        return object : ArtworkTransportResponse {
            override val statusCode = response.statusCode
            override val redirectLocation = response.redirectLocation
            override val contentType = response.contentType
            override val contentLength = response.contentLength
            override val body: InputStream = ByteArrayInputStream(response.bytes)

            override fun close() {
                body.close()
            }
        }
    }
}
