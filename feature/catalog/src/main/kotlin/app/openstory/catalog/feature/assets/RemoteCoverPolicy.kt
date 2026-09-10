package app.openstory.catalog.feature.assets

import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.asset.SourceAssetPolicyProvider
import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.identity.CatalogSourceKey
import java.io.Closeable
import java.io.File
import java.net.URI
import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal class RemoteCoverPolicy(
    private val policyProvider: suspend () -> SourceAssetPolicyProvider?,
    private val transport: RemoteCoverTransport?,
    private val temporaryDirectory: File,
) {
    suspend fun fetch(locator: CoverLocator.RemoteHttps): RemoteCoverPayload =
        fetch(validate(locator))

    internal suspend fun fetch(
        catalogSourceKey: CatalogSourceKey,
        rawUri: String,
    ): RemoteCoverPayload = fetch(validate(catalogSourceKey, rawUri))

    internal suspend fun validate(locator: CoverLocator.RemoteHttps): ValidatedRemoteCover =
        validate(locator.catalogSourceKey, locator.normalizedUri.value)

    private suspend fun validate(
        catalogSourceKey: CatalogSourceKey,
        rawUri: String,
    ): ValidatedRemoteCover {
        val initialUri = try {
            RemoteHttpsUriV1.parseAndNormalize(rawUri)
        } catch (error: IllegalArgumentException) {
            throw CatalogFailureException(
                CatalogFailure.Artwork(CatalogArtworkFailureReason.INVALID_LOCATOR),
                error,
            )
        }
        val sourcePolicy = policyProvider()
            ?.policyFor(catalogSourceKey)
            ?.takeIf { it.catalogSourceKey == catalogSourceKey }
            ?: artworkFailure(CatalogArtworkFailureReason.POLICY_REJECTED)
        validateHost(initialUri, sourcePolicy, isRedirect = false)
        return ValidatedRemoteCover(initialUri, sourcePolicy)
    }

    internal suspend fun fetch(validated: ValidatedRemoteCover): RemoteCoverPayload {
        if (transport == null) artworkFailure(CatalogArtworkFailureReason.IO_FAILED)

        var current = validated.initialUri
        val visited = linkedSetOf(current)
        var redirectCount = 0
        while (true) {
            val result = executeHop(current)
            when (result) {
                is RemoteHopResult.Payload -> return result.payload
                is RemoteHopResult.Redirect -> {
                    if (redirectCount >= RemoteCoverLimits.MAX_REDIRECTS) {
                        artworkFailure(CatalogArtworkFailureReason.REDIRECT_REJECTED)
                    }
                    val next = try {
                        RemoteHttpsUriV1.resolveAndNormalize(current, result.location)
                    } catch (_: IllegalArgumentException) {
                        artworkFailure(CatalogArtworkFailureReason.REDIRECT_REJECTED)
                    }
                    validateHost(next, validated.sourcePolicy, isRedirect = true)
                    if (!visited.add(next)) artworkFailure(CatalogArtworkFailureReason.REDIRECT_REJECTED)
                    redirectCount++
                    current = next
                }
            }
        }
    }

    @Suppress("ThrowsCount")
    private suspend fun executeHop(uri: RemoteHttpsUriV1): RemoteHopResult {
        var pendingPayload: RemoteCoverPayload? = null
        val result = try {
            withTimeoutOrNull(RemoteCoverLimits.CALL_TIMEOUT_MILLIS) {
                try {
                    requireNotNull(transport).execute(
                        RemoteCoverTransportRequest(
                            uri = uri,
                            connectTimeoutMillis = RemoteCoverLimits.CONNECT_TIMEOUT_MILLIS,
                            readTimeoutMillis = RemoteCoverLimits.READ_TIMEOUT_MILLIS,
                            callTimeoutMillis = RemoteCoverLimits.CALL_TIMEOUT_MILLIS,
                        ),
                    ).use { response ->
                        response.toHopResult().also { hop ->
                            pendingPayload = (hop as? RemoteHopResult.Payload)?.payload
                        }
                    }
                } catch (timeout: TimeoutCancellationException) {
                    throw timeout
                } catch (cancellation: CancellationException) {
                    throw PreservedCallerCancellation(cancellation)
                }
            }
        } catch (preserved: PreservedCallerCancellation) {
            pendingPayload?.close()
            throw preserved.cancellation
        } catch (cancellation: CancellationException) {
            pendingPayload?.close()
            throw cancellation
        } catch (failure: CatalogFailureException) {
            pendingPayload?.close()
            throw failure
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            pendingPayload?.close()
            throw CatalogFailureException(CatalogFailure.Artwork(CatalogArtworkFailureReason.IO_FAILED), error)
        }
        if (result == null) {
            pendingPayload?.close()
            artworkFailure(CatalogArtworkFailureReason.TIMEOUT)
        }
        return result
    }

    @Suppress("ThrowsCount")
    private fun RemoteCoverTransportResponse.toHopResult(): RemoteHopResult {
        if (statusCode in ADMITTED_REDIRECT_CODES) {
            val location = redirectLocation
                ?.takeIf(String::isNotBlank)
                ?: artworkFailure(CatalogArtworkFailureReason.REDIRECT_REJECTED)
            return RemoteHopResult.Redirect(location)
        }
        if (statusCode in HTTP_REDIRECT_STATUS_RANGE) {
            artworkFailure(CatalogArtworkFailureReason.REDIRECT_REJECTED)
        }
        if (statusCode !in HTTP_SUCCESS_STATUS_RANGE) {
            artworkFailure(CatalogArtworkFailureReason.IO_FAILED)
        }

        val mediaType = acceptedMediaType(contentType)
        val declaredLength = contentLength?.takeIf { it >= 0 }
        if (declaredLength != null && declaredLength > CatalogImageLimits.MAX_ENCODED_BYTES) {
            artworkFailure(CatalogArtworkFailureReason.ENCODED_TOO_LARGE)
        }
        temporaryDirectory.mkdirs()
        val file = File.createTempFile("remote-cover-", ".encoded", temporaryDirectory)
        try {
            val copied = body.copyBoundedTo(file)
            if (declaredLength != null && copied != declaredLength) {
                artworkFailure(CatalogArtworkFailureReason.IO_FAILED)
            }
            return RemoteHopResult.Payload(RemoteCoverPayload(file, mediaType, copied))
        } catch (cancellation: CancellationException) {
            file.delete()
            throw cancellation
        } catch (failure: CatalogFailureException) {
            file.delete()
            throw failure
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            file.delete()
            throw CatalogFailureException(CatalogFailure.Artwork(CatalogArtworkFailureReason.IO_FAILED), error)
        }
    }

    private fun java.io.InputStream.copyBoundedTo(file: File): Long {
        var total = 0L
        file.outputStream().buffered().use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = read(buffer)
                if (read == -1) break
                total += read
                if (total > CatalogImageLimits.MAX_ENCODED_BYTES) {
                    artworkFailure(CatalogArtworkFailureReason.ENCODED_TOO_LARGE)
                }
                output.write(buffer, 0, read)
            }
        }
        return total
    }

}

