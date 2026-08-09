package app.openstory.storage.room.catalog

import androidx.room.withTransaction
import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.model.CatalogEntry
import app.openstory.common.Outcome
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomCatalogRepository internal constructor(
    private val database: OpenStoryDatabase,
    private val dao: CatalogDao,
) : CatalogRepository {
    constructor(database: OpenStoryDatabase) : this(database, database.catalogDao())

    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = combine(
        dao.observeSnapshots(),
        dao.observeSections(),
        dao.observeItems(),
        dao.observeAllEntries(),
    ) { snapshots, sections, items, storedEntries ->
        val entries = storedEntries.associateBy { it.pluginId to it.sourceId }
        snapshots.map { snapshot ->
            snapshot.toModel(
                sections.filter { it.pluginId == snapshot.pluginId },
                items.filter { it.pluginId == snapshot.pluginId },
                entries.mapValues { it.value.toModel() },
            )
        }
    }

    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = combine(
        dao.observeStory(storyId.value),
        dao.observeEntries(storyId.value),
    ) { story, entries ->
        story?.toModel()?.let { value -> StoryCatalogSnapshot(value, entries.map(CatalogEntryEntity::toModel)) }
    }

    override suspend fun matchSnapshot(): CatalogMatchSnapshot = CatalogMatchSnapshot(
        dao.entries().sortedWith(compareBy<CatalogEntryEntity> { it.storyId }.thenBy { it.pluginId }.thenBy { it.sourceId })
            .map(CatalogEntryEntity::toCandidate),
    )

    override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<Unit, CatalogStoreFailure> =
        runCatching {
            database.withTransaction {
                val existing = mutation.entries.mapNotNull { entry ->
                    dao.findEntry(entry.pluginId.value, entry.sourceId)
                }.associateBy { it.pluginId to it.sourceId }
                dao.upsertStories(mutation.stories.map { it.toEntity() })
                dao.upsertEntries(
                    mutation.entries.map { entry ->
                        merge(existing[entry.pluginId.value to entry.sourceId], entry, mutation)
                    },
                )
                dao.deleteSections(mutation.pluginId.value)
                dao.upsertSnapshot(
                    app.openstory.storage.room.catalog.CatalogHomeSnapshotEntity(
                        mutation.pluginId.value,
                        mutation.pluginVersion,
                        mutation.refreshedAtEpochMillis,
                    ),
                )
                dao.upsertSections(mutation.sections.mapIndexed { index, section ->
                    CatalogHomeSectionEntity(mutation.pluginId.value, section.sourceId, section.title, index)
                })
                dao.upsertItems(mutation.sections.flatMap { section ->
                    section.items.mapIndexed { index, entry ->
                        CatalogHomeItemEntity(mutation.pluginId.value, section.sourceId, index, entry.sourceId)
                    }
                })
            }
            Unit
        }.fold(
            onSuccess = { Outcome.Success(Unit) },
            onFailure = { Outcome.Failure(CatalogStoreFailure("catalog.home.commit_failed", retryable = true)) },
        )

    override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<StoryId, CatalogStoreFailure> =
        runCatching {
            database.withTransaction {
                dao.upsertStories(listOf(app.openstory.storage.room.catalog.StoryEntity(mutation.storyId.value, mutation.entry.contentType.name)))
                val existing = dao.findEntry(mutation.entry.pluginId.value, mutation.entry.sourceId)
                dao.upsertEntries(listOf(merge(existing, mutation.entry, mutation)))
            }
            mutation.storyId
        }.fold(
            onSuccess = { Outcome.Success(it) },
            onFailure = { Outcome.Failure(CatalogStoreFailure("catalog.details.commit_failed", retryable = true)) },
        )

    private fun merge(existing: CatalogEntryEntity?, entry: CatalogEntry, mutation: CatalogHomeMutation) =
        entry.toEntity(mutation.pluginVersion, mutation.refreshedAtEpochMillis).let { incoming ->
            existing?.copy(
                storyId = incoming.storyId,
                title = incoming.title,
                aliases = incoming.aliases.ifEmpty { existing.aliases },
                authors = incoming.authors.ifEmpty { existing.authors },
                description = incoming.description ?: existing.description,
                genres = incoming.genres.ifEmpty { existing.genres },
                contentType = incoming.contentType,
                languageTags = incoming.languageTags.ifEmpty { existing.languageTags },
                coverUrl = incoming.coverUrl ?: existing.coverUrl,
                sourceUrl = incoming.sourceUrl ?: existing.sourceUrl,
                scoreValue = incoming.scoreValue ?: existing.scoreValue,
                scoreScale = incoming.scoreScale ?: existing.scoreScale,
                popularityRank = incoming.popularityRank ?: existing.popularityRank,
                pluginVersion = incoming.pluginVersion,
                fetchedAtEpochMillis = incoming.fetchedAtEpochMillis,
            ) ?: incoming
        }

    private fun merge(existing: CatalogEntryEntity?, entry: CatalogEntry, mutation: CatalogDetailsMutation) =
        entry.toEntity(mutation.pluginVersion, mutation.fetchedAtEpochMillis).let { incoming ->
            existing?.copy(
                storyId = incoming.storyId,
                title = incoming.title,
                aliases = incoming.aliases.ifEmpty { existing.aliases },
                authors = incoming.authors.ifEmpty { existing.authors },
                description = incoming.description ?: existing.description,
                genres = incoming.genres.ifEmpty { existing.genres },
                contentType = incoming.contentType,
                languageTags = incoming.languageTags.ifEmpty { existing.languageTags },
                coverUrl = incoming.coverUrl ?: existing.coverUrl,
                sourceUrl = incoming.sourceUrl ?: existing.sourceUrl,
                scoreValue = incoming.scoreValue ?: existing.scoreValue,
                scoreScale = incoming.scoreScale ?: existing.scoreScale,
                popularityRank = incoming.popularityRank ?: existing.popularityRank,
                pluginVersion = incoming.pluginVersion,
                fetchedAtEpochMillis = incoming.fetchedAtEpochMillis,
            ) ?: incoming
        }
}
