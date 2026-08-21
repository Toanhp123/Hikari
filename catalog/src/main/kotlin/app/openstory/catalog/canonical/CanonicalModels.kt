package app.openstory.catalog.canonical

import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Story
import app.openstory.common.id.StoryId

enum class CanonicalHealth { FRESH, STALE, REEVALUATING, DEGRADED }

enum class CanonicalSourcePreferenceMode { AUTO, PINNED }

data class CanonicalSourcePreference(
    val storyId: StoryId,
    val mode: CanonicalSourcePreferenceMode,
    val pinnedSource: SourceKey?,
    val revision: Long,
) {
    init {
        require(revision >= 0L)
        when (mode) {
            CanonicalSourcePreferenceMode.AUTO -> require(pinnedSource == null)
            CanonicalSourcePreferenceMode.PINNED -> requireNotNull(pinnedSource)
        }
    }
}

data class CanonicalScore(
    val normalizedValue: Double,
    val contributorCount: Int,
) {
    init {
        require(normalizedValue.isFinite() && normalizedValue in 0.0..1.0)
        require(contributorCount > 0)
    }
}

enum class CanonicalFieldKey {
    TITLE,
    DESCRIPTION,
    COVER_URL,
    SOURCE_URL,
    POPULARITY_RANK,
    ALIASES,
    AUTHORS,
    GENRES,
    LANGUAGE_TAGS,
    PUBLICATION_STATUS,
    LATEST_UPDATE,
    SCORE,
}

enum class CanonicalFieldStrategy {
    PRIMARY_WITH_FALLBACK,
    NORMALIZED_UNION,
    FRESHEST_QUALIFIED_VALUE,
    FRESHEST_COHERENT_OBJECT,
    NORMALIZED_MEAN,
}

data class CanonicalFieldContributor(
    val sourceKey: SourceKey,
    val fusionFingerprint: String,
    val metadataLevel: CatalogMetadataLevel,
) {
    init {
        require(fusionFingerprint.isNotBlank())
    }
}

data class CanonicalFieldProvenance(
    val field: CanonicalFieldKey,
    val strategy: CanonicalFieldStrategy,
    val contributors: List<CanonicalFieldContributor>,
    val reasonCodes: List<String>,
    val policyVersion: Int,
) {
    init {
        require(contributors.isNotEmpty())
        require(reasonCodes.all(String::isNotBlank))
        require(policyVersion > 0)
    }
}

data class CanonicalMetadata(
    val title: String,
    val description: String?,
    val coverUrl: String?,
    val sourceUrl: String?,
    val popularityRank: Long?,
    val aliases: List<String>,
    val authors: List<String>,
    val genres: List<String>,
    val languageTags: List<String>,
    val publicationStatus: PublicationStatus?,
    val latestUpdate: CatalogLatestUpdate?,
    val score: CanonicalScore?,
) {
    init {
        require(title.isNotBlank())
    }
}

data class CanonicalGeneration(
    val id: String,
    val storyId: StoryId,
    val fusionPolicyVersion: Int,
    val primarySelectionPolicyVersion: Int,
    val fusionFingerprint: String,
    val effectivePrimary: SourceKey,
    val metadata: CanonicalMetadata,
    val health: CanonicalHealth,
    val provenance: Map<CanonicalFieldKey, CanonicalFieldProvenance>,
    val createdAtEpochMillis: Long,
) {
    init {
        require(id.isNotBlank())
        require(fusionPolicyVersion > 0)
        require(primarySelectionPolicyVersion > 0)
        require(fusionFingerprint.isNotBlank())
        require(createdAtEpochMillis >= 0L)
        require(provenance.all { (field, value) -> field == value.field })
    }
}

data class CanonicalSourceSummary(
    val sourceKey: SourceKey,
    val entry: CatalogEntry,
    val summary: CatalogMetadataStamp,
    val full: CatalogMetadataStamp?,
    val identityFingerprint: String,
    val fusionFingerprint: String,
) {
    init {
        require(identityFingerprint.isNotBlank())
        require(fusionFingerprint.isNotBlank())
    }
}

sealed interface CanonicalStoryState {
    val story: Story
    val health: CanonicalHealth
    val preference: CanonicalSourcePreference
    val sources: List<CanonicalSourceSummary>

    data class Preparing(
        override val story: Story,
        override val health: CanonicalHealth,
        override val preference: CanonicalSourcePreference,
        override val sources: List<CanonicalSourceSummary>,
    ) : CanonicalStoryState {
        init {
            require(preference.storyId == story.id)
        }
    }

    data class Ready(
        override val story: Story,
        override val health: CanonicalHealth,
        override val preference: CanonicalSourcePreference,
        override val sources: List<CanonicalSourceSummary>,
        val generation: CanonicalGeneration,
    ) : CanonicalStoryState {
        init {
            require(preference.storyId == story.id)
            require(generation.storyId == story.id)
        }
    }
}
