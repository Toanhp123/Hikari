package app.openstory.plugins.runtime.capabilities.http

import java.io.ByteArrayOutputStream
import java.io.IOException
import okhttp3.ResponseBody

internal object BoundedResponseReader {
    fun read(body: ResponseBody, maxBytes: Long): ByteArray {
        enforceBudget(body.contentLength(), maxBytes)
        try {
            body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    enforceBudget(total, maxBytes)
                    output.write(buffer, 0, read)
                }
                return output.toByteArray()
            }
        } catch (failure: IOException) {
            propagateReadFailure(failure)
        }
    }

    private fun enforceBudget(actualBytes: Long, maxBytes: Long) {
        if (actualBytes > maxBytes) throw ResponseBudgetExceeded()
    }

    private fun propagateReadFailure(failure: IOException): Nothing =
        throw if (failure is ResponseBudgetExceeded) {
            failure
        } else if (failure is CompressedResponseBudgetExceeded) {
            failure
        } else {
            HttpCapabilityFailure("plugin.http_read_failed", retryable = true, failure)
        }
}

internal class ResponseBudgetExceeded : IOException()
