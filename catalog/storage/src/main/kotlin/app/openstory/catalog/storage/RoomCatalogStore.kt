package app.openstory.catalog.storage

import androidx.room.withTransaction
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevision
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.read.DiscoverReadPort
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.validation.CatalogPublicationValidator
import app.openstory.catalog.domain.write.CatalogMutationDiagnostics
import app.openstory.catalog.domain.write.DiscoverPublicationCommand
import app.openstory.catalog.storage.discover.CatalogSourceStateEntity
import app.openstory.catalog.storage.discover.DiscoverCardEntity
import app.openstory.catalog.storage.discover.DiscoverObservationRow
import app.openstory.catalog.storage.story.StorySourceIdentityEntity
import app.openstory.catalog.storage.story.StorySourceSummaryEntity
import app.openstory.common.id.StoryId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class RoomCatalogStore internal constructor(
    private val database: CatalogDatabase,
) : DiscoverReadPort, AutoCloseable {
    private val dao = database.discoverDao()
    private val closed = AtomicBoolean(false)

    override fun observe(
        catalogSourceKey: CatalogSourceKey,
        mediaType: CatalogMediaType,
    ): Flow<DiscoverPersistenceState> = dao.observeDiscover(catalogSourceKey.value, mediaType.name)
        .map(::toPersistenceState)
        .catch { error -> throw error.toStorageFailure(CatalogStorageOperation.READ_DISCOVER) }

    @Suppress("UnusedParameter")
    suspend fun publishDiscover(
        command: DiscoverPublicationCommand,
        retentionProtectedStoryIds: Set<StoryId>,
    ): CatalogMutationDiagnostics {
        val cards = command.cards.toList()
        validatePublication(command, cards)
        return runCatching {
            database.withTransaction {
                val sourceKey = command.catalogSourceKey.value
                val mediaType = command.mediaType.name
                val previousState = dao.sourceState(sourceKey, mediaType)
                val previousStoryIds = previousState?.let { state ->
                    dao.storyIdsForGeneration(sourceKey, mediaType, state.publishedGeneration)
                }.orEmpty()
                val nextGeneration = nextGeneration(previousState?.publishedGeneration)

                val summaryCards = cards.sortedWith(DISCOVER_ORDER)
                    .distinctBy { it.ref.storyId }
                summaryCards.forEach { card -> ensureIdentity(card.ref) }
                dao.upsertSummaries(summaryCards.map { it.toSummaryEntity(command.provenance.acquiredAtEpochMs) })
                dao.upsertSourceState(
                    CatalogSourceStateEntity(
                        sourceKey = sourceKey,
                        mediaType = mediaType,
                        sourceVersion = command.provenance.sourceVersion,
                        publishedGeneration = nextGeneration,
                        publishedAtEpochMs = command.provenance.acquiredAtEpochMs,
                        lastSuccessEpochMs = command.provenance.acquiredAtEpochMs,
                    ),
                )
                dao.insertDiscoverCards(cards.map { it.toDiscoverEntity(sourceKey, mediaType, nextGeneration) })
                previousState?.let { dao.deleteGeneration(sourceKey, mediaType, it.publishedGeneration) }

                CatalogMutationDiagnostics(
                    touchedStoryIds = (previousStoryIds.map(::StoryId) + cards.map { it.ref.storyId }).toSet(),
                )
            }
        }.getOrElse { error ->
            throw error.toStorageFailure(CatalogStorageOperation.PUBLISH_DISCOVER)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) database.close()
    }

    private suspend fun ensureIdentity(ref: StorySourceRef) {
        val byStoryId = dao.identityByStoryId(ref.storyId.value)
        if (byStoryId != null && !byStoryId.matches(ref)) throwIdentityCollision(ref.storyId)
        val bySourceKey = dao.identityBySourceStoryKey(ref.catalogSourceKey.value, ref.sourceStoryId)
        if (bySourceKey != null && bySourceKey.storyId != ref.storyId.value) throwIdentityCollision(ref.storyId)
        if (byStoryId == null && bySourceKey == null) {
            dao.insertIdentity(ref.toIdentityEntity())
            val persisted = dao.identityByStoryId(ref.storyId.value)
            if (persisted == null || !persisted.matches(ref)) throwIdentityCollision(ref.storyId)
        }
    }

    private fun toPersistenceState(rows: List<DiscoverObservationRow>): DiscoverPersistenceState {
        if (rows.isEmpty()) return DiscoverPersistenceState.Absent
        val first = rows.first()
        require(rows.all { it.stateSourceKey == first.stateSourceKey })
        require(rows.all { it.stateMediaType == first.stateMediaType })
        require(rows.all { it.stateGeneration == first.stateGeneration })
        return DiscoverPersistenceState.Published(
            generation = first.stateGeneration,
            provenance = AcquisitionProvenance(
                catalogSourceKey = CatalogSourceKey(first.stateSourceKey),
                sourceVersion = first.stateSourceVersion,
                acquiredAtEpochMs = first.statePublishedAtEpochMs,
            ),
            cards = rows.mapNotNull(DiscoverObservationRow::toDomainCard),
        )
    }
}

