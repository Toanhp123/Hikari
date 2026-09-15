package app.openstory.artwork.remote

import app.openstory.artwork.ArtworkFailureException
import app.openstory.artwork.ArtworkFailureReason
import app.openstory.artwork.ArtworkLimits
import app.openstory.artwork.artworkFailure
import app.openstory.artwork.policy.ArtworkPolicy
import app.openstory.artwork.policy.ArtworkPolicyResolver
import app.openstory.artwork.policy.canonicalHost
import app.openstory.artwork.request.ArtworkRequestIdentity
import app.openstory.common.execution.BoundedProcessWorkAdmission
import app.openstory.common.execution.ProcessWorkAdmission
import app.openstory.common.execution.WorkAdmissionResult
import app.openstory.common.execution.WorkPriority
import app.openstory.common.execution.WorkResource
import java.io.Closeable
import java.io.File
import java.net.URI
import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull

class ArtworkRemotePolicy(
    private val policyResolver: ArtworkPolicyResolver,
    private val transport: ArtworkTransport?,
    private val temporaryDirectory: File,
    private val admission: ProcessWorkAdmission = BoundedProcessWorkAdmission(),
) {
    suspend fun fetch(identity: ArtworkRequestIdentity): ArtworkRemotePayload = fetch(validate(identity))

    internal fun validate(identity: ArtworkRequestIdentity): ValidatedArtworkRequest {
        val uri = try {
            normalizeHttps(identity.locator)
        } catch (error: IllegalArgumentException) {
            throw ArtworkFailureException(ArtworkFailureReason.INVALID_LOCATOR, error)
        }
        val policy = policyResolver.policyFor(identity.authority)
            ?.takeIf { it.authority == identity.authority }
            ?: artworkFailure(ArtworkFailureReason.POLICY_REJECTED)
        validateHost(uri, policy, isRedirect = false)
        return ValidatedArtworkRequest(uri, policy)
    }

    internal suspend fun fetch(validated: ValidatedArtworkRequest): ArtworkRemotePayload {
        val activeTransport = transport ?: artworkFailure(ArtworkFailureReason.IO_FAILED)
        var current = validated.initialUri
        val visited = linkedSetOf(current)
        var redirectCount = 0
        while (true) {
            when (val result = executeHop(activeTransport, current)) {
                is RemoteHopResult.Payload -> return result.payload
                is RemoteHopResult.Redirect -> {
                    if (redirectCount >= ArtworkLimits.MAX_REDIRECTS) {
                        artworkFailure(ArtworkFailureReason.REDIRECT_REJECTED)
                    }
                    val next = try {
                        resolveHttps(current, result.location)
                    } catch (_: IllegalArgumentException) {
                        artworkFailure(ArtworkFailureReason.REDIRECT_REJECTED)
                    }
                    validateHost(next, validated.policy, isRedirect = true)
                    if (!visited.add(next)) artworkFailure(ArtworkFailureReason.REDIRECT_REJECTED)
                    redirectCount += 1
                    current = next
                }
            }
        }
    }

    private suspend fun executeHop(
        activeTransport: ArtworkTransport,
        uri: String,
    ): RemoteHopResult {
        var pendingPayload: ArtworkRemotePayload? = null
        val result = try {
            withTimeoutOrNull(ArtworkLimits.CALL_TIMEOUT_MILLIS) {
                try {
                    val admitted = admission.run(WorkResource.NETWORK, WorkPriority.VISIBLE_ARTWORK) {
                        activeTransport.execute(
                            ArtworkTransportRequest(
                                uri = uri,
                                connectTimeoutMillis = ArtworkLimits.CONNECT_TIMEOUT_MILLIS,
                                readTimeoutMillis = ArtworkLimits.READ_TIMEOUT_MILLIS,
                                callTimeoutMillis = ArtworkLimits.CALL_TIMEOUT_MILLIS,
                            ),
                        )
                    }
                    val response = when (admitted) {
                        is WorkAdmissionResult.Completed -> admitted.value
                        is WorkAdmissionResult.Rejected -> artworkFailure(ArtworkFailureReason.SATURATED)
                    }
                    response.use {
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
            failHop(pendingPayload, preserved.cancellation)
        } catch (cancellation: CancellationException) {
            failHop(pendingPayload, cancellation)
        } catch (failure: ArtworkFailureException) {
            failHop(pendingPayload, failure)
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            failHop(
                pendingPayload,
                ArtworkFailureException(ArtworkFailureReason.IO_FAILED, error),
            )
        }
        if (result == null) {
            pendingPayload?.close()
            artworkFailure(ArtworkFailureReason.TIMEOUT)
        }
        return result
    }

    private fun ArtworkTransportResponse.toHopResult(): RemoteHopResult {
        if (statusCode in ADMITTED_REDIRECT_CODES) {
            val location = redirectLocation?.takeIf(String::isNotBlank)
                ?: artworkFailure(ArtworkFailureReason.REDIRECT_REJECTED)
            return RemoteHopResult.Redirect(location)
        }
        if (statusCode in HTTP_REDIRECT_STATUS_RANGE) {
            artworkFailure(ArtworkFailureReason.REDIRECT_REJECTED)
        }
        if (statusCode !in HTTP_SUCCESS_STATUS_RANGE) artworkFailure(ArtworkFailureReason.IO_FAILED)

        val mediaType = acceptedMediaType(contentType)
        val declaredLength = contentLength?.takeIf { it >= 0 }
        if (declaredLength != null && declaredLength > ArtworkLimits.MAX_ENCODED_BYTES) {
            artworkFailure(ArtworkFailureReason.ENCODED_TOO_LARGE)
        }
        temporaryDirectory.mkdirs()
        val file = File.createTempFile("artwork-", ".encoded", temporaryDirectory)
        try {
            val copied = body.copyBoundedTo(file)
            if (declaredLength != null && copied != declaredLength) {
                artworkFailure(ArtworkFailureReason.IO_FAILED)
            }
            return RemoteHopResult.Payload(ArtworkRemotePayload(file, mediaType, copied))
        } catch (cancellation: CancellationException) {
            failPayloadFile(file, cancellation)
        } catch (failure: ArtworkFailureException) {
            failPayloadFile(file, failure)
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            failPayloadFile(
                file,
                ArtworkFailureException(ArtworkFailureReason.IO_FAILED, error),
            )
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
                if (total > ArtworkLimits.MAX_ENCODED_BYTES) {
                    artworkFailure(ArtworkFailureReason.ENCODED_TOO_LARGE)
                }
                output.write(buffer, 0, read)
            }
        }
        return total
    }
}

class ArtworkRemotePayload(
    val file: File,
    val mediaType: String,
    val length: Long,
) : Closeable {
    override fun close() {
        file.delete()
    }
}

internal data class ValidatedArtworkRequest(
    val initialUri: String,
    val policy: ArtworkPolicy,
)

private sealed interface RemoteHopResult {
    data class Redirect(val location: String) : RemoteHopResult
    data class Payload(val payload: ArtworkRemotePayload) : RemoteHopResult
}

private class PreservedCallerCancellation(
    val cancellation: CancellationException,
) : RuntimeException(cancellation)

private fun failHop(payload: ArtworkRemotePayload?, failure: Throwable): Nothing {
    payload?.close()
    throw failure
}

private fun failPayloadFile(file: File, failure: Throwable): Nothing {
    file.delete()
    throw failure
}

private fun normalizeHttps(raw: String): String {
    require(raw.length <= ArtworkLimits.MAX_LOCATOR_CHARS)
    require(raw.none { it.isWhitespace() || it.isISOControl() })
    val uri = URI(raw)
    require(uri.scheme.equals("https", ignoreCase = true))
    require(uri.userInfo == null && uri.fragment == null)
    require(uri.port == -1 || uri.port == HTTPS_PORT)
    val host = canonicalHost(uri.host ?: error("Missing host"))
    val path = uri.rawPath?.ifEmpty { "/" } ?: "/"
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    return "https://$host$path$query"
}

private fun resolveHttps(current: String, target: String): String {
    require(target.isNotBlank())
    return normalizeHttps(URI(current).resolve(target).normalize().toASCIIString())
}

private fun validateHost(uri: String, policy: ArtworkPolicy, isRedirect: Boolean) {
    val host = canonicalHost(requireNotNull(URI(uri).host))
    if (host !in policy.allowedHttpsHosts) {
        artworkFailure(
            if (isRedirect) ArtworkFailureReason.REDIRECT_REJECTED else ArtworkFailureReason.POLICY_REJECTED,
        )
    }
}

private fun acceptedMediaType(raw: String?): String {
    val normalized = raw?.substringBefore(';')?.trim()?.lowercase()
    return normalized?.takeIf { it in ACCEPTED_MEDIA_TYPES }
        ?: artworkFailure(ArtworkFailureReason.MEDIA_TYPE_REJECTED)
}

private val ADMITTED_REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
private val ACCEPTED_MEDIA_TYPES = setOf("image/jpeg", "image/png", "image/webp")
private val HTTP_REDIRECT_STATUS_RANGE = 300..399
private val HTTP_SUCCESS_STATUS_RANGE = 200..299
private const val COPY_BUFFER_BYTES = 8_192
private const val HTTPS_PORT = 443
