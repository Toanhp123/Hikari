package app.openstory.catalog.evidence

import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.common.id.StoryId

data class CatalogSourceRecord(
    val key: SourceKey,
    val storyId: StoryId,
    val entry: CatalogEntry,
    val summary: CatalogMetadataStamp,
    val full: CatalogMetadataStamp?,
    val identityFingerprint: String,
    val fusionFingerprint: String,
)
