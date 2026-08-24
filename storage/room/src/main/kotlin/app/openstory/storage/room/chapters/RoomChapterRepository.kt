package app.openstory.storage.room.chapters

import androidx.room.withTransaction
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterOverrideKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.notification.ChapterChangeDetector
import app.openstory.chapters.notification.ChapterChangeFact
import app.openstory.chapters.notification.NotificationDrainScheduler
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.repository.ChapterReleaseLookup
import app.openstory.chapters.repository.ChapterSyncPhase
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.RoomStoryIdentityResolver
import app.openstory.storage.room.catalog.observeResolvedSet
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class RoomChapterRepository internal constructor(
    private val database: OpenStoryDatabase,
    private val dao: ChapterDao,
    private val syncDao: ChapterSyncDao,
    private val identity: RoomStoryIdentityResolver,
    private val notificationDao: NotificationEventDao,
    private val notificationScheduler: NotificationDrainScheduler,
    private val nowEpochMillis: () -> Long,
) : ChapterRepository, ChapterReleaseLookup {
    constructor(database: OpenStoryDatabase) : this(
        database,
        database.chapterDao(),
        database.chapterSyncDao(),
        RoomStoryIdentityResolver(database),
        database.notificationEventDao(),
        NotificationDrainScheduler { },
        System::currentTimeMillis,
    )

    constructor(
        database: OpenStoryDatabase,
        notificationScheduler: NotificationDrainScheduler,
    ) : this(
        database,
        database.chapterDao(),
        database.chapterSyncDao(),
        RoomStoryIdentityResolver(database),
        database.notificationEventDao(),
        notificationScheduler,
        System::currentTimeMillis,
    )

    override fun observeAll(): Flow<List<CanonicalChapterGroup>> =
        dao.observeAllGroups().map(List<CanonicalChapterWithReleases>::toModelsByIdentity)

    override fun observeForStories(storyIds: Set<StoryId>): Flow<List<CanonicalChapterGroup>> =
        if (storyIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            identity.observeResolvedSet(storyIds).flatMapLatest { resolved ->
                dao.observeGroups(resolved.map(StoryId::value))
                    .map(List<CanonicalChapterWithReleases>::toModelsByIdentity)
            }
        }

    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> =
        identity.observeResolved(storyId).flatMapLatest { resolved ->
            dao.observeGroups(resolved.value).map(List<CanonicalChapterWithReleases>::toModels)
        }

    override suspend fun snapshot(storyId: StoryId): ChapterGraphSnapshot {
        val resolved = identity.resolve(storyId)
        val groups = dao.groups(resolved.value).toModels()
        return ChapterGraphSnapshot(
            chapters = groups.map(CanonicalChapterGroup::chapter),
            releases = dao.releases(resolved.value).map(ChapterReleaseEntity::toModel).sortedBy { it.id.value },
            overrides = dao.overrides(resolved.value).map(ChapterAggregationOverrideEntity::toModel)
                .sortedBy { it.releaseId.value },
        )
    }

    override suspend fun findRelease(releaseId: ChapterReleaseId): ChapterRelease? =
        dao.findRelease(releaseId.value)?.toModel()

    override suspend fun commit(mutation: ChapterMutation): ChapterCommitResult {
        var evidenceCommitted = false
        val commitResult = try {
            database.withTransaction {
                val resolved = identity.resolve(mutation.storyId)
                val before = snapshotResolved(resolved)
                val normalizedCreates = mutation.plan.creates.map { it.copy(storyId = resolved) }
                val normalizedReleases = mutation.releases.map { it.copy(storyId = resolved) }
                val normalizedSyncState = mutation.syncState?.copy(storyId = resolved)
                if (normalizedCreates.isNotEmpty()) {
                    dao.upsertChapters(normalizedCreates.map(CanonicalChapter::toEntity))
                }
                if (normalizedReleases.isNotEmpty()) {
                    dao.upsertReleases(normalizedReleases.map(ChapterRelease::toEntity))
                }
                if (mutation.plan.unlinks.isNotEmpty()) {
                    dao.unlink(mutation.plan.unlinks.map { it.value })
                }
                val linkedChapterIds = linkedSetOf<String>()
                mutation.plan.links.forEach { link ->
                    check(dao.link(link.releaseId.value, link.canonicalChapterId.value) == 1) {
                        "Chapter release link target is missing"
                    }
                    linkedChapterIds += link.canonicalChapterId.value
                }
                if (linkedChapterIds.isNotEmpty()) {
                    dao.restore(linkedChapterIds)
                }
                if (mutation.plan.tombstones.isNotEmpty()) {
                    dao.tombstone(mutation.plan.tombstones.map { it.value })
                }
                normalizedSyncState?.let { syncDao.upsert(it.toEntity()) }
                val after = snapshotResolved(resolved)
                val fingerprint = normalizedSyncState?.fingerprint?.takeIf(String::isNotBlank)
                    ?: after.stableFingerprint()
                val occurredAt = normalizedSyncState?.updatedAtEpochMillis ?: nowEpochMillis()
                val facts = ChapterChangeDetector().detect(before, after, fingerprint, occurredAt)
                insertNotificationEvidence(facts, occurredAt)
                evidenceCommitted = facts.isNotEmpty()
            }
            ChapterCommitResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ChapterCommitResult.Failure("chapter.storage_commit_failed", true)
        }
        if (commitResult == ChapterCommitResult.Success && evidenceCommitted) {
            try {
                notificationScheduler.schedule()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The durable pending row is recovered by the startup drain wake.
            }
        }
        return commitResult
    }

    override suspend fun saveOverride(storyId: StoryId, override: ChapterAggregationOverride) {
        syncDao.upsertOverride(override.toEntity(identity.resolve(storyId)))
    }

    override suspend fun syncState(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
    ): ChapterSyncState? = syncDao.find(identity.resolve(storyId).value, pluginId.value, sourceStoryId)?.toModel()

    private suspend fun snapshotResolved(storyId: StoryId): ChapterGraphSnapshot = ChapterGraphSnapshot(
        chapters = dao.groups(storyId.value).toModels().map(CanonicalChapterGroup::chapter),
        releases = dao.releases(storyId.value).map(ChapterReleaseEntity::toModel).sortedBy { it.id.value },
        overrides = dao.overrides(storyId.value).map(ChapterAggregationOverrideEntity::toModel)
            .sortedBy { it.releaseId.value },
    )

    private suspend fun insertNotificationEvidence(facts: List<ChapterChangeFact>, createdAt: Long) {
        if (facts.isEmpty()) return
        notificationDao.insertEvents(facts.map(ChapterChangeFact::toEntity))
        val ids = notificationDao.eventIds(facts.map(ChapterChangeFact::eventKey))
        check(ids.size == facts.size) { "Chapter notification evidence was not committed" }
        notificationDao.insertDeliveries(
            ids.map { eventId ->
                NotificationDeliveryEntity(
                    eventId = eventId,
                    status = "PENDING",
                    claimToken = null,
                    claimExpiresAtEpochMillis = null,
                    attemptCount = 0,
                    nextAttemptAtEpochMillis = createdAt,
                    notificationId = null,
                    reasonCode = null,
                    lastErrorCode = null,
                    updatedAtEpochMillis = createdAt,
                )
            },
        )
    }
}

