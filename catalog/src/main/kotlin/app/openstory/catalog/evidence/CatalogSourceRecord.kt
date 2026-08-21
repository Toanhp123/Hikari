package app.openstory.catalog.evidence

import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
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

fun CatalogMetadataSnapshot.toSourceRecord(): CatalogSourceRecord = CatalogSourceRecord(
    key = SourceKey(entry.pluginId, entry.sourceId),
    storyId = entry.storyId,
    entry = entry,
    summary = summary,
    full = full,
    identityFingerprint = CatalogEvidenceFingerprints.identity(entry),
    fusionFingerprint = CatalogEvidenceFingerprints.fusion(this),
)
