package app.openstory.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

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
}

@JvmInline
value class RouteEntryId private constructor(val value: String) {
    companion object {
        private const val MAX_LENGTH = 128
        private val SAFE_VALUE = Regex("[A-Za-z0-9_-]+")

        fun from(wireValue: String): RouteEntryId {
            require(wireValue.isNotBlank()) { "Route entry id must not be blank" }
            require(wireValue.length <= MAX_LENGTH) { "Route entry id is too long" }
            require(SAFE_VALUE.matches(wireValue)) { "Route entry id contains unsafe characters" }
            return RouteEntryId(wireValue)
        }
    }
}