private fun ChapterChangeFact.toEntity() = ChapterChangeEventEntity(
    eventKey = eventKey,
    storyId = storyId.value,
    chapterId = chapterId.value,
    releaseId = releaseId?.value,
    changeKind = kind.name,
    chapterCommitFingerprint = chapterCommitFingerprint,
    occurredAtEpochMillis = occurredAtEpochMillis,
)

private fun ChapterGraphSnapshot.stableFingerprint(): String {
    val value = buildString {
        chapters.sortedBy { it.id.value }.forEach { chapter ->
            append(chapter.id.value).append(':').append(chapter.tombstoned).append(';')
        }
        releases.sortedBy { it.id.value }.forEach { release ->
            append(release.id.value).append(':').append(release.canonicalChapterId?.value).append(';')
        }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and UNSIGNED_BYTE_MASK) }
}

private const val UNSIGNED_BYTE_MASK = 0xff

private fun List<CanonicalChapterWithReleases>.toModels(): List<CanonicalChapterGroup> = map { group ->
    val releases = group.releases.map(ChapterReleaseEntity::toModel).sortedWith(releaseComparator)
    CanonicalChapterGroup(group.chapter.toModel(releases.mapTo(linkedSetOf()) { it.id }), releases)
}.sortedWith(groupComparator)

private fun List<CanonicalChapterWithReleases>.toModelsByIdentity(): List<CanonicalChapterGroup> =
    map { group ->
        val releases = group.releases.map(ChapterReleaseEntity::toModel).sortedWith(releaseComparator)
        CanonicalChapterGroup(group.chapter.toModel(releases.mapTo(linkedSetOf()) { it.id }), releases)
    }

