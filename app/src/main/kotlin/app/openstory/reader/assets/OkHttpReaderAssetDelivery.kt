package app.openstory.reader.assets

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OkHttpReaderAssetDelivery(
    client: OkHttpClient,
) : ReaderAssetDeliveryPort {
    private val client = client.newBuilder()
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .addNetworkInterceptor { chain ->
            val credentialFreeRequest = chain.request().newBuilder()
                .removeHeader("Authorization")
                .removeHeader("Proxy-Authorization")
                .removeHeader("Cookie")
                .build()

            // OkHttp may inspect Retry-After before this adapter can classify a 503.
            // The header is not part of ReaderAssetDeliveryResult, so remove it for every 503 and
            // keep ReaderAssetLoader as the sole retry owner.
            val response = chain.proceed(credentialFreeRequest)
            if (response.code == HTTP_SERVICE_UNAVAILABLE && response.header("Retry-After") != null) {
                response.newBuilder()
                    .removeHeader("Retry-After")
                    .build()
            } else {
                response
            }
        }
        .build()

    override suspend fun fetch(request: ReaderAssetDeliveryRequest): ReaderAssetDeliveryResult {
        val httpUrl = requireNotNull(request.deliveryLocator.toHttpUrlOrNull()) {
            "Reader delivery locator must be a valid HTTP URL"
        }
        require(httpUrl.isHttps) { "Reader delivery locator must use HTTPS" }

        val call = client.newCall(
            Request.Builder()
                .url(httpUrl)
                .get()
                .build(),
        )
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resume(retryableTransportFailure())
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!continuation.isActive) {
                            response.close()
                            return
                        }
                        try {
                            val result = response.use(::mapResponse)
                            if (continuation.isActive) continuation.resume(result)
                        } catch (_: IOException) {
                            if (continuation.isActive) continuation.resume(retryableTransportFailure())
                        } catch (@Suppress("TooGenericExceptionCaught") failure: RuntimeException) {
                            if (continuation.isActive) continuation.resumeWithException(failure)
                        }
                    }
                },
            )
        }
    }

    private fun mapResponse(response: Response): ReaderAssetDeliveryResult = when {
        response.code.isRetryableStatus() -> retryableTransportFailure()
        !response.isSuccessful -> deliveryRejected(response.code)
        else -> mapSuccessfulResponse(response)
    }

    private fun mapSuccessfulResponse(response: Response): ReaderAssetDeliveryResult {
        val body = response.body
        val contentLength = body.contentLength()
        val mimeType = body.contentType()?.toString()?.trim()?.lowercase(Locale.ROOT)
        return when {
            contentLength > ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES -> assetTooLarge()
            mimeType.isObviouslyNonImagePayload() -> invalidPayload()
            else -> mapBoundedBody(body.byteStream(), mimeType)
        }
    }

    private fun mapBoundedBody(
        input: java.io.InputStream,
        mimeType: String?,
    ): ReaderAssetDeliveryResult {
        val bytes = readBounded(input) ?: return assetTooLarge()
        return when {
            bytes.isEmpty() || bytes.looksLikeTextErrorPayload() -> invalidPayload()
            else -> verifiedPayload(bytes, mimeType)
        }
    }

    private fun verifiedPayload(
        bytes: ByteArray,
        mimeType: String?,
    ): ReaderAssetDeliveryResult = try {
        ReaderAssetDeliveryResult.Success(
            ReaderAssetPayload.verifiedBounded(
                bytes = bytes,
                mimeType = mimeType,
                sourceIntegrityEvidence = null,
            ),
        )
    } catch (_: IllegalArgumentException) {
        invalidPayload()
    }

    private fun readBounded(input: java.io.InputStream): ByteArray? {
        val output = ByteArrayOutputStream(BUFFER_SIZE)
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytes = 0
        var read = input.read(buffer)
        while (read != -1) {
            if (read > 0) {
                if (totalBytes > ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES - read) return null
                output.write(buffer, 0, read)
                totalBytes += read
            }
            read = input.read(buffer)
        }
        return output.toByteArray()
    }

    private fun String?.isObviouslyNonImagePayload(): Boolean {
        if (this == null) return false
        val mediaType = substringBefore(';').trim()
        return mediaType == "text/html" ||
            mediaType == "application/json" ||
            mediaType.endsWith("+json")
    }

    private fun ByteArray.looksLikeTextErrorPayload(): Boolean {
        val sniffLength = minOf(size, PAYLOAD_SNIFF_BYTES)
        val prefix = copyOfRange(0, sniffLength)
            .toString(Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            .lowercase(Locale.ROOT)
        return prefix.startsWith("<!doctype html") ||
            prefix.startsWith("<html") ||
            prefix.startsWith("<body") ||
            prefix.startsWith("{") ||
            prefix.startsWith("[")
    }

    private fun retryableTransportFailure(): ReaderAssetDeliveryResult.Failure =
        ReaderAssetDeliveryResult.Failure(
            ReaderAssetFailure.TransportUnavailable(retryable = true),
        )

    private fun deliveryRejected(status: Int): ReaderAssetDeliveryResult.Failure =
        ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.DeliveryRejected(status))

    private fun assetTooLarge(): ReaderAssetDeliveryResult.Failure =
        ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.AssetTooLarge)

    private fun invalidPayload(): ReaderAssetDeliveryResult.Failure =
        ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.InvalidPayload)

    private fun Int.isRetryableStatus(): Boolean =
        this == HTTP_REQUEST_TIMEOUT || this == HTTP_TOO_MANY_REQUESTS || this in HTTP_SERVER_ERROR_RANGE

    private companion object {
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVICE_UNAVAILABLE = 503
        val HTTP_SERVER_ERROR_RANGE = 500..599
        const val BUFFER_SIZE = 8 * 1024
        const val PAYLOAD_SNIFF_BYTES = 256
    }
}
