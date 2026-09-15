package app.openstory.artwork.preflight

import android.graphics.BitmapFactory
import app.openstory.artwork.ArtworkFailureException
import app.openstory.artwork.ArtworkFailureReason
import app.openstory.artwork.artworkFailure
import coil3.size.Dimension
import coil3.size.Size
import java.io.File
import java.util.concurrent.CancellationException

fun interface ArtworkPreflight {
    fun inspect(file: File, declaredMediaType: String, targetSize: Size)
}

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
        val encodedBounds = inspectArtworkImageContainer(file, declaredMediaType)
        validateArtworkDimensionLimits(encodedBounds.width, encodedBounds.height)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) artworkFailure(ArtworkFailureReason.DECODE_FAILED)
        if (width.toLong() != encodedBounds.width || height.toLong() != encodedBounds.height) {
            artworkFailure(ArtworkFailureReason.DECODE_FAILED)
        }
        validateArtworkDimensionLimits(width.toLong(), height.toLong())
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
}

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
