@file:Suppress("MagicNumber") // Binary image headers use format-defined byte offsets and masks.

package app.openstory.artwork

import android.graphics.BitmapFactory
import app.openstory.common.execution.BoundedProcessWorkAdmission
import app.openstory.common.execution.ProcessWorkAdmission
import app.openstory.common.execution.WorkAdmissionResult
import app.openstory.common.execution.WorkPriority
import app.openstory.common.execution.WorkResource
import coil3.size.Dimension
import coil3.size.Size
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.IDN
import java.net.URI
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull

class ArtworkPolicy(
    val authority: ArtworkAuthorityKey,
    allowedHttpsHosts: Set<String>,
) {
    val allowedHttpsHosts: Set<String> = allowedHttpsHosts.toSet()

    init {
        this.allowedHttpsHosts.forEach { host ->
            require(canonicalHost(host) == host)
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is ArtworkPolicy &&
            authority == other.authority &&
            allowedHttpsHosts == other.allowedHttpsHosts

    override fun hashCode(): Int = 31 * authority.hashCode() + allowedHttpsHosts.hashCode()

    override fun toString(): String =
        "ArtworkPolicy(authority=$authority, allowedHttpsHosts=$allowedHttpsHosts)"
}

enum class ArtworkFailureReason {
    INVALID_LOCATOR,
    POLICY_REJECTED,
    REDIRECT_REJECTED,
    MEDIA_TYPE_REJECTED,
    ENCODED_TOO_LARGE,
    DIMENSIONS_TOO_LARGE,
    DECODE_FAILED,
    TIMEOUT,
    SATURATED,
    IO_FAILED,
}

class ArtworkFailureException(
    val reason: ArtworkFailureReason,
    cause: Throwable? = null,
) : RuntimeException(reason.name, cause)

class ArtworkRemotePolicy(
    private val policyResolver: ArtworkPolicyResolver,
    private val transport: ArtworkTransport?,
    private val temporaryDirectory: File,
    private val admission: ProcessWorkAdmission = BoundedProcessWorkAdmission(),
) {
    suspend fun fetch(identity: ArtworkRequestIdentity): ArtworkRemotePayload {
        return fetch(validate(identity))
    }

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
            pendingPayload?.close()
            throw preserved.cancellation
        } catch (cancellation: CancellationException) {
            pendingPayload?.close()
            throw cancellation
        } catch (failure: ArtworkFailureException) {
            pendingPayload?.close()
            throw failure
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            pendingPayload?.close()
            throw ArtworkFailureException(ArtworkFailureReason.IO_FAILED, error)
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
            file.delete()
            throw cancellation
        } catch (failure: ArtworkFailureException) {
            file.delete()
            throw failure
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            file.delete()
            throw ArtworkFailureException(ArtworkFailureReason.IO_FAILED, error)
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

data class ArtworkImagePreflightResult(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val sampleSize: Int,
)

class ArtworkImagePreflight {
    fun inspect(
        file: File,
        declaredMediaType: String,
        targetSize: Size,
    ): ArtworkImagePreflightResult = try {
        val encodedBounds = verifyContainer(file, declaredMediaType)
        validateDimensionLimits(encodedBounds.width, encodedBounds.height)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) artworkFailure(ArtworkFailureReason.DECODE_FAILED)
        if (width.toLong() != encodedBounds.width || height.toLong() != encodedBounds.height) {
            artworkFailure(ArtworkFailureReason.DECODE_FAILED)
        }
        validateDimensionLimits(width.toLong(), height.toLong())
        ArtworkImagePreflightResult(
            sourceWidth = width,
            sourceHeight = height,
            sampleSize = calculateSampleSize(width, height, targetSize),
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: ArtworkFailureException) {
        throw failure
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        throw ArtworkFailureException(ArtworkFailureReason.DECODE_FAILED, error)
    }

    private fun verifyContainer(file: File, declaredMediaType: String): EncodedImageBounds =
        RandomAccessFile(file, "r").use { input ->
            val detected = detectImageBounds(input)
                ?: artworkFailure(ArtworkFailureReason.DECODE_FAILED)
            if (detected.mediaType != declaredMediaType) {
                artworkFailure(ArtworkFailureReason.MEDIA_TYPE_REJECTED)
            }
            when (detected.mediaType) {
                "image/png" -> if (containsPngChunk(input, "acTL")) {
                    artworkFailure(ArtworkFailureReason.DECODE_FAILED)
                }
                "image/webp" -> if (isAnimatedWebp(input)) {
                    artworkFailure(ArtworkFailureReason.DECODE_FAILED)
                }
            }
            detected
        }
}

private data class EncodedImageBounds(
    val mediaType: String,
    val width: Long,
    val height: Long,
)

private fun validateDimensionLimits(width: Long, height: Long) {
    if (width <= 0 || height <= 0) artworkFailure(ArtworkFailureReason.DECODE_FAILED)
    if (width > ArtworkLimits.MAX_SOURCE_DIMENSION ||
        height > ArtworkLimits.MAX_SOURCE_DIMENSION ||
        width * height > ArtworkLimits.MAX_SOURCE_PIXELS
    ) {
        artworkFailure(ArtworkFailureReason.DIMENSIONS_TOO_LARGE)
    }
}

private fun detectImageBounds(input: RandomAccessFile): EncodedImageBounds? {
    if (input.length() < CONTAINER_HEADER_BYTES) return null
    input.seek(0)
    val header = ByteArray(CONTAINER_HEADER_BYTES)
    input.readFully(header)
    return when {
        header[0] == JPEG_MARKER_PREFIX &&
            header[1] == JPEG_START_OF_IMAGE &&
            header[2] == JPEG_MARKER_PREFIX -> readJpegBounds(input)
        header.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE) -> readPngBounds(input)
        header.copyOfRange(0, RIFF.size).contentEquals(RIFF) &&
            header.copyOfRange(WEBP_OFFSET, WEBP_OFFSET + WEBP.size).contentEquals(WEBP) ->
            readWebpBounds(input)
        else -> null
    }
}

@Suppress("ReturnCount")
private fun readJpegBounds(input: RandomAccessFile): EncodedImageBounds? {
    var offset = JPEG_INITIAL_SEGMENT_OFFSET
    while (offset + JPEG_SEGMENT_PREFIX_BYTES <= input.length()) {
        input.seek(offset)
        if (input.readUnsignedByte() != JPEG_MARKER_PREFIX_UNSIGNED) return null
        val marker = input.readUnsignedByte()
        if (marker == JPEG_END_OF_IMAGE || marker == JPEG_START_OF_SCAN) return null
        val segmentLength = input.readUnsignedShort()
        if (segmentLength < JPEG_SEGMENT_LENGTH_BYTES ||
            offset + JPEG_MARKER_BYTES + segmentLength > input.length()
        ) {
            return null
        }
        if (marker in JPEG_START_OF_FRAME_MARKERS) {
            if (segmentLength < JPEG_START_OF_FRAME_MIN_LENGTH) return null
            input.readUnsignedByte()
            val height = input.readUnsignedShort().toLong()
            val width = input.readUnsignedShort().toLong()
            return EncodedImageBounds("image/jpeg", width, height)
        }
        offset += JPEG_MARKER_BYTES + segmentLength
    }
    return null
}

@Suppress("ReturnCount")
private fun readPngBounds(input: RandomAccessFile): EncodedImageBounds? {
    if (input.length() < PNG_DIMENSIONS_END_OFFSET) return null
    input.seek(PNG_FIRST_CHUNK_OFFSET)
    if ((input.readInt().toLong() and UINT_MASK) < PNG_IHDR_DATA_BYTES) return null
    val chunkType = ByteArray(PNG_CHUNK_TYPE_BYTES)
    input.readFully(chunkType)
    if (!chunkType.contentEquals(PNG_IHDR)) return null
    val width = input.readInt().toLong() and UINT_MASK
    val height = input.readInt().toLong() and UINT_MASK
    return EncodedImageBounds("image/png", width, height)
}

@Suppress("ReturnCount")
private fun readWebpBounds(input: RandomAccessFile): EncodedImageBounds? {
    var offset = WEBP_FIRST_CHUNK_OFFSET
    var canvasBounds: EncodedImageBounds? = null
    while (offset + WEBP_CHUNK_HEADER <= input.length()) {
        input.seek(offset)
        val typeBytes = ByteArray(WEBP_CHUNK_TYPE_BYTES)
        input.readFully(typeBytes)
        val length = readUnsignedIntLittleEndian(input)
        val payloadOffset = offset + WEBP_CHUNK_HEADER
        if (payloadOffset + length > input.length()) return null
        when (String(typeBytes, Charsets.US_ASCII)) {
            "VP8 " -> return validateWebpImageBounds(
                canvasBounds,
                readVp8Bounds(input, payloadOffset, length),
            )
            "VP8L" -> return validateWebpImageBounds(
                canvasBounds,
                readVp8lBounds(input, payloadOffset, length),
            )
            "VP8X" -> {
                if (canvasBounds != null) return null
                canvasBounds = readVp8xBounds(input, payloadOffset, length) ?: return null
                validateDimensionLimits(canvasBounds.width, canvasBounds.height)
            }
        }
        offset = payloadOffset + length + (length and 1L)
    }
    return null
}

private fun validateWebpImageBounds(
    canvasBounds: EncodedImageBounds?,
    imageBounds: EncodedImageBounds?,
): EncodedImageBounds? {
    if (imageBounds != null) {
        validateDimensionLimits(imageBounds.width, imageBounds.height)
    }

    return imageBounds?.takeIf { bounds ->
        canvasBounds == null ||
            (canvasBounds.width == bounds.width && canvasBounds.height == bounds.height)
    }
}

@Suppress("ReturnCount")
private fun readVp8Bounds(input: RandomAccessFile, offset: Long, length: Long): EncodedImageBounds? {
    if (length < VP8_HEADER_BYTES) return null
    input.seek(offset + VP8_START_CODE_OFFSET)
    val startCode = ByteArray(VP8_START_CODE.size)
    input.readFully(startCode)
    if (!startCode.contentEquals(VP8_START_CODE)) return null
    val width = readUnsignedShortLittleEndian(input).toLong() and VP8_DIMENSION_MASK
    val height = readUnsignedShortLittleEndian(input).toLong() and VP8_DIMENSION_MASK
    return EncodedImageBounds("image/webp", width, height)
}

@Suppress("ReturnCount")
private fun readVp8lBounds(input: RandomAccessFile, offset: Long, length: Long): EncodedImageBounds? {
    if (length < VP8L_HEADER_BYTES) return null
    input.seek(offset)
    if (input.readUnsignedByte() != VP8L_SIGNATURE) return null
    val dimensions = readUnsignedIntLittleEndian(input)
    val width = (dimensions and VP8L_DIMENSION_MASK) + 1
    val height = ((dimensions shr VP8L_HEIGHT_SHIFT) and VP8L_DIMENSION_MASK) + 1
    return EncodedImageBounds("image/webp", width, height)
}

private fun readVp8xBounds(input: RandomAccessFile, offset: Long, length: Long): EncodedImageBounds? {
    if (length < VP8X_HEADER_BYTES) return null
    input.seek(offset + VP8X_WIDTH_OFFSET)
    val width = readUnsigned24LittleEndian(input) + 1
    val height = readUnsigned24LittleEndian(input) + 1
    return EncodedImageBounds("image/webp", width, height)
}

private fun containsPngChunk(input: RandomAccessFile, expectedType: String): Boolean {
    var offset = PNG_SIGNATURE.size.toLong()
    var found = false
    var complete = false
    while (!complete && offset + PNG_CHUNK_OVERHEAD <= input.length()) {
        input.seek(offset)
        val length = input.readInt().toLong() and UINT_MASK
        val typeBytes = ByteArray(4)
        input.readFully(typeBytes)
        val next = offset + PNG_CHUNK_OVERHEAD + length
        if (next > input.length()) {
            complete = true
        } else {
            val type = String(typeBytes, Charsets.US_ASCII)
            found = type == expectedType
            complete = found || type == "IEND"
            offset = next
        }
    }
    return found
}

private fun isAnimatedWebp(input: RandomAccessFile): Boolean {
    var offset = 12L
    var animated = false
    var complete = false
    while (!complete && offset + WEBP_CHUNK_HEADER <= input.length()) {
        input.seek(offset)
        val typeBytes = ByteArray(4)
        input.readFully(typeBytes)
        val length = readUnsignedIntLittleEndian(input)
        val type = String(typeBytes, Charsets.US_ASCII)
        animated = type == "ANIM" || type == "ANMF"
        if (type == "VP8X" && length >= 1) {
            val flags = input.readUnsignedByte()
            animated = flags and WEBP_ANIMATION_FLAG != 0
        }
        val paddedLength = length + (length and 1L)
        val next = offset + WEBP_CHUNK_HEADER + paddedLength
        complete = animated || next <= offset || next > input.length()
        offset = next
    }
    return animated
}

private fun readUnsignedIntLittleEndian(input: RandomAccessFile): Long =
    input.readUnsignedByte().toLong() or
        (input.readUnsignedByte().toLong() shl 8) or
        (input.readUnsignedByte().toLong() shl 16) or
        (input.readUnsignedByte().toLong() shl 24)

private fun readUnsignedShortLittleEndian(input: RandomAccessFile): Int =
    input.readUnsignedByte() or (input.readUnsignedByte() shl 8)

private fun readUnsigned24LittleEndian(input: RandomAccessFile): Long =
    input.readUnsignedByte().toLong() or
        (input.readUnsignedByte().toLong() shl 8) or
        (input.readUnsignedByte().toLong() shl 16)

private fun calculateSampleSize(sourceWidth: Int, sourceHeight: Int, targetSize: Size): Int {
    val targetWidth = (targetSize.width as? Dimension.Pixels)?.px
        ?: artworkFailure(ArtworkFailureReason.DECODE_FAILED)
    val targetHeight = (targetSize.height as? Dimension.Pixels)?.px
        ?: artworkFailure(ArtworkFailureReason.DECODE_FAILED)
    var sampleSize = 1
    while (sourceWidth / (sampleSize * 2) >= targetWidth &&
        sourceHeight / (sampleSize * 2) >= targetHeight
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
)
private val PNG_IHDR = "IHDR".encodeToByteArray()
private val RIFF = "RIFF".encodeToByteArray()
private val WEBP = "WEBP".encodeToByteArray()
private val VP8_START_CODE = byteArrayOf(0x9d.toByte(), 0x01, 0x2a)
private val JPEG_START_OF_FRAME_MARKERS = setOf(
    0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf,
)
private const val CONTAINER_HEADER_BYTES = 12
private val JPEG_MARKER_PREFIX = 0xff.toByte()
private val JPEG_START_OF_IMAGE = 0xd8.toByte()
private const val JPEG_MARKER_PREFIX_UNSIGNED = 0xff
private const val JPEG_INITIAL_SEGMENT_OFFSET = 2L
private const val JPEG_SEGMENT_PREFIX_BYTES = 4L
private const val JPEG_SEGMENT_LENGTH_BYTES = 2
private const val JPEG_MARKER_BYTES = 2L
private const val JPEG_START_OF_FRAME_MIN_LENGTH = 7
private const val JPEG_END_OF_IMAGE = 0xd9
private const val JPEG_START_OF_SCAN = 0xda
private const val PNG_FIRST_CHUNK_OFFSET = 8L
private const val PNG_CHUNK_TYPE_BYTES = 4
private const val PNG_IHDR_DATA_BYTES = 13L
private const val PNG_DIMENSIONS_END_OFFSET = 24L
private const val WEBP_OFFSET = 8
private const val WEBP_FIRST_CHUNK_OFFSET = 12L
private const val WEBP_CHUNK_TYPE_BYTES = 4
private const val PNG_CHUNK_OVERHEAD = 12L
private const val WEBP_CHUNK_HEADER = 8L
private const val WEBP_ANIMATION_FLAG = 0x02
private const val VP8_HEADER_BYTES = 10L
private const val VP8_START_CODE_OFFSET = 3L
private const val VP8_DIMENSION_MASK = 0x3fffL
private const val VP8L_HEADER_BYTES = 5L
private const val VP8L_SIGNATURE = 0x2f
private const val VP8L_DIMENSION_MASK = 0x3fffL
private const val VP8L_HEIGHT_SHIFT = 14
private const val VP8X_HEADER_BYTES = 10L
private const val VP8X_WIDTH_OFFSET = 4L
private const val UINT_MASK = 0xffff_ffffL

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

private fun canonicalHost(host: String): String {
    require(host.isNotBlank() && ':' !in host && '[' !in host && ']' !in host)
    val canonical = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
        .lowercase(Locale.ROOT)
        .removeSuffix(".")
    require(canonical.isNotBlank() && canonical.length <= MAX_DNS_HOST_CHARS)
    require(!IPV4_PATTERN.matches(canonical))
    canonical.split('.').forEach { label ->
        require(label.isNotEmpty() && label.length <= MAX_DNS_LABEL_CHARS)
    }
    return canonical
}

private fun acceptedMediaType(raw: String?): String {
    val normalized = raw?.substringBefore(';')?.trim()?.lowercase()
    return normalized?.takeIf { it in ACCEPTED_MEDIA_TYPES }
        ?: artworkFailure(ArtworkFailureReason.MEDIA_TYPE_REJECTED)
}

private fun artworkFailure(reason: ArtworkFailureReason): Nothing = throw ArtworkFailureException(reason)

private val ADMITTED_REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
private val ACCEPTED_MEDIA_TYPES = setOf("image/jpeg", "image/png", "image/webp")
private val IPV4_PATTERN = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
private val HTTP_REDIRECT_STATUS_RANGE = 300..399
private val HTTP_SUCCESS_STATUS_RANGE = 200..299
private const val COPY_BUFFER_BYTES = 8_192
private const val HTTPS_PORT = 443
private const val MAX_DNS_HOST_CHARS = 253
private const val MAX_DNS_LABEL_CHARS = 63
