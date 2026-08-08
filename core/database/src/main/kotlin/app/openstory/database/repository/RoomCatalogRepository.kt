package app.openstory.database.repository

import androidx.room.withTransaction
import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.common.Clock
import app.openstory.common.SystemClock
import app.openstory.database.OpenStoryDatabase
import app.openstory.database.dao.CatalogEntryWithStoryRow
import app.openstory.database.entity.StoryCatalogEntryEntity
import app.openstory.database.mapping.toCatalogEntry
import app.openstory.database.mapping.toDomain
import app.openstory.database.mapping.toEntity
import app.openstory.database.mapping.toHomeSnapshots
import app.openstory.model.CanonicalStory
import app.openstory.model.CatalogCanonicalResolution
import app.openstory.model.CatalogCanonicalResolver
import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogEntryWithStory
import app.openstory.model.CatalogHomeSnapshot
import app.openstory.model.CatalogSnapshot
import app.openstory.model.CatalogSnapshotItem
import app.openstory.model.CatalogSourceMetadata
import app.openstory.model.PluginId
import app.openstory.model.StoryId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomCatalogRepository(
    private val database: OpenStoryDatabase,
    private val resolver: CatalogCanonicalResolver =
        SourceIsolatedCatalogResolver(),
    private val clock: Clock = SystemClock,
) : CatalogRepository {

    private val catalogDao =
        database.catalogDao()

    private val storyDao =
        database.storyDao()

    private val homeWriter =
        CatalogHomeWriter(catalogDao)

    override suspend fun ingest(
        snapshot: CatalogSnapshot,
    ): AppResult<Unit> =
        executeStorageWrite {
            database.withTransaction {
                val now = clock.nowEpochMillis()
                val uniqueItems =
                    snapshot.sections
                        .asSequence()
                        .flatMap { section ->
                            section.items.asSequence()
                        }
                        .distinctBy(CatalogSnapshotItem::sourceId)
                        .toList()
                val existing =
                    uniqueItems.associateWith { item ->
                        catalogDao.catalogEntryWithStory(
                            pluginId = snapshot.pluginId.value,
                            sourceId = item.sourceId,
                        )
                    }
                val candidates: MutableList<CanonicalStory> =
                    if (existing.values.any { row -> row == null }) {
                        storyDao
                            .canonicalStoryCandidates()
                            .map { aggregate -> aggregate.toDomain() }
                            .toMutableList()
                    } else {
                        mutableListOf()
                    }
                val savedEntries =
                    linkedMapOf<String, CatalogEntryWithStory>()

                uniqueItems.forEach { item ->
                    val saved =
                        upsertCard(
                            pluginId = snapshot.pluginId,
                            pluginVersion = snapshot.pluginVersion,
                            item = item,
                            fetchedAtEpochMillis = now,
                            existing = existing.getValue(item),
                            candidates = candidates,
                        )
                    savedEntries[item.sourceId] = saved
                }

                homeWriter.replace(
                    snapshot = snapshot,
                    refreshedAtEpochMillis = now,
                    savedEntries = savedEntries,
                )
            }
        }

    override suspend fun upsertSourceMetadata(
        pluginId: PluginId,
        pluginVersion: String,
        metadata: CatalogSourceMetadata,
    ): AppResult<CatalogEntryWithStory> {
        if (pluginVersion.isBlank()) {
            return AppResult.Failure(
                AppError.Validation(
                    code = "catalog.plugin_version_blank",
                ),
            )
        }

        return executeStorageWrite {
            database.withTransaction {
                val now = clock.nowEpochMillis()
                val existing =
                    catalogDao.catalogEntryWithStory(
                        pluginId = pluginId.value,
                        sourceId = metadata.sourceId,
                    )
                val candidates: MutableList<CanonicalStory> =
                    if (existing == null) {
                        storyDao
                            .canonicalStoryCandidates()
                            .map { aggregate -> aggregate.toDomain() }
                            .toMutableList()
                    } else {
                        mutableListOf()
                    }
                val entry =
                    metadata.toCatalogEntry(
                        pluginId = pluginId,
                        pluginVersion = pluginVersion,
                        fetchedAtEpochMillis = now,
                        existing = existing?.entry?.toDomain(),
                    )

                upsertResolvedEntry(
                    pluginId = pluginId,
                    source = metadata.toResolverItem(),
                    entry = entry,
                    existing = existing,
                    candidates = candidates,
                    canonicalAliases = metadata.aliases,
                )
            }
        }
    }

    override suspend fun catalogEntry(
        pluginId: PluginId,
        sourceId: String,
    ): AppResult<CatalogEntryWithStory?> =
        executeStorageRead {
            catalogDao
                .catalogEntryWithStory(
                    pluginId = pluginId.value,
                    sourceId = sourceId,
                )
                ?.toDomain()
        }

    override fun observeCatalogHome(
        pluginId: PluginId,
    ): Flow<CatalogHomeSnapshot?> =
        catalogDao
            .observeHome(pluginId.value)
            .map { rows ->
                rows.toHomeSnapshots().singleOrNull()
            }
            .distinctUntilChanged()

    override fun observeCatalogHomes():
        Flow<List<CatalogHomeSnapshot>> =
        catalogDao
            .observeHomes()
            .map { rows -> rows.toHomeSnapshots() }
            .distinctUntilChanged()

    private suspend fun upsertCard(
        pluginId: PluginId,
        pluginVersion: String,
        item: CatalogSnapshotItem,
        fetchedAtEpochMillis: Long,
        existing: CatalogEntryWithStoryRow?,
        candidates: MutableList<CanonicalStory>,
    ): CatalogEntryWithStory {
        val entry =
            item.toCatalogEntry(
                pluginId = pluginId,
                pluginVersion = pluginVersion,
                fetchedAtEpochMillis = fetchedAtEpochMillis,
                existing = existing?.entry?.toDomain(),
            )

        return upsertResolvedEntry(
            pluginId = pluginId,
            source = item,
            entry = entry,
            existing = existing,
            candidates = candidates,
            canonicalAliases = emptySet(),
        )
    }

    private suspend fun upsertResolvedEntry(
        pluginId: PluginId,
        source: CatalogSnapshotItem,
        entry: CatalogEntry,
        existing: CatalogEntryWithStoryRow?,
        candidates: MutableList<CanonicalStory>,
        canonicalAliases: Set<String>,
    ): CatalogEntryWithStory {
        val storyId =
            if (existing != null) {
                StoryId(existing.storyId)
            } else {
                val resolution =
                    resolver.resolve(
                        pluginId = pluginId,
                        source = source,
                        candidates = candidates,
                    )

                when (resolution) {
                    is CatalogCanonicalResolution.Existing ->
                        resolution.storyId

                    is CatalogCanonicalResolution.Create -> {
                        val story =
                            CanonicalStory(
                                id = resolution.storyId,
                                contentType = source.contentType,
                                preferredTitle = source.title,
                                aliases = canonicalAliases,
                                catalogEntries = emptyList(),
                            )
                        catalogDao.insertCanonicalStory(
                            story.toEntity(),
                        )
                        candidates += story
                        resolution.storyId
                    }
                }
            }

        catalogDao.upsertCatalogEntry(entry.toEntity())

        if (existing == null) {
            catalogDao.deleteCatalogLinks(entry.id.value)
            val inserted =
                catalogDao.insertCatalogLink(
                    StoryCatalogEntryEntity(
                        storyId = storyId.value,
                        catalogEntryId = entry.id.value,
                    ),
                )
            check(inserted != INSERT_CONFLICT) {
                "Expected catalog entry canonical link to be inserted"
            }
            candidates.replaceCatalogEntry(
                storyId = storyId,
                entry = entry,
            )
        }

        return CatalogEntryWithStory(
            storyId = storyId,
            entry = entry,
        )
    }

    private fun CatalogSourceMetadata.toResolverItem():
        CatalogSnapshotItem =
        CatalogSnapshotItem(
            sourceId = sourceId,
            title = title,
            contentType = contentType,
            authors = authors.sorted(),
            coverReference = coverReference,
            score = score,
            scoreScale = scoreScale,
        )

    private fun MutableList<CanonicalStory>.replaceCatalogEntry(
        storyId: StoryId,
        entry: CatalogEntry,
    ) {
        val index = indexOfFirst { story -> story.id == storyId }
        if (index < 0) {
            return
        }

        val current = get(index)
        set(
            index,
            current.copy(
                catalogEntries =
                    current.catalogEntries
                        .filterNot { existing ->
                            existing.id == entry.id
                        } + entry,
            ),
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> executeStorageRead(
        block: suspend () -> T,
    ): AppResult<T> =
        try {
            AppResult.Success(block())
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                throw exception
            }

            AppResult.Failure(
                AppError.Storage(
                    code = "storage.read_failed",
                    retryable = true,
                ),
            )
        }

    private companion object {
        const val INSERT_CONFLICT = -1L
    }
}
