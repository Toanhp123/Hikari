package app.openstory.navigation

import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Library : AppRoute

    @Serializable
    data object Plugins : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data class Story(
        val storyId: String,
    ) : AppRoute

    @Serializable
    data class Reader(
        val chapterId: String,
        val releaseId: String?,
    ) : AppRoute
}
