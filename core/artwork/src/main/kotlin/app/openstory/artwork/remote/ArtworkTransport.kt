package app.openstory.artwork.remote

import java.io.Closeable
import java.io.InputStream

data class ArtworkTransportRequest(
    val uri: String,
    val connectTimeoutMillis: Long,
    val readTimeoutMillis: Long,
    val callTimeoutMillis: Long,
)

interface ArtworkTransportResponse : Closeable {
    val statusCode: Int
    val redirectLocation: String?
    val contentType: String?
    val contentLength: Long?
    val body: InputStream
}

fun interface ArtworkTransport {
    suspend fun execute(request: ArtworkTransportRequest): ArtworkTransportResponse
}
