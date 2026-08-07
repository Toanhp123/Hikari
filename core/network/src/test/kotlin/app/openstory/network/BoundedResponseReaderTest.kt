package app.openstory.network

import app.openstory.common.AppResult
import java.io.IOException
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BoundedResponseReaderTest {
    @Test
    fun decompressedCeilingIsEnforced() {
        val response = responseWith("abcd".encodeToByteArray().toResponseBody())

        val result = BoundedResponseReader().read(
            response,
            RequestBudget(maxDecompressedBytes = 3),
        )

        assertEquals("network.response_decompressed_too_large", result.errorCode())
    }

    @Test
    fun compressedCeilingFailureKeepsExistingErrorCode() {
        val response = responseWith(ThrowingResponseBody(CompressedBodyTooLargeException()))

        val result = BoundedResponseReader().read(response, RequestBudget())

        assertEquals("network.response_compressed_too_large", result.errorCode())
    }

    @Test
    fun bytesWithinBudgetAreReturned() {
        val result = BoundedResponseReader().read(
            responseWith("abc".encodeToByteArray().toResponseBody()),
            RequestBudget(maxDecompressedBytes = 3),
        )

        assertEquals("abc", assertIs<AppResult.Success<ByteArray>>(result).value.decodeToString())
    }
}

private fun responseWith(body: ResponseBody): Response =
    Response.Builder()
        .request(Request.Builder().url("https://example.com").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body)
        .build()

private fun ByteArray.toResponseBody(): ResponseBody =
    object : ResponseBody() {
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = size.toLong()
        override fun source(): BufferedSource = Buffer().write(this@toResponseBody)
    }

private class ThrowingResponseBody(
    private val failure: IOException,
) : ResponseBody() {
    override fun contentType(): MediaType? = null
    override fun contentLength(): Long = -1L
    override fun source(): BufferedSource =
        object : ForwardingSource(Buffer()) {
            override fun read(sink: Buffer, byteCount: Long): Long = throw failure
        }.buffer()
}

private fun AppResult<*>.errorCode(): String? =
    when (this) {
        is AppResult.Failure -> error.code
        is AppResult.Success -> null
    }
