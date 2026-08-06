package app.openstory.model

import kotlinx.serialization.Serializable

@Serializable
enum class ContentType {
    LIGHT_NOVEL,
    WEB_NOVEL,
    MANGA,
    ANIME,
}