private val groupComparator = compareBy<CanonicalChapterGroup> { it.chapter.parsedLabel.volume == null }
    .thenBy { it.chapter.parsedLabel.volume }
    .thenBy { it.chapter.parsedLabel.chapter == null }
    .thenBy { it.chapter.parsedLabel.chapter }
    .thenBy { it.chapter.parsedLabel.kind.ordinal }
    .thenBy { it.chapter.displayLabel }
    .thenBy { it.chapter.id.value }

private val releaseComparator = compareByDescending<ChapterRelease> { it.publishedAtEpochMillis }
    .thenBy { it.pluginId.value }
    .thenBy { it.id.value }

private fun CanonicalChapter.toEntity() = CanonicalChapterEntity(
    canonicalChapterId = id.value,
    storyId = storyId.value,
    kind = parsedLabel.kind.name,
    volume = parsedLabel.volume?.toPlainString(),
    chapter = parsedLabel.chapter?.toPlainString(),
    part = parsedLabel.part,
    normalizedTitle = parsedLabel.normalizedTitle,
    displayLabel = displayLabel,
    tombstoned = tombstoned,
)

private fun CanonicalChapterEntity.toModel(releaseIds: Set<ChapterReleaseId>) = CanonicalChapter(
    id = CanonicalChapterId(canonicalChapterId),
    storyId = StoryId(storyId),
    parsedLabel = parsedLabel(kind, volume, chapter, part, normalizedTitle),
    displayLabel = displayLabel,
    tombstoned = tombstoned,
    releaseIds = releaseIds,
)

private fun ChapterRelease.toEntity() = ChapterReleaseEntity(
    chapterReleaseId = id.value,
    storyId = storyId.value,
    pluginId = pluginId.value,
    sourceStoryId = sourceStoryId,
    sourceReleaseId = sourceReleaseId,
    displayLabel = displayLabel,
    kind = parsedLabel.kind.name,
    volume = parsedLabel.volume?.toPlainString(),
    chapter = parsedLabel.chapter?.toPlainString(),
    part = parsedLabel.part,
    normalizedTitle = parsedLabel.normalizedTitle,
    languageTag = languageTag,
    publishedAtEpochMillis = publishedAtEpochMillis,
    canonicalChapterId = canonicalChapterId?.value,
)

private fun ChapterReleaseEntity.toModel() = ChapterRelease(
    id = ChapterReleaseId(chapterReleaseId),
    storyId = StoryId(storyId),
    pluginId = PluginId(pluginId),
    sourceStoryId = sourceStoryId,
    sourceReleaseId = sourceReleaseId,
    displayLabel = displayLabel,
    parsedLabel = parsedLabel(kind, volume, chapter, part, normalizedTitle),
    languageTag = languageTag,
    publishedAtEpochMillis = publishedAtEpochMillis,
    canonicalChapterId = canonicalChapterId?.let(::CanonicalChapterId),
)

private fun parsedLabel(
    kind: String,
    volume: String?,
    chapter: String?,
    part: Int?,
    normalizedTitle: String?,
) = ParsedChapterLabel(
    kind = ChapterKind.valueOf(kind),
    volume = volume?.let(::BigDecimal),
    chapter = chapter?.let(::BigDecimal),
    part = part,
    normalizedTitle = normalizedTitle,
)

private fun ChapterAggregationOverride.toEntity(storyId: StoryId) = ChapterAggregationOverrideEntity(
    storyId = storyId.value,
    chapterReleaseId = releaseId.value,
    canonicalChapterId = canonicalChapterId?.value,
    kind = kind.name,
)

private fun ChapterAggregationOverrideEntity.toModel() = ChapterAggregationOverride(
    releaseId = ChapterReleaseId(chapterReleaseId),
    canonicalChapterId = canonicalChapterId?.let(::CanonicalChapterId),
    kind = ChapterOverrideKind.valueOf(kind),
)

private fun ChapterSyncState.toEntity() = ChapterSyncStateEntity(
    storyId = storyId.value,
    pluginId = pluginId.value,
    sourceStoryId = sourceStoryId,
    phase = phase.name,
    cursor = cursor,
    checkpoint = checkpoint,
    fingerprint = fingerprint,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun ChapterSyncStateEntity.toModel() = ChapterSyncState(
    storyId = StoryId(storyId),
    pluginId = PluginId(pluginId),
    sourceStoryId = sourceStoryId,
    phase = ChapterSyncPhase.valueOf(phase),
    cursor = cursor,
    checkpoint = checkpoint,
    fingerprint = fingerprint,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
