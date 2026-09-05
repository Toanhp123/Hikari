package app.openstory.downloads.cache

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.assets.ReaderAssetMetadata
import app.openstory.reader.assets.ReaderAssetActiveProtections
import app.openstory.reader.assets.ReaderAssetProtectionClass

enum class AutomaticCacheRetention {
    STALE_INVALIDATED,
    COLD_SPECULATIVE_IMAGE,
    WARM_SPECULATIVE_IMAGE,
    TRANSITION_SPECULATIVE_IMAGE,
    CURRENT_AHEAD_SPECULATIVE_IMAGE,
    WARM_DOCUMENT,
    CONSUMED_IMAGE_HISTORY,
    PROGRESS_PROTECTED_DOCUMENT,
    RECENT_IMAGE_HISTORY_2,
    RECENT_IMAGE_HISTORY_1,
    ACTIVE_CONSUMED_IMAGE,
    ACTIVE_INTERACTIVE_IMAGE,
    ACTIVE_READ_LEASE,
}

internal sealed interface AutomaticCacheCandidate {
    val retention: AutomaticCacheRetention
    val bytes: Long
    val recencyEpochMillis: Long
    val stableId: String

    data class Document(
        val entry: CacheEntry,
        override val retention: AutomaticCacheRetention,
    ) : AutomaticCacheCandidate {
        override val bytes: Long = entry.sizeBytes
        override val recencyEpochMillis: Long = entry.lastAccessedAtEpochMillis
        override val stableId: String = buildString {
            append(entry.key.releaseId.value)
            append('\u0000')
            append(entry.key.contentFingerprint)
        }
    }

    data class Image(
        val metadata: ReaderAssetMetadata,
        override val retention: AutomaticCacheRetention,
    ) : AutomaticCacheCandidate {
        override val bytes: Long = metadata.byteSize
        override val recencyEpochMillis: Long = metadata.lastConsumedAtEpochMillis
            ?: metadata.lastAccessedAtEpochMillis
        override val stableId: String = metadata.logicalAssetKeyHash.value
    }
}

internal fun normalizedAutomaticCacheCandidates(
    documents: List<CacheEntry>,
    images: List<ReaderAssetMetadata>,
    progressProtectedReleaseIds: Set<ChapterReleaseId>,
    activeProtections: ReaderAssetActiveProtections,
): List<AutomaticCacheCandidate> = buildList {
    documents.forEach { entry ->
        add(
            AutomaticCacheCandidate.Document(
                entry = entry,
                retention = if (entry.key.releaseId in progressProtectedReleaseIds) {
                    AutomaticCacheRetention.PROGRESS_PROTECTED_DOCUMENT
                } else {
                    AutomaticCacheRetention.WARM_DOCUMENT
                },
            ),
        )
    }
    images.forEach { metadata ->
        add(
            AutomaticCacheCandidate.Image(
                metadata = metadata,
                retention = imageRetention(metadata, activeProtections),
            ),
        )
    }
}.sortedWith(
    compareBy<AutomaticCacheCandidate> { it.retention.ordinal }
        .thenBy(AutomaticCacheCandidate::recencyEpochMillis)
        .thenBy(AutomaticCacheCandidate::stableId),
)

internal fun AutomaticCacheRetention.isNormalQuotaVictim(): Boolean =
    ordinal <= AutomaticCacheRetention.CONSUMED_IMAGE_HISTORY.ordinal

internal fun AutomaticCacheRetention.isEmergencyPressureVictim(): Boolean =
    ordinal <= AutomaticCacheRetention.ACTIVE_INTERACTIVE_IMAGE.ordinal

internal fun AutomaticCacheRetention.isPhysicalPressureVictim(): Boolean =
    ordinal <= AutomaticCacheRetention.CONSUMED_IMAGE_HISTORY.ordinal

private fun imageRetention(
    metadata: ReaderAssetMetadata,
    activeProtections: ReaderAssetActiveProtections,
): AutomaticCacheRetention = when (activeProtections.byKey[metadata.logicalAssetKeyHash]) {
    ReaderAssetProtectionClass.ACTIVE_INTERACTIVE -> AutomaticCacheRetention.ACTIVE_INTERACTIVE_IMAGE
    ReaderAssetProtectionClass.ACTIVE_CONSUMED -> AutomaticCacheRetention.ACTIVE_CONSUMED_IMAGE
    ReaderAssetProtectionClass.RECENT_HISTORY_1 -> AutomaticCacheRetention.RECENT_IMAGE_HISTORY_1
    ReaderAssetProtectionClass.RECENT_HISTORY_2 -> AutomaticCacheRetention.RECENT_IMAGE_HISTORY_2
    ReaderAssetProtectionClass.CURRENT_AHEAD_SPECULATIVE ->
        AutomaticCacheRetention.CURRENT_AHEAD_SPECULATIVE_IMAGE
    ReaderAssetProtectionClass.TRANSITION_SPECULATIVE ->
        AutomaticCacheRetention.TRANSITION_SPECULATIVE_IMAGE
    null -> when {
        metadata.lastConsumedAtEpochMillis != null -> AutomaticCacheRetention.CONSUMED_IMAGE_HISTORY
        metadata.lastAccessedAtEpochMillis == metadata.createdAtEpochMillis ->
            AutomaticCacheRetention.COLD_SPECULATIVE_IMAGE
        else -> AutomaticCacheRetention.WARM_SPECULATIVE_IMAGE
    }
}