private fun validatePublication(command: DiscoverPublicationCommand, cards: List<DiscoverCard>) {
    if (command.provenance.catalogSourceKey != command.catalogSourceKey) {
        throwValidation("provenance.catalogSourceKey", CatalogValidationReason.AUTHORITY_MISMATCH)
    }
    CatalogPublicationValidator.requireValidPublishedCards(cards)
    if (cards.any { it.ref.catalogSourceKey != command.catalogSourceKey }) {
        throwValidation("cards.ref.catalogSourceKey", CatalogValidationReason.AUTHORITY_MISMATCH)
    }
    if (cards.any { it.contentType != command.mediaType }) {
        throwValidation("cards.contentType", CatalogValidationReason.AUTHORITY_MISMATCH)
    }
    if (cards.any { it.sourceVersion != command.provenance.sourceVersion }) {
        throwValidation("cards.sourceVersion", CatalogValidationReason.AUTHORITY_MISMATCH)
    }
}

private fun nextGeneration(previous: Long?): Long = when (previous) {
    null -> 1L
    Long.MAX_VALUE -> throw CatalogFailureException(
        CatalogFailure.InternalInvariant("discover_generation_overflow"),
    )
    else -> previous + 1L
}

private fun throwValidation(field: String, reason: CatalogValidationReason): Nothing =
    throw CatalogFailureException(CatalogFailure.Validation(field, reason))

private fun throwIdentityCollision(storyId: StoryId): Nothing =
    throw CatalogFailureException(CatalogFailure.IdentityCollision(storyId.value))

private fun StorySourceIdentityEntity.matches(ref: StorySourceRef): Boolean =
    storyId == ref.storyId.value &&
        sourceKey == ref.catalogSourceKey.value &&
        sourceStoryId == ref.sourceStoryId

private fun StorySourceRef.toIdentityEntity() = StorySourceIdentityEntity(
    storyId = storyId.value,
    sourceKey = catalogSourceKey.value,
    sourceStoryId = sourceStoryId,
)

private fun DiscoverCard.toSummaryEntity(lastSeenEpochMs: Long): StorySourceSummaryEntity {
    val cover = coverColumns()
    return StorySourceSummaryEntity(
        storyId = ref.storyId.value,
        sourceKey = ref.catalogSourceKey.value,
        sourceVersion = sourceVersion,
        title = title,
        contentType = contentType.name,
        coverLocatorType = cover.type,
        coverLocatorValue = cover.value,
        coverLocatorAux = cover.aux,
        coverRevision = cover.revision,
        ratingValue = rating?.value,
        ratingScale = rating?.scale,
        publicationStatusSummary = publicationStatusSummary,
        latestUpdateEpochMs = latestUpdateEpochMs,
        lastSeenEpochMs = lastSeenEpochMs,
    )
}

private fun DiscoverCard.toDiscoverEntity(
    sourceKey: String,
    mediaType: String,
    generation: Long,
): DiscoverCardEntity {
    val cover = coverColumns()
    return DiscoverCardEntity(
        sourceKey = sourceKey,
        mediaType = mediaType,
        sourceVersion = sourceVersion,
        generation = generation,
        sectionKind = sectionKind.name,
        itemPosition = itemPosition,
        storyId = ref.storyId.value,
        sourceStoryId = ref.sourceStoryId,
        title = title,
        contentType = contentType.name,
        coverLocatorType = cover.type,
        coverLocatorValue = cover.value,
        coverLocatorAux = cover.aux,
        coverRevision = cover.revision,
        ratingValue = rating?.value,
        ratingScale = rating?.scale,
        publicationStatusSummary = publicationStatusSummary,
        latestUpdateEpochMs = latestUpdateEpochMs,
    )
}

