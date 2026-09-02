package app.openstory.downloads.assets

import app.openstory.common.Clock
import app.openstory.common.MonotonicClock
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.cache.AutomaticCacheBudgetCoordinator
import app.openstory.downloads.cache.AutomaticCachePublicationResult
import app.openstory.downloads.cache.AutomaticCacheReservation
import app.openstory.downloads.cache.AutomaticCacheRuntimePolicy
import app.openstory.downloads.cache.AutomaticCacheWriteAuthority
import app.openstory.downloads.reconcile.StorageWriteAdmission
import app.openstory.reader.assets.ReaderAssetActiveProtections
import app.openstory.reader.assets.ReaderAssetCachePressure
import app.openstory.reader.assets.ReaderAssetClearScope
import app.openstory.reader.assets.ReaderAssetCommitFacts
import app.openstory.reader.assets.ReaderAssetCommitResult
import app.openstory.reader.assets.ReaderAssetDurableWriteAuthority
import app.openstory.reader.assets.ReaderAssetFailure
import app.openstory.reader.assets.ReaderAssetInvalidationReason
import app.openstory.reader.assets.ReaderAssetKeyHash
import app.openstory.reader.assets.ReaderAssetLocalPresence
import app.openstory.reader.assets.ReaderAssetOpenResult
import app.openstory.reader.assets.ReaderAssetPayload
import app.openstory.reader.assets.ReaderAssetReadLease
import app.openstory.reader.assets.ReaderAssetRuntimePolicy
import app.openstory.reader.assets.ReaderAssetStorePort
import app.openstory.reader.assets.ReaderPageAssetKey
import app.openstory.reader.assets.isSupportedSchema
import app.openstory.reader.routing.ReaderSessionId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DownloadReaderAssetStore(
    private val metadataRepository: ReaderAssetMetadataRepository,
    private val blobStore: ReaderAssetBlobStore,
    private val blobIdFactory: ReaderAssetBlobIdFactory,
    private val budget: AutomaticCacheBudgetCoordinator,
    private val clock: Clock,
    private val monotonicClock: MonotonicClock,
    private val writeAdmission: StorageWriteAdmission = StorageWriteAdmission.ALLOW_ALL,
    private val runtimePolicy: AutomaticCacheRuntimePolicy = AutomaticCacheRuntimePolicy(),
) : ReaderAssetStorePort {
    private val touchGate = Mutex()
    private val lastAccessTouches = mutableMapOf<ReaderAssetKeyHash, Long>()

    override suspend fun inspect(
        keys: Set<ReaderPageAssetKey>,
    ): Map<ReaderPageAssetKey, ReaderAssetLocalPresence> = when {
        keys.isEmpty() -> emptyMap()
        keys.none(ReaderPageAssetKey::isSupportedSchema) ->
            keys.associateWith { ReaderAssetLocalPresence.LOCAL_MISSING }
        else -> inspectSupported(keys)
    }

    override suspend fun openLocal(key: ReaderPageAssetKey): ReaderAssetOpenResult =
        if (!key.isSupportedSchema()) {
            ReaderAssetOpenResult.Missing
        } else {
            when (val lookup = storageCall { metadataRepository.find(setOf(key.hash))[key.hash] }) {
                is StorageCall.Failed -> ReaderAssetOpenResult.Unavailable
                is StorageCall.Value -> lookup.value?.let { openMetadata(it) } ?: ReaderAssetOpenResult.Missing
            }
        }

    override suspend fun captureDurableWriteAuthority(
        facts: ReaderAssetCommitFacts,
    ): ReaderAssetDurableWriteAuthority? {
        val scope = facts.takeIf { it.key.isSupportedSchema() }
            ?.let(ReaderAssetEvictionMapper::writeScope)
        return scope?.let { budget.captureWriteAuthority(it) }?.let { authority ->
            DownloadReaderAssetDurableWriteAuthority(
                logicalKeyHash = facts.key.hash,
                delegate = authority,
            )
        }
    }

    override suspend fun commit(
        facts: ReaderAssetCommitFacts,
        authority: ReaderAssetDurableWriteAuthority,
        payload: ReaderAssetPayload,
    ): ReaderAssetCommitResult = prepareCommit(facts, authority, payload)?.let { prepared ->
        commitPrepared(facts, payload, prepared)
    } ?: ReaderAssetCommitResult.Bypassed

    override suspend fun markConsumed(key: ReaderPageAssetKey) {
        if (key.isSupportedSchema()) {
            storageCall { metadataRepository.updateLastConsumed(key.hash, clock.nowEpochMillis()) }
        }
    }

    override suspend fun invalidate(key: ReaderPageAssetKey, reason: ReaderAssetInvalidationReason) {
        if (!key.isSupportedSchema()) return
        val lookup = storageCall { metadataRepository.find(setOf(key.hash))[key.hash] }
        if (lookup is StorageCall.Value && lookup.value != null) {
            budget.invalidateReaderAssetGeneration(lookup.value)
        }
    }

    override suspend fun cachePressure(): ReaderAssetCachePressure = when {
        !writeAdmission.canStore(MINIMUM_ADMISSION_PROBE_BYTES) -> ReaderAssetCachePressure.EMERGENCY
        else -> when (val snapshot = storageCall { budget.snapshot() }) {
            is StorageCall.Failed -> ReaderAssetCachePressure.EMERGENCY
            is StorageCall.Value -> {
                val highWatermark = snapshot.value.quotaBytes
                    .basisPoints(runtimePolicy.highWatermarkBasisPoints)
                if (snapshot.value.quotaBytes == 0L || snapshot.value.totalAccountedBytes >= highWatermark) {
                    ReaderAssetCachePressure.PRESSURED
                } else {
                    ReaderAssetCachePressure.NORMAL
                }
            }
        }
    }

    override suspend fun reconcile(activeProtections: ReaderAssetActiveProtections) {
        budget.replaceActiveProtections(activeProtections)
        budget.requestReconciliation()
    }

    override suspend fun releaseSession(sessionId: ReaderSessionId) {
        budget.requestReconciliation()
    }

    override suspend fun clearAutomatic(scope: ReaderAssetClearScope) {
        budget.clearAutomatic(ReaderAssetEvictionMapper.invalidationScope(scope))
    }

    private suspend fun inspectSupported(
        keys: Set<ReaderPageAssetKey>,
    ): Map<ReaderPageAssetKey, ReaderAssetLocalPresence> {
        val supportedHashes = keys.asSequence()
            .filter(ReaderPageAssetKey::isSupportedSchema)
            .mapTo(mutableSetOf(), ReaderPageAssetKey::hash)
        return when (val lookup = storageCall { metadataRepository.find(supportedHashes) }) {
            is StorageCall.Failed -> keys.associateWith { ReaderAssetLocalPresence.LOCAL_UNAVAILABLE }
            is StorageCall.Value -> keys.associateWith { key -> inspectOne(key, lookup.value[key.hash]) }
        }
    }

    private suspend fun inspectOne(
        key: ReaderPageAssetKey,
        metadata: ReaderAssetMetadata?,
    ): ReaderAssetLocalPresence = when {
        !key.isSupportedSchema() || metadata == null -> ReaderAssetLocalPresence.LOCAL_MISSING
        else -> when (val exists = storageCall { blobStore.exists(ReaderAssetBlobId(metadata.blobId)) }) {
            is StorageCall.Failed -> ReaderAssetLocalPresence.LOCAL_UNAVAILABLE
            is StorageCall.Value -> if (exists.value) {
                ReaderAssetLocalPresence.LOCAL_AVAILABLE
            } else {
                repairMissingMetadata(metadata)
            }
        }
    }

    private suspend fun repairMissingMetadata(metadata: ReaderAssetMetadata): ReaderAssetLocalPresence =
        when (storageCall { budget.invalidateReaderAssetGeneration(metadata) }) {
            is StorageCall.Failed -> ReaderAssetLocalPresence.LOCAL_UNAVAILABLE
            is StorageCall.Value -> ReaderAssetLocalPresence.LOCAL_MISSING
        }

    private suspend fun openMetadata(metadata: ReaderAssetMetadata): ReaderAssetOpenResult =
        when (val opened = storageCall { blobStore.open(ReaderAssetBlobId(metadata.blobId)) }) {
            is StorageCall.Failed -> ReaderAssetOpenResult.Unavailable
            is StorageCall.Value -> opened.value?.let { lease ->
                openVerified(metadata, lease)
            } ?: repairMissingOpen(metadata)
        }

    private suspend fun repairMissingOpen(metadata: ReaderAssetMetadata): ReaderAssetOpenResult =
        when (storageCall { budget.invalidateReaderAssetGeneration(metadata) }) {
            is StorageCall.Failed -> ReaderAssetOpenResult.Unavailable
            is StorageCall.Value -> ReaderAssetOpenResult.Missing
        }

    private suspend fun openVerified(
        metadata: ReaderAssetMetadata,
        physicalLease: ReaderAssetBlobReadLease,
    ): ReaderAssetOpenResult = when (val verification = verifyReaderAssetLease(physicalLease, metadata)) {
        is ReaderAssetVerification.Verified -> {
            touchAccessBestEffort(metadata.logicalAssetKeyHash)
            ReaderAssetOpenResult.Available(
                VerifiedReaderAssetReadLease(verification.bytes, physicalLease),
            )
        }
        ReaderAssetVerification.Corrupt -> {
            physicalLease.closeBestEffort()
            when (storageCall { budget.invalidateReaderAssetGeneration(metadata) }) {
                is StorageCall.Failed -> ReaderAssetOpenResult.Unavailable
                is StorageCall.Value -> ReaderAssetOpenResult.Corrupt
            }
        }
        ReaderAssetVerification.Unavailable -> {
            physicalLease.closeBestEffort()
            ReaderAssetOpenResult.Unavailable
        }
    }

    private suspend fun prepareCommit(
        facts: ReaderAssetCommitFacts,
        authority: ReaderAssetDurableWriteAuthority,
        payload: ReaderAssetPayload,
    ): PreparedReaderAssetCommit? {
        val durableAuthority = authority as? DownloadReaderAssetDurableWriteAuthority
        val expectedScope = ReaderAssetEvictionMapper.writeScope(facts)
        val bytes = payload.bytes()
        requireReaderAssetBlobPayloadSize(bytes)
        val validAuthority = durableAuthority != null &&
            durableAuthority.logicalKeyHash == facts.key.hash &&
            durableAuthority.delegate.scope == expectedScope
        return if (!validAuthority || !writeAdmission.canStore(bytes.size.toLong())) {
            null
        } else {
            budget.reserve(bytes.size.toLong(), durableAuthority.delegate)?.let { reservation ->
                PreparedReaderAssetCommit(
                    authority = durableAuthority.delegate,
                    reservation = reservation,
                    blobId = blobIdFactory.create(facts.key.hash),
                    bytes = bytes,
                )
            }
        }
    }

    private suspend fun commitPrepared(
        facts: ReaderAssetCommitFacts,
        payload: ReaderAssetPayload,
        prepared: PreparedReaderAssetCommit,
    ): ReaderAssetCommitResult = try {
        when (val write = writeWithOneRetry(prepared.blobId, prepared.bytes)) {
            is ReaderAssetBlobWriteResult.Stored -> commitStored(facts, payload, prepared, write.blob)
            ReaderAssetBlobWriteResult.NoSpace,
            is ReaderAssetBlobWriteResult.Unavailable,
            -> cleanupGeneration(prepared.blobId, ReaderAssetCommitResult.Degraded(cacheStorageFailure))
        }
    } finally {
        budget.release(prepared.reservation)
    }

    private suspend fun writeWithOneRetry(
        blobId: ReaderAssetBlobId,
        bytes: ByteArray,
    ): ReaderAssetBlobWriteResult = when (val firstWrite = writeBlob(blobId, bytes)) {
        is ReaderAssetBlobWriteResult.Stored -> firstWrite
        is ReaderAssetBlobWriteResult.Unavailable -> firstWrite
        ReaderAssetBlobWriteResult.NoSpace -> when (
            storageCall { budget.relievePhysicalPressure(bytes.size.toLong()) }
        ) {
            is StorageCall.Failed -> ReaderAssetBlobWriteResult.Unavailable(cacheStorageFailureCause)
            is StorageCall.Value -> writeBlob(blobId, bytes)
        }
    }

    private suspend fun writeBlob(blobId: ReaderAssetBlobId, bytes: ByteArray): ReaderAssetBlobWriteResult =
        when (val write = storageCall { blobStore.writeAtomic(blobId, bytes) }) {
            is StorageCall.Failed -> ReaderAssetBlobWriteResult.Unavailable(write.cause)
            is StorageCall.Value -> write.value
        }

    private suspend fun commitStored(
        facts: ReaderAssetCommitFacts,
        payload: ReaderAssetPayload,
        prepared: PreparedReaderAssetCommit,
        stored: StoredReaderAssetBlob,
    ): ReaderAssetCommitResult = if (!stored.matches(prepared.blobId, prepared.bytes)) {
        cleanupGeneration(prepared.blobId, ReaderAssetCommitResult.Degraded(cacheStorageFailure))
    } else {
        publishStored(facts, payload, prepared, stored)
    }

    private suspend fun publishStored(
        facts: ReaderAssetCommitFacts,
        payload: ReaderAssetPayload,
        prepared: PreparedReaderAssetCommit,
        stored: StoredReaderAssetBlob,
    ): ReaderAssetCommitResult {
        val publication = storageCall {
            val metadata = readerAssetMetadata(facts, payload, stored, clock.nowEpochMillis())
            budget.publishIfCurrent(
                authority = prepared.authority,
                reservation = prepared.reservation,
                replacedCommittedBytes = { previous: ReaderAssetMetadata? -> previous?.byteSize ?: 0L },
            ) {
                val previous = metadataRepository.find(setOf(facts.key.hash))[facts.key.hash]
                metadataRepository.upsert(metadata)
                previous
            }
        }
        return when (publication) {
            is StorageCall.Failed -> cleanupGeneration(
                prepared.blobId,
                ReaderAssetCommitResult.Degraded(cacheStorageFailure),
            )
            is StorageCall.Value -> publicationResult(prepared.blobId, publication.value)
        }
    }

    private fun publicationResult(
        blobId: ReaderAssetBlobId,
        publication: AutomaticCachePublicationResult<ReaderAssetMetadata?>,
    ): ReaderAssetCommitResult = when (publication) {
        is AutomaticCachePublicationResult.Published -> {
            publication.value
                ?.takeIf { it.blobId != blobId.value }
                ?.let { budget.requestReaderAssetGenerationDeletion(ReaderAssetBlobId(it.blobId)) }
            ReaderAssetCommitResult.Persisted
        }
        AutomaticCachePublicationResult.Revoked ->
            cleanupGeneration(blobId, ReaderAssetCommitResult.Bypassed)
    }

    private suspend fun touchAccessBestEffort(key: ReaderAssetKeyHash) {
        val nowNanos = monotonicClock.nowNanos()
        val intervalNanos = runtimePolicy.assetAccessTouchIntervalMillis.toNanosSaturated()
        val due = touchGate.withLock {
            val previous = lastAccessTouches[key]
            val elapsed = previous?.let { nowNanos - it }
            val shouldTouch = previous == null || elapsed == null || elapsed < 0L || elapsed >= intervalNanos
            if (shouldTouch) lastAccessTouches[key] = nowNanos
            shouldTouch
        }
        if (due) {
            val touched = storageCall { metadataRepository.updateLastAccessed(key, clock.nowEpochMillis()) }
            if (touched is StorageCall.Failed) {
                touchGate.withLock {
                    if (lastAccessTouches[key] == nowNanos) lastAccessTouches.remove(key)
                }
            }
        }
    }

    private fun cleanupGeneration(
        blobId: ReaderAssetBlobId,
        result: ReaderAssetCommitResult,
    ): ReaderAssetCommitResult {
        budget.requestReaderAssetGenerationDeletion(blobId)
        return result
    }
}

