package app.openstory.database.repository

import app.openstory.model.CanonicalStory
import app.openstory.model.CatalogCanonicalResolution
import app.openstory.model.CatalogCanonicalResolver
import app.openstory.model.CatalogSnapshotItem
import app.openstory.model.PluginId
import app.openstory.model.StoryId

class SourceIsolatedCatalogResolver : CatalogCanonicalResolver {
    override fun resolve(
        pluginId: PluginId,
        source: CatalogSnapshotItem,
        candidates: List<CanonicalStory>,
    ): CatalogCanonicalResolution {
        val storyId =
            StoryId(
                "catalog:${pluginId.value}:${source.sourceId}",
            )

        return if (
            candidates.any { candidate ->
                candidate.id == storyId
            }
        ) {
            CatalogCanonicalResolution.Existing(storyId)
        } else {
            CatalogCanonicalResolution.Create(storyId)
        }
    }
}
