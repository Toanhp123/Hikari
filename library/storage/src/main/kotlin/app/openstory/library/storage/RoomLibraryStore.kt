package app.openstory.library.storage

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevision
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.common.id.StoryId
import app.openstory.library.domain.LibraryArtworkSnapshot
import app.openstory.library.domain.LibraryCursor
import app.openstory.library.domain.LibraryEntry
import app.openstory.library.domain.LibraryFilter
import app.openstory.library.domain.LibraryMutationResult
import app.openstory.library.domain.LibraryPort
import app.openstory.library.domain.LibraryPresentationSnapshot
import app.openstory.library.domain.LibraryQuery
import app.openstory.library.domain.LibraryWindow
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryStorageFactory(
    context: Context,
    private val databaseName: String = LibraryDatabase.NAME,
) {
    private val applicationContext = context.applicationContext

    fun open(): RoomLibraryStore = RoomLibraryStore(
        Room.databaseBuilder(applicationContext, LibraryDatabase::class.java, databaseName).build(),
    )
}

class RoomLibraryStore internal constructor(
    private val database: LibraryDatabase,
) : LibraryPort, AutoCloseable {
    private val dao = database.libraryDao()
    private val closed = AtomicBoolean(false)

    override fun observeMembership(ref: StorySourceRef): Flow<LibraryEntry?> =
        dao.observeMembership(ref.storyId.value).map { entity -> entity?.toDomain() }

    override fun observeWindow(query: LibraryQuery): Flow<LibraryWindow> {
        val entities = when {
            query.normalizedText.isEmpty() -> observeBlankWindow(query)
            else -> observeSearchWindow(query, ftsMatchQuery(query.normalizedText))
        }
        return entities.map { rows -> rows.toWindow(query.limit) }
    }

    override suspend fun add(entry: LibraryEntry): LibraryMutationResult = database.withTransaction {
        val entity = entry.toEntity()
        if (dao.insertEntry(entity) == INSERT_CONFLICT) return@withTransaction LibraryMutationResult.NO_OP
        dao.insertSearch(entity.toSearchEntity())
        LibraryMutationResult.CHANGED
    }

    override suspend fun remove(ref: StorySourceRef): LibraryMutationResult = database.withTransaction {
        if (dao.deleteEntry(ref.storyId.value) == 0) return@withTransaction LibraryMutationResult.NO_OP
        dao.deleteSearch(ref.storyId.value)
        LibraryMutationResult.CHANGED
    }

    override suspend fun enrichSnapshot(
        ref: StorySourceRef,
        snapshot: LibraryPresentationSnapshot,
    ): LibraryMutationResult = database.withTransaction {
        val existing = dao.entry(ref.storyId.value) ?: return@withTransaction LibraryMutationResult.NO_OP
        val replacement = existing.copySnapshot(ref, snapshot)
        if (replacement == existing) return@withTransaction LibraryMutationResult.NO_OP
        check(dao.updateEntry(replacement) == 1)
        dao.deleteSearch(ref.storyId.value)
        dao.insertSearch(replacement.toSearchEntity())
        LibraryMutationResult.CHANGED
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) database.close()
    }

    private fun observeBlankWindow(query: LibraryQuery): Flow<List<LibraryEntryEntity>> = when (query.filter) {
        LibraryFilter.ALL -> query.after?.let { cursor ->
            dao.observeUnfilteredAfter(cursor.savedAtEpochMs, cursor.storyId, query.limit)
        } ?: dao.observeUnfilteredFirst(query.limit)
        LibraryFilter.MANGA, LibraryFilter.LIGHT_NOVEL -> query.after?.let { cursor ->
            dao.observeFilteredAfter(
                query.filter.mediaTypeName(),
                cursor.savedAtEpochMs,
                cursor.storyId,
                query.limit,
            )
        } ?: dao.observeFilteredFirst(query.filter.mediaTypeName(), query.limit)
    }

    private fun observeSearchWindow(
        query: LibraryQuery,
        matchQuery: String,
    ): Flow<List<LibraryEntryEntity>> = when (query.filter) {
        LibraryFilter.ALL -> query.after?.let { cursor ->
            dao.observeSearchAfter(matchQuery, cursor.savedAtEpochMs, cursor.storyId, query.limit)
        } ?: dao.observeSearchFirst(matchQuery, query.limit)
        LibraryFilter.MANGA, LibraryFilter.LIGHT_NOVEL -> query.after?.let { cursor ->
            dao.observeFilteredSearchAfter(
                matchQuery,
                query.filter.mediaTypeName(),
                cursor.savedAtEpochMs,
                cursor.storyId,
                query.limit,
            )
        } ?: dao.observeFilteredSearchFirst(matchQuery, query.filter.mediaTypeName(), query.limit)
    }
}

