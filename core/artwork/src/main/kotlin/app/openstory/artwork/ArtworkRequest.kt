package app.openstory.artwork

import android.content.Context
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import java.io.File

data class ArtworkRequest(
    val identity: ArtworkRequestIdentity,
    val locator: ArtworkLocator?,
) {
    init {
        require(locator == null || locator.identity == identity.locator)
    }

    fun toImageRequest(context: Context): ImageRequest = ImageRequest.Builder(context)
        .data(this)
        .memoryCacheKey(identity.decodedCacheKey)
        .diskCacheKey(identity.encodedCacheKey)
        .diskCachePolicy(CachePolicy.DISABLED)
        .build()
}

sealed interface ArtworkLocator {
    val identity: String

    data class TrustedLocalResource(
        val logicalAssetId: String,
        val assetVersion: String,
    ) : ArtworkLocator {
        override val identity: String = "local:$logicalAssetId:$assetVersion"
    }

    data class RemoteHttps(
        val normalizedUri: String,
    ) : ArtworkLocator {
        override val identity: String = normalizedUri
    }
}

@JvmInline
value class ArtworkLocalAsset(val resourceId: Int) {
    init {
        require(resourceId != 0)
    }
}

fun interface ArtworkLocalResolver {
    fun resolve(logicalAssetId: String, assetVersion: String): ArtworkLocalAsset?
}

fun interface ArtworkPreflight {
    fun inspect(file: File, declaredMediaType: String, targetSize: Size)
}
