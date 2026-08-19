package app.openstory.catalog.ui.components

import app.openstory.common.id.PluginId

internal fun PluginId.catalogDisplayName(): String {
    val segments = value.split('.')
    val normalized = segments.map { segment -> segment.lowercase() }
    val key = normalized.last()
    return when {
        "mangadex" in normalized -> "MangaDex"
        "myanimelist" in normalized || "mal" in normalized -> "MyAnimeList"
        segments.size == 2 && segments.first().equals("catalog", ignoreCase = true) -> "Catalog ${key.uppercase()}"
        else -> key
            .split('-', '_')
            .filter(String::isNotBlank)
            .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }
    }
}
