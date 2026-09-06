package app.openstory.catalog.engine.evidence

import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataSnapshot

fun CatalogMetadataSnapshot.toSourceRecord(): CatalogSourceRecord = CatalogSourceRecord(
    key = SourceKey(entry.pluginId, entry.sourceId),
    storyId = entry.storyId,
    entry = entry,
    summary = summary,
    full = full,
    identityFingerprint = CatalogEvidenceFingerprints.identity(entry),
    fusionFingerprint = CatalogEvidenceFingerprints.fusion(this),
)
