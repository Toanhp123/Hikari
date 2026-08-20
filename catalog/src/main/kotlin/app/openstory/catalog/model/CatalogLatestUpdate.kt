package app.openstory.catalog.model

data class CatalogLatestUpdate(
    val atEpochMillis: Long,
    val releaseLabel: String?,
) {
    init {
        require(atEpochMillis >= 0L) { "Latest update time must not be negative" }
        require(releaseLabel == null || releaseLabel.isNotBlank()) {
            "Latest update release label must be null or non-blank"
        }
    }
}
