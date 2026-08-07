package app.openstory.network

import app.openstory.common.AppError
import app.openstory.common.AppResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

internal class BoundedResponseReader {
    fun read(
        response: Response,
        budget: RequestBudget,
    ): AppResult<ByteArray> =
        try {
            response.body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0L

                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break

                    totalBytes += read
                    if (totalBytes > budget.maxDecompressedBytes) {
                        return boundedReaderFailure(
                            "network.response_decompressed_too_large",
                            retryable = false,
                        )
                    }
                    output.write(buffer, 0, read)
                }

                AppResult.Success(output.toByteArray())
            }
        } catch (_: CompressedBodyTooLargeException) {
            compressedBodyTooLarge()
        } catch (_: IOException) {
            boundedReaderFailure("network.request_failed", retryable = true)
        }
}

internal data class CompressedByteLimit(
    val maxBytes: Long,
)

internal class CompressedLimitInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val limit = chain.request().tag(CompressedByteLimit::class.java)?.maxBytes
            ?: return response
        val body = response.body

        if (body.contentLength() > limit) {
            response.close()
            throw CompressedBodyTooLargeException()
        }

        return response.newBuilder()
            .body(CompressedLimitResponseBody(body, limit))
            .build()
    }
}

private class CompressedLimitResponseBody(
    private val delegate: ResponseBody,
    private val maxBytes: Long,
) : ResponseBody() {
    private val limitedSource: BufferedSource by lazy {
        object : ForwardingSource(delegate.source()) {
            private var totalBytes = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read > 0) {
                    totalBytes += read
                    if (totalBytes > maxBytes) throw CompressedBodyTooLargeException()
                }
                return read
            }
        }.buffer()
    }

    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()
    override fun source(): BufferedSource = limitedSource
}

internal class CompressedBodyTooLargeException :
    IOException("Compressed response exceeds its byte budget.")

internal fun compressedBodyTooLarge(): AppResult.Failure =
    boundedReaderFailure(
        "network.response_compressed_too_large",
        retryable = false,
    )

private fun boundedReaderFailure(
    code: String,
    retryable: Boolean,
): AppResult.Failure =
    AppResult.Failure(
        AppError.Network(
            code = code,
            retryable = retryable,
        ),
    )
