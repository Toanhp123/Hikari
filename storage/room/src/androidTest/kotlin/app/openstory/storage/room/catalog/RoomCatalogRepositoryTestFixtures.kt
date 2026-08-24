package app.openstory.storage.room.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.openstory.catalog.canonical.CanonicalFieldContributor
import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.canonical.CanonicalFieldProvenance
import app.openstory.catalog.canonical.CanonicalFieldStrategy
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Story
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase

internal fun mutation(
    plugin: String,
    sourceIds: List<String>,
    timestamp: Long,
    canonicalStoryId: StoryId? = null,
): CatalogHomeMutation {
    val pluginId = PluginId(plugin)
    val entries = sourceIds.map { sourceId ->
        CatalogEntry(
            canonicalStoryId ?: StoryId("story:$sourceId"),
            pluginId,
            sourceId,
            sourceId,
            contentType = ContentType.MANGA,
        )
    }
    return CatalogHomeMutation(
        pluginId = pluginId,
        pluginVersion = "1.0.0",
        refreshedAtEpochMillis = timestamp,
        stories = entries.map { Story(it.storyId, it.contentType) },
        entries = entries,
        sections = listOf(CatalogHomeSection("section", "Section", entries)),
        orderedSourceItemIds = mapOf("section" to sourceIds),
    )
}

internal fun projectionGeneration(storyId: StoryId): CanonicalGeneration {
    val source = SourceKey(PluginId("a"), "a-1")
    return CanonicalGeneration(
        id = "gen:projection-a",
        storyId = storyId,
        fusionPolicyVersion = 1,
        primarySelectionPolicyVersion = 1,
        fusionFingerprint = "fusion:projection-a",
        effectivePrimary = source,
        metadata = CanonicalMetadata(
            title = "Canonical A",
            description = null,
            coverUrl = "https://example.test/canonical-a.jpg",
            sourceUrl = null,
            popularityRank = null,
            aliases = emptyList(),
            authors = emptyList(),
            genres = emptyList(),
            languageTags = emptyList(),
            publicationStatus = PublicationStatus.ONGOING,
            latestUpdate = null,
            score = null,
        ),
        health = CanonicalHealth.FRESH,
        provenance = mapOf(
            CanonicalFieldKey.TITLE to CanonicalFieldProvenance(
                field = CanonicalFieldKey.TITLE,
                strategy = CanonicalFieldStrategy.PRIMARY_WITH_FALLBACK,
                contributors = listOf(
                    CanonicalFieldContributor(source, "source-fusion", CatalogMetadataLevel.Summary),
                ),
                reasonCodes = listOf("primary"),
                policyVersion = 1,
            ),
        ),
        createdAtEpochMillis = 10L,
    )
}

internal suspend fun withDatabase(block: suspend (OpenStoryDatabase) -> Unit) {
    val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        OpenStoryDatabase::class.java,
    ).build()
    try {
        block(database)
    } finally {
        database.close()
    }
}
