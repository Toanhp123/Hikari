package app.openstory.catalog.orchestration

import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.repository.CatalogCommitChange
import app.openstory.common.id.StoryId

enum class CatalogEvidenceLevel {
    SUMMARY,
    FULL,
}

data class CatalogEvidenceChange(
    val storyId: StoryId,
    val sourceKey: SourceKey,
    val identityFingerprintChanged: Boolean,
    val fusionFingerprintChanged: Boolean,
    val availabilityChanged: Boolean = false,
    val level: CatalogEvidenceLevel,
)

fun CatalogCommitChange.toEvidenceChange(level: CatalogEvidenceLevel): CatalogEvidenceChange = CatalogEvidenceChange(
    storyId = storyId,
    sourceKey = sourceKey,
    identityFingerprintChanged = identityFingerprintChanged,
    fusionFingerprintChanged = fusionFingerprintChanged,
    level = level,
)