private data class DownloadReaderAssetDurableWriteAuthority(
    val logicalKeyHash: ReaderAssetKeyHash,
    val delegate: AutomaticCacheWriteAuthority,
) : ReaderAssetDurableWriteAuthority

private data class PreparedReaderAssetCommit(
    val authority: AutomaticCacheWriteAuthority,
    val reservation: AutomaticCacheReservation,
    val blobId: ReaderAssetBlobId,
    val bytes: ByteArray,
)

private sealed interface StorageCall<out T> {
    data class Value<T>(val value: T) : StorageCall<T>
    data class Failed(val cause: Throwable) : StorageCall<Nothing>
}

@Suppress("TooGenericExceptionCaught")
private suspend inline fun <T> storageCall(block: suspend () -> T): StorageCall<T> = try {
    StorageCall.Value(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    StorageCall.Failed(failure)
}

private sealed interface ReaderAssetVerification {
    data class Verified(val bytes: ByteArray) : ReaderAssetVerification
    data object Corrupt : ReaderAssetVerification
    data object Unavailable : ReaderAssetVerification
}

private fun verifyReaderAssetLease(
    physicalLease: ReaderAssetBlobReadLease,
    metadata: ReaderAssetMetadata,
): ReaderAssetVerification = try {
    if (physicalLease.sizeBytes != metadata.byteSize ||
        metadata.byteSize !in 1L..ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES.toLong()
    ) {
        ReaderAssetVerification.Corrupt
    } else {
        val bytes = physicalLease.openStream().use(::readBounded)
        if (bytes.size.toLong() == metadata.byteSize && BlobChecksum.sha256(bytes) == metadata.localBlobChecksum) {
            ReaderAssetVerification.Verified(bytes)
        } else {
            ReaderAssetVerification.Corrupt
        }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: ReaderAssetCorruptionException) {
    ReaderAssetVerification.Corrupt
} catch (_: Exception) {
    ReaderAssetVerification.Unavailable
}

private fun readBounded(stream: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(READ_BUFFER_BYTES)
    var total = 0
    while (total <= ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES) {
        val read = stream.read(buffer)
        if (read < 0) return output.toByteArray()
        if (read > 0) {
            total += read
            if (total > ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES) throw ReaderAssetCorruptionException()
            output.write(buffer, 0, read)
        }
    }
    throw ReaderAssetCorruptionException()
}

private fun readerAssetMetadata(
    facts: ReaderAssetCommitFacts,
    payload: ReaderAssetPayload,
    stored: StoredReaderAssetBlob,
    nowEpochMillis: Long,
) = ReaderAssetMetadata(
    logicalAssetKeyHash = facts.key.hash,
    keySchemaVersion = facts.key.schemaVersion,
    storyId = facts.storyId,
    canonicalChapterId = facts.canonicalChapterId,
    chapterReleaseId = facts.releaseId,
    sourceNamespace = facts.sourceNamespace,
    securityScopeHash = ReaderAssetEvictionMapper.securityScopeHash(facts.securityScope),
    contentVariant = facts.contentVariant,
    identityMode = facts.identityMode,
    persistenceMode = facts.persistenceMode,
    imageSetNamespaceHash = facts.imageSetNamespace,
    pageIdentityHash = facts.key.pageIdentityHash,
    pageOrdinal = facts.imageOrdinal,
    blobId = stored.id.value,
    byteSize = stored.sizeBytes,
    localBlobChecksum = stored.checksum,
    sourceIntegrityHash = payload.sourceIntegrityHash,
    createdAtEpochMillis = nowEpochMillis,
    lastAccessedAtEpochMillis = nowEpochMillis,
    lastConsumedAtEpochMillis = null,
)

private fun StoredReaderAssetBlob.matches(blobId: ReaderAssetBlobId, bytes: ByteArray): Boolean =
    id == blobId && sizeBytes == bytes.size.toLong() && checksum == BlobChecksum.sha256(bytes)

private class VerifiedReaderAssetReadLease(
    private val bytes: ByteArray,
    private val physicalLease: ReaderAssetBlobReadLease,
) : ReaderAssetReadLease {
    private val closed = AtomicBoolean(false)

    override val sizeBytes: Long = bytes.size.toLong()

    override fun openStream(): InputStream {
        check(!closed.get()) { "Reader asset lease is closed." }
        return ByteArrayInputStream(bytes)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) physicalLease.close()
    }
}

private fun ReaderAssetBlobReadLease.closeBestEffort() {
    runCatching(::close)
}

private fun Long.basisPoints(basisPoints: Int): Long =
    (this / BASIS_POINTS) * basisPoints + (this % BASIS_POINTS) * basisPoints / BASIS_POINTS

private fun Long.toNanosSaturated(): Long =
    if (this > Long.MAX_VALUE / NANOS_PER_MILLISECOND) Long.MAX_VALUE else this * NANOS_PER_MILLISECOND

private class ReaderAssetCorruptionException : Exception()

private val cacheStorageFailure = ReaderAssetFailure.CacheStorageUnavailable
private val cacheStorageFailureCause = IllegalStateException("Automatic cache storage is unavailable.")
private const val READ_BUFFER_BYTES = 8 * 1024
private const val MINIMUM_ADMISSION_PROBE_BYTES = 1L
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val BASIS_POINTS = 10_000L