private fun LibraryEntry.toEntity(): LibraryEntryEntity {
    val artwork = snapshot.artwork
    val locator = artwork?.coverLocator
    return LibraryEntryEntity(
        storyId = ref.storyId.value,
        sourceKey = ref.catalogSourceKey.value,
        sourceStoryId = ref.sourceStoryId,
        originMediaContext = originMediaContext.name,
        savedAtEpochMs = savedAtEpochMs,
        title = snapshot.title,
        supportingText = snapshot.supportingText,
        coverLocatorType = locator?.storageType(),
        coverLocatorValue = locator?.storageValue(),
        coverLocatorAux = locator?.storageAux(),
        coverRevision = artwork?.coverAssetKey?.coverRevision?.value,
    )
}

private fun LibraryEntryEntity.copySnapshot(
    ref: StorySourceRef,
    snapshot: LibraryPresentationSnapshot,
): LibraryEntryEntity = LibraryEntry(
    ref = ref,
    originMediaContext = CatalogMediaType.valueOf(originMediaContext),
    savedAtEpochMs = savedAtEpochMs,
    snapshot = snapshot,
).toEntity()

private fun LibraryEntryEntity.toSearchEntity() = LibrarySearchFts(
    storyId = storyId,
    title = title,
    supportingText = supportingText,
)

private fun LibraryEntryEntity.toDomain(): LibraryEntry {
    val ref = StorySourceRef(
        storyId = StoryId(storyId),
        catalogSourceKey = CatalogSourceKey(sourceKey),
        sourceStoryId = sourceStoryId,
    )
    val locator = toCoverLocator()
    val artwork = locator?.let {
        LibraryArtworkSnapshot(
            coverAssetKey = CoverAssetKey(ref.storyId, CoverRevision(requireNotNull(coverRevision))),
            coverLocator = it,
        )
    }
    return LibraryEntry(
        ref = ref,
        originMediaContext = CatalogMediaType.valueOf(originMediaContext),
        savedAtEpochMs = savedAtEpochMs,
        snapshot = LibraryPresentationSnapshot(title, artwork, supportingText),
    )
}

private fun LibraryEntryEntity.toCoverLocator(): CoverLocator? = when (coverLocatorType) {
    null -> {
        require(coverLocatorValue == null && coverLocatorAux == null && coverRevision == null)
        null
    }
    LOCAL_COVER -> CoverLocator.TrustedLocalResource(
        logicalAssetId = requireNotNull(coverLocatorValue),
        assetVersion = requireNotNull(coverLocatorAux),
    )
    REMOTE_COVER -> CoverLocator.RemoteHttps(
        catalogSourceKey = CatalogSourceKey(requireNotNull(coverLocatorAux)),
        normalizedUri = RemoteHttpsUriV1.parseAndNormalize(requireNotNull(coverLocatorValue)),
        revision = CoverRevision(requireNotNull(coverRevision)),
    )
    else -> error("Unknown Library cover locator type")
}

private fun CoverLocator.storageType(): String = when (this) {
    is CoverLocator.TrustedLocalResource -> LOCAL_COVER
    is CoverLocator.RemoteHttps -> REMOTE_COVER
}

private fun CoverLocator.storageValue(): String = when (this) {
    is CoverLocator.TrustedLocalResource -> logicalAssetId
    is CoverLocator.RemoteHttps -> normalizedUri.value
}

private fun CoverLocator.storageAux(): String = when (this) {
    is CoverLocator.TrustedLocalResource -> assetVersion
    is CoverLocator.RemoteHttps -> catalogSourceKey.value
}

private fun List<LibraryEntryEntity>.toWindow(limit: Int): LibraryWindow {
    require(size <= limit)
    val items = map(LibraryEntryEntity::toDomain)
    val nextCursor = takeIf { size == limit }?.lastOrNull()?.let { last ->
        LibraryCursor(last.savedAtEpochMs, last.storyId)
    }
    return LibraryWindow(items, nextCursor)
}

private fun LibraryFilter.mediaTypeName(): String = when (this) {
    LibraryFilter.MANGA -> CatalogMediaType.MANGA.name
    LibraryFilter.LIGHT_NOVEL -> CatalogMediaType.LIGHT_NOVEL.name
    LibraryFilter.ALL -> error("ALL has no media type")
}

private fun ftsMatchQuery(text: String): String = "\"${text.replace("\"", "\"\"")}\""

private const val INSERT_CONFLICT = -1L
private const val LOCAL_COVER = "LOCAL"
private const val REMOTE_COVER = "REMOTE"
