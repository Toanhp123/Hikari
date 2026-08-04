package app.openstory.model

import app.openstory.common.StableId

@JvmInline
value class StoryId(
    val value: String,
) {
    init {
        StableId.requireValid(value)
    }
}

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
value class PluginId(
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
