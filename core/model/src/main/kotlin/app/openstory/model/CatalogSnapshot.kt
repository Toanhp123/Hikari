package app.openstory.model

data class CatalogSnapshot(
    val pluginId: PluginId,
    val pluginVersion: String,
    val sections: List<CatalogSnapshotSection>,
) {
    init {
        require(pluginVersion.isNotBlank()) {
            "Catalog snapshot plugin version must not be blank"
        }
        require(sections.map(CatalogSnapshotSection::sourceId).distinct().size == sections.size) {
            "Catalog snapshot section IDs must be unique"
        }
    }
}

data class CatalogSnapshotSection(
    val sourceId: String,
    val title: String,
    val items: List<CatalogSnapshotItem>,
) {
    init {
        requireStableCatalogSourceId(sourceId, "Catalog section source ID")
        require(title.isNotBlank()) {
            "Catalog section title must not be blank"
        }
        require(items.map(CatalogSnapshotItem::sourceId).distinct().size == items.size) {
            "Catalog section item source IDs must be unique"
        }
    }
}

data class CatalogSnapshotItem(
    val sourceId: String,
    val title: String,
    val contentType: ContentType,
    val authors: List<String>,
    val coverReference: String?,
    val score: Double?,
    val scoreScale: Double?,
) {
    init {
        requireStableCatalogSourceId(sourceId, "Catalog item source ID")
        require(title.isNotBlank()) {
            "Catalog item title must not be blank"
        }
        require(authors.none(String::isBlank)) {
            "Catalog item authors must not contain blank values"
        }
        require(authors.distinct().size == authors.size) {
            "Catalog item authors must not contain duplicates"
        }
        require(coverReference == null || coverReference.isNotBlank()) {
            "Catalog item cover reference must be null or non-blank"
        }
        requireValidCatalogScore(score, scoreScale)
    }
}

internal fun requireStableCatalogSourceId(
    value: String,
    label: String,
) {
    require(value.isNotBlank()) {
        "$label must not be blank"
    }
    require(value.none(Char::isWhitespace)) {
        "$label must not contain whitespace"
    }
    require(value.none(Char::isISOControl)) {
        "$label must not contain control characters"
    }
}

internal fun requireValidCatalogScore(
    score: Double?,
    scoreScale: Double?,
) {
    require(
        (score == null && scoreScale == null) ||
            (
                score != null &&
                    scoreScale != null &&
                    scoreScale > 0.0 &&
                    score in 0.0..scoreScale
            )
    ) {
        "Catalog score and positive score scale must be provided together"
    }
}