internal class RemoteCoverPayload(
    val file: File,
    val mediaType: String,
    val length: Long,
) : Closeable {
    override fun close() {
        file.delete()
    }
}

internal data class ValidatedRemoteCover(
    val initialUri: RemoteHttpsUriV1,
    val sourcePolicy: SourceAssetPolicy,
)

internal object RemoteCoverLimits {
    const val MAX_REDIRECTS = 5
    const val CONNECT_TIMEOUT_MILLIS = 10_000L
    const val READ_TIMEOUT_MILLIS = 20_000L
    const val CALL_TIMEOUT_MILLIS = 20_000L
    const val MAX_SOURCE_DIMENSION = 8_192
    const val MAX_SOURCE_PIXELS = 32_000_000L
}

private sealed interface RemoteHopResult {
    data class Redirect(val location: String) : RemoteHopResult
    data class Payload(val payload: RemoteCoverPayload) : RemoteHopResult
}

private class PreservedCallerCancellation(
    val cancellation: CancellationException,
) : RuntimeException(cancellation)

private fun acceptedMediaType(raw: String?): String {
    val normalized = raw?.substringBefore(';')?.trim()?.lowercase()
    return normalized?.takeIf { it in ACCEPTED_MEDIA_TYPES }
        ?: artworkFailure(CatalogArtworkFailureReason.MEDIA_TYPE_REJECTED)
}

private fun validateHost(
    uri: RemoteHttpsUriV1,
    policy: SourceAssetPolicy,
    isRedirect: Boolean,
) {
    val host = URI(uri.value).host
    if (host == null || host !in policy.allowedHttpsHosts) {
        artworkFailure(
            if (isRedirect) {
                CatalogArtworkFailureReason.REDIRECT_REJECTED
            } else {
                CatalogArtworkFailureReason.POLICY_REJECTED
            },
        )
    }
}

internal fun artworkFailure(reason: CatalogArtworkFailureReason): Nothing =
    throw CatalogFailureException(CatalogFailure.Artwork(reason))

private val ADMITTED_REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
private val HTTP_REDIRECT_STATUS_RANGE = 300..399
private val HTTP_SUCCESS_STATUS_RANGE = 200..299
private val ACCEPTED_MEDIA_TYPES = setOf("image/jpeg", "image/png", "image/webp")
private const val COPY_BUFFER_BYTES = 8_192
