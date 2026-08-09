package app.openstory.model

import app.openstory.common.StableId

typealias StoryId = app.openstory.common.id.StoryId
typealias PluginId = app.openstory.common.id.PluginId

@JvmInline
value class ChapterId(
    val value: String,
) {
    init {
        StableId.requireValid(value)
    }
}

@JvmInline
value class ReleaseId(
    val value: String,
) {
    init {
        StableId.requireValid(value)
    }
}

@JvmInline
value class CatalogEntryId(
    val value: String,
) {
    init {
        StableId.requireValid(value)
    }
}

@JvmInline
value class ContentMappingId(
    val value: String,
) {
    init {
        StableId.requireValid(value)
    }
}

@JvmInline
value class DownloadId(
    val value: String,
) {
    init {
        StableId.requireValid(value)
    }
}