private fun DiscoverCard.coverColumns(): CoverColumns = when (val locator = coverLocator) {
    null -> CoverColumns(null, null, null, null)
    is CoverLocator.TrustedLocalResource -> CoverColumns(
        type = COVER_LOCAL,
        value = locator.logicalAssetId,
        aux = locator.assetVersion,
        revision = requireNotNull(coverAssetKey).coverRevision.value,
    )
    is CoverLocator.RemoteHttps -> CoverColumns(
        type = COVER_REMOTE,
        value = locator.normalizedUri.value,
        aux = null,
        revision = requireNotNull(coverAssetKey).coverRevision.value,
    )
}

private fun DiscoverObservationRow.toDomainCard(): DiscoverCard? {
    val storyIdValue = cardStoryId ?: return null
    val sourceStoryId = requireNotNull(cardSourceStoryId)
    val ref = StorySourceRef(
        storyId = StoryId(storyIdValue),
        catalogSourceKey = CatalogSourceKey(stateSourceKey),
        sourceStoryId = sourceStoryId,
    )
    val cover = toDomainCover(ref)
    val rating = when {
        cardRatingValue == null && cardRatingScale == null -> null
        else -> CatalogRating(requireNotNull(cardRatingValue), requireNotNull(cardRatingScale))
    }
    return DiscoverCard(
        ref = ref,
        sectionKind = CatalogSectionKind.valueOf(requireNotNull(cardSectionKind)),
        itemPosition = requireNotNull(cardItemPosition),
        title = requireNotNull(cardTitle),
        contentType = CatalogMediaType.valueOf(requireNotNull(cardContentType)),
        sourceVersion = requireNotNull(cardSourceVersion),
        coverLocator = cover?.first,
        coverAssetKey = cover?.second,
        rating = rating,
        publicationStatusSummary = cardPublicationStatusSummary,
        latestUpdateEpochMs = cardLatestUpdateEpochMs,
    )
}

private fun DiscoverObservationRow.toDomainCover(
    ref: StorySourceRef,
): Pair<CoverLocator, CoverAssetKey>? = when (cardCoverLocatorType) {
    null -> {
        require(cardCoverLocatorValue == null && cardCoverLocatorAux == null && cardCoverRevision == null)
        null
    }
    COVER_LOCAL -> {
        val locator = CoverLocator.TrustedLocalResource(
            logicalAssetId = requireNotNull(cardCoverLocatorValue),
            assetVersion = requireNotNull(cardCoverLocatorAux),
        )
        val key = CoverAssetKey(ref.storyId, CoverRevision(requireNotNull(cardCoverRevision)))
        locator to key
    }
    COVER_REMOTE -> {
        require(cardCoverLocatorAux == null)
        val revision = CoverRevision(requireNotNull(cardCoverRevision))
        val locator = CoverLocator.RemoteHttps(
            catalogSourceKey = ref.catalogSourceKey,
            normalizedUri = RemoteHttpsUriV1.parseAndNormalize(requireNotNull(cardCoverLocatorValue)),
            revision = revision,
        )
        locator to CoverAssetKey(ref.storyId, revision)
    }
    else -> error("Unknown cover locator type")
}

private data class CoverColumns(
    val type: String?,
    val value: String?,
    val aux: String?,
    val revision: String?,
)

private val DISCOVER_ORDER = compareBy<DiscoverCard>(
    { SECTION_ORDER.getValue(it.sectionKind) },
    DiscoverCard::itemPosition,
)
private val SECTION_ORDER = mapOf(
    CatalogSectionKind.POPULAR to 0,
    CatalogSectionKind.LATEST_UPDATES to 1,
    CatalogSectionKind.TOP_RATED to 2,
)
private const val COVER_LOCAL = "LOCAL"
private const val COVER_REMOTE = "REMOTE"
