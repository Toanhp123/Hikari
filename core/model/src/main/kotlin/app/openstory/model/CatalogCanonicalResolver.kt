package app.openstory.model

sealed interface CatalogCanonicalResolution {
    val storyId: StoryId

    data class Existing(
        override val storyId: StoryId,
    ) : CatalogCanonicalResolution

    data class Create(
        override val storyId: StoryId,
    ) : CatalogCanonicalResolution
}

fun interface CatalogCanonicalResolver {
    fun resolve(
        pluginId: PluginId,
        source: CatalogSnapshotItem,
        candidates: List<CanonicalStory>,
    ): CatalogCanonicalResolution
}
