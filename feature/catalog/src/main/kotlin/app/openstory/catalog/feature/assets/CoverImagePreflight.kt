@file:Suppress("MagicNumber") // Binary image headers use format-defined byte offsets and masks.

package app.openstory.catalog.feature.assets

import android.graphics.BitmapFactory
import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailureException
import coil3.size.Dimension
import coil3.size.Size
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CancellationException

internal data class CoverImagePreflightResult(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val sampleSize: Int,
)

internal class CoverImagePreflight {
    fun inspect(
        file: File,
        declaredMediaType: String,
        targetSize: Size,
    ): CoverImagePreflightResult = try {
        val encodedBounds = verifyContainer(file, declaredMediaType)
        validateDimensionLimits(encodedBounds.width, encodedBounds.height)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) artworkFailure(CatalogArtworkFailureReason.DECODE_FAILED)
        if (width.toLong() != encodedBounds.width || height.toLong() != encodedBounds.height) {
            artworkFailure(CatalogArtworkFailureReason.DECODE_FAILED)
        }
        validateDimensionLimits(width.toLong(), height.toLong())
        CoverImagePreflightResult(
            sourceWidth = width,
            sourceHeight = height,
            sampleSize = calculateSampleSize(width, height, targetSize),
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: CatalogFailureException) {
        throw failure
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        throw app.openstory.catalog.domain.failure.CatalogFailureException(
            app.openstory.catalog.domain.failure.CatalogFailure.Artwork(
                CatalogArtworkFailureReason.DECODE_FAILED,
            ),
            error,
        )
    }

    private fun verifyContainer(file: File, declaredMediaType: String): EncodedImageBounds =
        RandomAccessFile(file, "r").use { input ->
            val detected = detectImageBounds(input)
                ?: artworkFailure(CatalogArtworkFailureReason.DECODE_FAILED)
            if (detected.mediaType != declaredMediaType) {
                artworkFailure(CatalogArtworkFailureReason.MEDIA_TYPE_REJECTED)
            }
            when (detected.mediaType) {
                "image/png" -> if (containsPngChunk(input, "acTL")) {
                    artworkFailure(CatalogArtworkFailureReason.DECODE_FAILED)
                }
                "image/webp" -> if (isAnimatedWebp(input)) {
                    artworkFailure(CatalogArtworkFailureReason.DECODE_FAILED)
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
    if (width <= 0 || height <= 0) artworkFailure(CatalogArtworkFailureReason.DECODE_FAILED)
    if (width > RemoteCoverLimits.MAX_SOURCE_DIMENSION ||
        height > RemoteCoverLimits.MAX_SOURCE_DIMENSION ||
        width * height > RemoteCoverLimits.MAX_SOURCE_PIXELS
    ) {
        artworkFailure(CatalogArtworkFailureReason.DIMENSIONS_TOO_LARGE)
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
        ?: artworkFailure(CatalogArtworkFailureReason.DECODE_FAILED)
    val targetHeight = (targetSize.height as? Dimension.Pixels)?.px
        ?: artworkFailure(CatalogArtworkFailureReason.DECODE_FAILED)
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
