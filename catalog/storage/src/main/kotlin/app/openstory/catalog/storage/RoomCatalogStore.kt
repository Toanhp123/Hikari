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
import app.openstory.catalog.domain.model.CatalogSectionCaps
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.read.DiscoverReadPort
import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.domain.read.StoryDetailReadPort
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.validation.CatalogPublicationValidator
import app.openstory.catalog.domain.write.CatalogMutationDiagnostics
import app.openstory.catalog.domain.write.CatalogMutationBounds
import app.openstory.catalog.domain.write.CatalogWritePort
import app.openstory.catalog.domain.write.DiscoverPublicationCommand
import app.openstory.catalog.domain.write.StoryDetailPublicationCommand
import app.openstory.catalog.storage.discover.CatalogSourceStateEntity
import app.openstory.catalog.storage.discover.DiscoverCardEntity
import app.openstory.catalog.storage.discover.DiscoverObservationRow
import app.openstory.catalog.storage.story.StorySourceIdentityEntity
import app.openstory.catalog.storage.story.StorySourceSummaryEntity
import app.openstory.catalog.storage.story.matches
import app.openstory.catalog.storage.story.toIdentityEntity
import app.openstory.catalog.storage.retention.StoryRetentionStorage
import app.openstory.common.id.StoryId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class RoomCatalogStore internal constructor(
    private val database: CatalogDatabase,
) : DiscoverReadPort, StoryDetailReadPort, CatalogWritePort, AutoCloseable {
    private val dao = database.discoverDao()
    private val storyStorage = StoryStorage(database)
    private val retentionStorage = StoryRetentionStorage(database.storyRetentionDao())
    private val closed = AtomicBoolean(false)

    override fun observe(
        catalogSourceKey: CatalogSourceKey,
        mediaType: CatalogMediaType,
    ): Flow<DiscoverPersistenceState> = dao.observeDiscover(catalogSourceKey.value, mediaType.name)
        .map(::toPersistenceState)
        .catch { error -> throw error.toStorageFailure(CatalogStorageOperation.READ_DISCOVER) }

    override suspend fun publishDiscover(
        command: DiscoverPublicationCommand,
        retentionProtectedStoryIds: Set<StoryId>,
    ): CatalogMutationDiagnostics {
        requireProtectedStoryBound(retentionProtectedStoryIds)
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
                ensureIdentities(summaryCards.map(DiscoverCard::ref))
                requireConsistentStoredContentTypes(summaryCards)
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

                val currentStoryIds = cards.mapTo(linkedSetOf()) { it.ref.storyId }
                val touched = linkedSetOf<StoryId>().apply {
                    previousStoryIds.mapTo(this, ::StoryId)
                    addAll(currentStoryIds)
                }
                retentionStorage.removeNewlyReachable(currentStoryIds)
                previousStoryIds.asSequence()
                    .map(::StoryId)
                    .filterNot(currentStoryIds::contains)
                    .forEach { removedStoryId ->
                        retentionStorage.classify(
                            storyId = removedStoryId,
                            identityExists = true,
                            protectedStoryIds = retentionProtectedStoryIds,
                            lastAccessedEpochMs = command.provenance.acquiredAtEpochMs,
                        )
                    }
                var remainingEvictions = CatalogSectionCaps.MAX_DISCOVER_MEMBERSHIPS
                while (remainingEvictions > 0) {
                    val evicted = retentionStorage.evictOneOverflow(retentionProtectedStoryIds) ?: break
                    touched += evicted
                    remainingEvictions -= 1
                }
                retentionStorage.requireWithinLimit()
                if (touched.size > CatalogMutationBounds.MAX_DISCOVER_TOUCHED_STORY_IDS) {
                    throw CatalogFailureException(
                        CatalogFailure.InternalInvariant("discover_mutation_touch_limit"),
                    )
                }
                CatalogMutationDiagnostics(
                    touchedStoryIds = touched,
                )
            }
        }.getOrElse { error ->
            throw error.toStorageFailure(CatalogStorageOperation.PUBLISH_DISCOVER)
        }
    }

    override fun observe(ref: StorySourceRef): Flow<StoryDetailProjection?> = storyStorage.observe(ref)

    override suspend fun publishStoryDetail(command: StoryDetailPublicationCommand) =
        storyStorage.publishStoryDetail(command)

    override suspend fun touchStoryAccess(ref: StorySourceRef, accessedAtEpochMs: Long) =
        storyStorage.touchStoryAccess(ref, accessedAtEpochMs)

    override suspend fun releaseStoryDemand(
        ref: StorySourceRef,
        retentionProtectedStoryIds: Set<StoryId>,
        releasedAtEpochMs: Long,
    ): CatalogMutationDiagnostics = storyStorage.releaseStoryDemand(
        ref = ref,
        retentionProtectedStoryIds = retentionProtectedStoryIds,
        releasedAtEpochMs = releasedAtEpochMs,
    )

    override fun close() {
        if (closed.compareAndSet(false, true)) database.close()
    }

    private suspend fun ensureIdentities(refs: List<StorySourceRef>) {
        if (refs.isEmpty()) return
        val distinctRefs = refs.distinctBy { it.storyId }
        val storyIds = distinctRefs.mapTo(linkedSetOf()) { it.storyId.value }
        val sourceStoryIds = distinctRefs.mapTo(linkedSetOf()) { it.sourceStoryId }
        val byStoryId = dao.identitiesByStoryIds(storyIds).associateBy { it.storyId }
        val bySourceStoryId = dao.identitiesBySourceStoryIds(
            distinctRefs.first().catalogSourceKey.value,
            sourceStoryIds,
        ).associateBy { it.sourceStoryId }
        distinctRefs.forEach { ref -> validateIdentity(ref, byStoryId, bySourceStoryId) }
        val missing = distinctRefs.filter { ref ->
            ref.storyId.value !in byStoryId && ref.sourceStoryId !in bySourceStoryId
        }
        if (missing.isNotEmpty()) dao.insertIdentities(missing.map { it.toIdentityEntity() })
        val persisted = dao.identitiesByStoryIds(storyIds).associateBy { it.storyId }
        distinctRefs.forEach { ref ->
            if (persisted[ref.storyId.value]?.matches(ref) != true) throwIdentityCollision(ref.storyId)
        }
    }

    private fun validateIdentity(
        ref: StorySourceRef,
        byStoryId: Map<String, StorySourceIdentityEntity>,
        bySourceStoryId: Map<String, StorySourceIdentityEntity>,
    ) {
        val persistedByStoryId = byStoryId[ref.storyId.value]
        if (persistedByStoryId != null && !persistedByStoryId.matches(ref)) {
            throwIdentityCollision(ref.storyId)
        }
        val persistedBySourceStoryId = bySourceStoryId[ref.sourceStoryId]
        if (persistedBySourceStoryId != null && persistedBySourceStoryId.storyId != ref.storyId.value) {
            throwIdentityCollision(ref.storyId)
        }
    }

    private suspend fun requireConsistentStoredContentTypes(cards: List<DiscoverCard>) {
        if (cards.isEmpty()) return
        val storyIds = cards.mapTo(linkedSetOf()) { it.ref.storyId.value }
        val stored = dao.summariesByStoryIds(storyIds).associateBy { it.storyId }
        val hasContentTypeConflict = cards.any { card ->
            stored[card.ref.storyId.value]?.contentType?.let { it != card.contentType.name } == true
        }
        if (hasContentTypeConflict) {
            throwValidation("cards.contentType", CatalogValidationReason.AUTHORITY_MISMATCH)
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

internal fun throwValidation(field: String, reason: CatalogValidationReason): Nothing =
    throw CatalogFailureException(CatalogFailure.Validation(field, reason))

internal fun throwIdentityCollision(storyId: StoryId): Nothing =
    throw CatalogFailureException(CatalogFailure.IdentityCollision(storyId.value))

internal fun requireProtectedStoryBound(storyIds: Set<StoryId>) {
    if (storyIds.size > CatalogMutationBounds.MAX_RETENTION_PROTECTED_STORY_IDS) {
        throw CatalogFailureException(CatalogFailure.InternalInvariant("retention_protected_story_limit"))
    }
}

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
