package app.openstory.catalog.repository

import app.openstory.catalog.identity.SourceKey
import app.openstory.common.id.StoryId

data class CatalogCommitChange(
    val storyId: StoryId,
    val sourceKey: SourceKey,
    val identityFingerprintChanged: Boolean,
    val fusionFingerprintChanged: Boolean,
)

data class CatalogHomeCommitResult(
    val changes: List<CatalogCommitChange>,
)

data class CatalogDetailsCommitResult(
    val storyId: StoryId,
    val changes: List<CatalogCommitChange>,
)
