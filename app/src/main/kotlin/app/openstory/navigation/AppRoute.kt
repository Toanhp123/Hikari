package app.openstory.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Search : AppRoute

    @Serializable
    data object Library : AppRoute

    @Serializable
    data object Plugins : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data class Story(val storyId: String) : AppRoute

    @Serializable
    data class Reader(
        val storyId: String,
        val chapterId: String,
        val releaseId: String?,
    ) : AppRoute
}
