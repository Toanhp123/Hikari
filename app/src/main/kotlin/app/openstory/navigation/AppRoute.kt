package app.openstory.navigation

import androidx.navigation3.runtime.NavKey
import app.openstory.common.navigation.RouteEntryId
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class ArtworkRoutePreviewWire(
    val authorityKey: String,
    val stableAssetKey: String,
    val locatorKind: String,
    val locatorValue: String,
    val locatorAux: String?,
    val revision: String,
)

@Serializable
data class StoryRouteWire(
    val entryId: String,
    val storyId: String,
    val catalogSourceKey: String,
    val sourceStoryId: String,
    val originMedia: AppMediaRoute,
    val previewTitle: String?,
    val previewArtwork: ArtworkRoutePreviewWire?,
)

@Serializable
sealed interface AppRoute : NavKey {
    val entryId: String

    @Serializable
    data class Discover(
        override val entryId: String,
        val media: AppMediaRoute,
    ) : AppRoute

    @Serializable
    data class Home(
        override val entryId: String,
    ) : AppRoute

    @Serializable
    data class Story(
        val wire: StoryRouteWire,
    ) : AppRoute {
        override val entryId: String get() = wire.entryId
    }
}

internal fun newStoryRouteEntryId(): RouteEntryId =
    RouteEntryId.from("story-${UUID.randomUUID()}")
