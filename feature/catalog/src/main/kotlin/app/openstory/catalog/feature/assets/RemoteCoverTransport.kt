package app.openstory.catalog.feature.assets

import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import java.io.Closeable
import java.io.InputStream

internal data class RemoteCoverTransportRequest(
    val uri: RemoteHttpsUriV1,
    val connectTimeoutMillis: Long,
    val readTimeoutMillis: Long,
    val callTimeoutMillis: Long,
)

internal interface RemoteCoverTransportResponse : Closeable {
    val statusCode: Int
    val redirectLocation: String?
    val contentType: String?
    val contentLength: Long?
    val body: InputStream
}

internal fun interface RemoteCoverTransport {
    suspend fun execute(request: RemoteCoverTransportRequest): RemoteCoverTransportResponse
}
