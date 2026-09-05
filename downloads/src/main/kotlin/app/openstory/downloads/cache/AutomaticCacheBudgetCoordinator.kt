package app.openstory.downloads.cache

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.assets.ReaderAssetBlobId
import app.openstory.downloads.assets.ReaderAssetBlobReadLease
import app.openstory.downloads.assets.ReaderAssetBlobStore
import app.openstory.downloads.assets.ReaderAssetBlobWriteResult
import app.openstory.downloads.assets.ReaderAssetMetadata
import app.openstory.downloads.assets.ReaderAssetMetadataRepository
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.reader.assets.ReaderAssetActiveProtections
import app.openstory.reader.assets.ReaderAssetDiagnosticsSink
import app.openstory.reader.assets.ReaderAssetKeyHash
import app.openstory.reader.assets.ReaderAssetSourceNamespace
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AutomaticCacheBudgetCoordinator(
    private val cacheRepository: CacheRepository,
    private val documentBlobStore: ChapterBlobStore,
    private val readerAssetMetadataRepository: ReaderAssetMetadataRepository,
    private val readerAssetBlobStore: ReaderAssetBlobStore,
    private val policy: AutomaticCacheRuntimePolicy = AutomaticCacheRuntimePolicy(),
    initialQuotaBytes: Long = DEFAULT_AUTOMATIC_CACHE_QUOTA_BYTES,
    private val reconciliationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val diagnostics: ReaderAssetDiagnosticsSink = ReaderAssetDiagnosticsSink.NO_OP,
) {
    private val publicationGate = Mutex()
    private val blobMaintenance = AutomaticCacheBlobMaintenance(documentBlobStore, readerAssetBlobStore)
    private val reconciliationScheduled = AtomicBoolean(false)
    private var quotaBytes = initialQuotaBytes.also {
        require(it >= 0L) { "Automatic cache quota must not be negative." }
    }
    private var globalEpoch = 0L
    private var scopedEpochCounter = 0L
    private val sourceEpochs = mutableMapOf<ReaderAssetSourceNamespace, Long>()
    private val allAccountEpochs = mutableMapOf<ReaderAssetSourceNamespace, Long>()
    private val accountEpochs = mutableMapOf<Pair<ReaderAssetSourceNamespace, String>, Long>()
    private var nextReservationId = 1L
    private val pendingReservations = linkedMapOf<Long, Long>()
    private var initialized = false
    private var committedBytes = 0L
    private var progressProtectedReleaseIds = emptySet<ChapterReleaseId>()
    private var activeProtections = ReaderAssetActiveProtections.EMPTY
    private var activeProtectedOverflowBytes = 0L
    private val pressureMaintenance = AutomaticCachePressureMaintenance(
        scope = reconciliationScope,
        diagnostics = diagnostics,
        maxPhysicalPressureVictims = policy.maxEnospcEvictionVictims,
        physicalCandidates = {
            publicationGate.withLock {
                ensureInitializedLocked()
                candidatesLocked().filter { it.retention.isPhysicalPressureVictim() }
            }
        },
        emergencyCandidates = ::emergencyCandidatesWithoutActiveLeases,
        relievePhysicalCandidate = ::relieveCandidate,
        relieveEmergencyCandidate = { candidate ->
            when (candidate) {
                is AutomaticCacheCandidate.Image -> relieveEmergencyImageCandidate(candidate.metadata)
                is AutomaticCacheCandidate.Document -> relieveCandidate(candidate)
            }
        },
        hasEmergencyVictims = { emergencyCandidatesWithoutActiveLeases().isNotEmpty() },
    )

    suspend fun updateQuota(quotaBytes: Long) {
        require(quotaBytes >= 0L) { "Automatic cache quota must not be negative." }
        val shouldReconcile = publicationGate.withLock {
            ensureInitializedLocked()
            if (this.quotaBytes > 0L && quotaBytes == 0L) {
                globalEpoch = globalEpoch.incrementEpoch()
            }
            this.quotaBytes = quotaBytes
            accountedBytesLocked() > highWatermarkBytesLocked()
        }
        if (shouldReconcile) scheduleReconciliation()
    }

    suspend fun updateProgressProtectedReleaseIds(releaseIds: Set<ChapterReleaseId>) {
        val shouldReconcile = publicationGate.withLock {
            ensureInitializedLocked()
            progressProtectedReleaseIds = releaseIds.toSet()
            accountedBytesLocked() > highWatermarkBytesLocked()
        }
        if (shouldReconcile) scheduleReconciliation()
    }

    suspend fun captureWriteAuthority(
        scope: AutomaticCacheWriteScope = AutomaticCacheWriteScope.GlobalAutomatic,
    ): AutomaticCacheWriteAuthority? = publicationGate.withLock {
        ensureInitializedLocked()
        if (quotaBytes == 0L) return@withLock null
        AutomaticCacheWriteAuthority(
            globalEpoch = globalEpoch,
            scopedEpoch = currentScopedEpochLocked(scope),
            scope = scope,
        )
    }

    suspend fun reserve(
        bytes: Long,
        authority: AutomaticCacheWriteAuthority,
    ): AutomaticCacheReservation? {
        require(bytes >= 0L) { "Automatic cache reservation must not be negative." }
        var shouldReconcile = false
        val reservation = publicationGate.withLock {
            ensureInitializedLocked()
            if (!isCurrentLocked(authority) || quotaBytes == 0L) return@withLock null
            val accounted = accountedBytesLocked()
            if (bytes > quotaBytes || accounted > quotaBytes - bytes) {
                shouldReconcile = accounted >= highWatermarkBytesLocked()
                return@withLock null
            }
            val id = nextReservationId
            nextReservationId = nextReservationId.incrementEpoch()
            pendingReservations[id] = bytes
            AutomaticCacheReservation(id, bytes)
        }
        if (shouldReconcile) scheduleReconciliation()
        return reservation
    }

    suspend fun <T> publishIfCurrent(
        authority: AutomaticCacheWriteAuthority,
        reservation: AutomaticCacheReservation,
        replacedCommittedBytes: (T) -> Long = { 0L },
        publishMetadata: suspend () -> T,
    ): AutomaticCachePublicationResult<T> {
        var reservationConsumed = false
        var shouldReconcile = false
        return try {
            val result = publicationGate.withLock {
                ensureInitializedLocked()
                val reservedBytes = pendingReservations.remove(reservation.id)
                    ?: return@withLock AutomaticCachePublicationResult.Revoked
                reservationConsumed = true
                if (reservedBytes != reservation.bytes || !isCurrentLocked(authority) || quotaBytes == 0L) {
                    return@withLock AutomaticCachePublicationResult.Revoked
                }
                val value = publishMetadata()
                val replacedBytes = replacedCommittedBytes(value)
                require(replacedBytes >= 0L) { "Replaced automatic cache bytes must not be negative." }
                committedBytes = (committedBytes - replacedBytes)
                    .coerceAtLeast(0L)
                    .saturatedAdd(reservedBytes)
                shouldReconcile = committedBytes > highWatermarkBytesLocked()
                AutomaticCachePublicationResult.Published(value)
            }
            if (shouldReconcile) scheduleReconciliation()
            result
        } finally {
            if (!reservationConsumed) release(reservation)
        }
    }

    suspend fun release(reservation: AutomaticCacheReservation) {
        publicationGate.withLock {
            pendingReservations.remove(reservation.id)
        }
    }

    internal suspend fun replaceActiveProtections(activeAssetProtections: ReaderAssetActiveProtections) {
        publicationGate.withLock {
            ensureInitializedLocked()
            activeProtections = activeAssetProtections
        }
    }

    internal fun requestReconciliation() {
        scheduleReconciliation()
    }

    internal fun requestReaderAssetGenerationDeletion(blobId: ReaderAssetBlobId) {
        reconciliationScope.launch { blobMaintenance.deleteImageBlobWhenUnleasedBestEffort(blobId) }
    }

    internal suspend fun invalidateReaderAssetGeneration(expected: ReaderAssetMetadata): Boolean {
        val detached = publicationGate.withLock {
            ensureInitializedLocked()
            detachImageIfCurrentLocked(expected)?.also { metadata ->
                committedBytes = (committedBytes - metadata.byteSize).coerceAtLeast(0L)
                activeProtectedOverflowBytes = (committedBytes - quotaBytes).coerceAtLeast(0L)
            }
        }
        // The expected generation is safe to delete even if a newer generation won publication.
        reconciliationScope.launch { blobMaintenance.deleteImageWhenUnleasedBestEffort(detached ?: expected) }
        return detached != null
    }

    suspend fun reconcile(
        activeAssetProtections: ReaderAssetActiveProtections = ReaderAssetActiveProtections.EMPTY,
    ) {
        val detachedImages = mutableListOf<ReaderAssetMetadata>()
        publicationGate.withLock {
            ensureInitializedLocked()
            recomputeCommittedBytesLocked()
            activeProtections = activeAssetProtections
            val targetBytes = lowWatermarkBytesLocked()
            if (committedBytes < highWatermarkBytesLocked()) {
                activeProtectedOverflowBytes = 0L
                return@withLock
            }
            val candidates = candidatesLocked()
            for (candidate in candidates.take(MAX_NORMAL_RECONCILIATION_VICTIMS)) {
                if (committedBytes <= targetBytes || !candidate.retention.isNormalQuotaVictim()) break
                evictQuotaCandidateLocked(candidate, detachedImages)
            }
            activeProtectedOverflowBytes = (committedBytes - quotaBytes).coerceAtLeast(0L)
        }
        detachedImages.forEach { metadata -> blobMaintenance.deleteImageWhenUnleasedBestEffort(metadata) }
    }

    suspend fun relievePhysicalPressure(requiredBytes: Long): Long =
        pressureMaintenance.relievePhysicalPressure(requiredBytes)

    internal suspend fun relieveEmergencyPressure(
        reserveRestored: () -> Boolean,
    ): AutomaticCacheEmergencyReliefReport = pressureMaintenance.relieveEmergencyPressure(reserveRestored)

    internal fun requestEmergencyReconciliation(reserveRestored: () -> Boolean) {
        pressureMaintenance.requestEmergencyReconciliation(reserveRestored)
    }

    suspend fun clearAutomatic(scope: AutomaticCacheInvalidationScope) {
        val detachedImages = publicationGate.withLock {
            ensureInitializedLocked()
            advanceInvalidationEpochLocked(scope)
            val documents = if (scope == AutomaticCacheInvalidationScope.AllAutomatic) {
                cacheRepository.detachAllAutomatic()
            } else {
                emptyList()
            }
            documents.forEach { entry -> blobMaintenance.deleteDocumentBestEffort(entry.key) }
            val images = when (scope) {
                AutomaticCacheInvalidationScope.AllAutomatic -> readerAssetMetadataRepository.detachAll()
                is AutomaticCacheInvalidationScope.ReaderAssetSource ->
                    readerAssetMetadataRepository.detachSource(scope.sourceNamespace)
                is AutomaticCacheInvalidationScope.ReaderAssetAccount ->
                    readerAssetMetadataRepository.detachAccount(scope.sourceNamespace, scope.securityScopeHash)
                is AutomaticCacheInvalidationScope.AllReaderAssetAccountsForSource ->
                    readerAssetMetadataRepository.detachAllAccountsForSource(scope.sourceNamespace)
            }
            val removedBytes = documents.sumOf(CacheEntry::sizeBytes)
                .saturatedAdd(images.sumOf(ReaderAssetMetadata::byteSize))
            committedBytes = (committedBytes - removedBytes).coerceAtLeast(0L)
            activeProtectedOverflowBytes = (committedBytes - quotaBytes).coerceAtLeast(0L)
            images
        }
        detachedImages.forEach { metadata -> blobMaintenance.deleteImageWhenUnleasedBestEffort(metadata) }
    }

    suspend fun snapshot(): AutomaticCacheBudgetSnapshot = publicationGate.withLock {
        ensureInitializedLocked()
        recomputeCommittedBytesLocked()
        AutomaticCacheBudgetSnapshot(
            quotaBytes = quotaBytes,
            committedBytes = committedBytes,
            pendingReservationBytes = pendingReservations.values.fold(0L, Long::saturatedAdd),
            activeProtectedOverflowBytes = activeProtectedOverflowBytes,
        )
    }

    private suspend fun ensureInitializedLocked() {
        if (initialized) return
        recomputeCommittedBytesLocked()
        initialized = true
    }

    private suspend fun recomputeCommittedBytesLocked() {
        committedBytes = cacheRepository.automaticUsageBytes()
            .saturatedAdd(readerAssetMetadataRepository.usageBytes())
    }

    private suspend fun emergencyCandidatesWithoutActiveLeases(): List<AutomaticCacheCandidate> {
        val candidates = publicationGate.withLock {
            ensureInitializedLocked()
            candidatesLocked().filter { it.retention.isEmergencyPressureVictim() }
        }
        return candidates.filterNot { candidate ->
            candidate is AutomaticCacheCandidate.Image && blobMaintenance.hasActiveReadLease(candidate.metadata)
        }
    }

    private suspend fun candidatesLocked(): List<AutomaticCacheCandidate> =
        normalizedAutomaticCacheCandidates(
            documents = automaticDocumentsLocked(),
            images = readerAssetMetadataRepository.all(),
            progressProtectedReleaseIds = progressProtectedReleaseIds,
            activeProtections = activeProtections,
        )

    private suspend fun automaticDocumentsLocked(): List<CacheEntry> =
        cacheRepository.entries().filter { it.key.namespace == ChapterBlobNamespace.AUTOMATIC_CACHE }

    private fun accountedBytesLocked(): Long = committedBytes.saturatedAdd(
        pendingReservations.values.fold(0L, Long::saturatedAdd),
    )

    private fun highWatermarkBytesLocked(): Long = quotaBytes.basisPoints(policy.highWatermarkBasisPoints)

    private fun lowWatermarkBytesLocked(): Long = quotaBytes.basisPoints(policy.lowWatermarkBasisPoints)

    private fun currentScopedEpochLocked(scope: AutomaticCacheWriteScope): Long = when (scope) {
        AutomaticCacheWriteScope.GlobalAutomatic -> 0L
        is AutomaticCacheWriteScope.ReaderAssetSource -> sourceEpochs[scope.sourceNamespace] ?: 0L
        is AutomaticCacheWriteScope.ReaderAssetAccount -> maxOf(
            sourceEpochs[scope.sourceNamespace] ?: 0L,
            allAccountEpochs[scope.sourceNamespace] ?: 0L,
            accountEpochs[scope.sourceNamespace to scope.securityScopeHash] ?: 0L,
        )
    }

    private fun isCurrentLocked(authority: AutomaticCacheWriteAuthority): Boolean =
        authority.globalEpoch == globalEpoch && authority.scopedEpoch == currentScopedEpochLocked(authority.scope)

    private fun advanceInvalidationEpochLocked(scope: AutomaticCacheInvalidationScope) {
        when (scope) {
            AutomaticCacheInvalidationScope.AllAutomatic -> globalEpoch = globalEpoch.incrementEpoch()
            is AutomaticCacheInvalidationScope.ReaderAssetSource -> {
                val epoch = nextScopedEpochLocked()
                sourceEpochs[scope.sourceNamespace] = epoch
            }
            is AutomaticCacheInvalidationScope.ReaderAssetAccount -> {
                val epoch = nextScopedEpochLocked()
                accountEpochs[scope.sourceNamespace to scope.securityScopeHash] = epoch
            }
            is AutomaticCacheInvalidationScope.AllReaderAssetAccountsForSource -> {
                val epoch = nextScopedEpochLocked()
                allAccountEpochs[scope.sourceNamespace] = epoch
            }
        }
    }

    private fun nextScopedEpochLocked(): Long {
        scopedEpochCounter = scopedEpochCounter.incrementEpoch()
        return scopedEpochCounter
    }

    private suspend fun detachDocumentIfCurrentLocked(expected: CacheEntry): CacheEntry? {
        val current = automaticDocumentsLocked().firstOrNull { it.key == expected.key }
        return if (current?.checksum == expected.checksum && current.sizeBytes == expected.sizeBytes) {
            cacheRepository.detachAutomatic(expected.key)
        } else {
            null
        }
    }

    private suspend fun detachImageIfCurrentLocked(expected: ReaderAssetMetadata): ReaderAssetMetadata? {
        val current = readerAssetMetadataRepository.find(setOf(expected.logicalAssetKeyHash))
            .get(expected.logicalAssetKeyHash)
        return if (current?.blobId == expected.blobId) {
            readerAssetMetadataRepository.detach(expected.logicalAssetKeyHash)
        } else {
            null
        }
    }

    private suspend fun evictQuotaCandidateLocked(
        candidate: AutomaticCacheCandidate,
        detachedImages: MutableList<ReaderAssetMetadata>,
    ) {
        when (candidate) {
            is AutomaticCacheCandidate.Document -> detachDocumentIfCurrentLocked(candidate.entry)?.let { detached ->
                blobMaintenance.deleteDocumentBestEffort(detached.key)
                committedBytes = (committedBytes - detached.sizeBytes).coerceAtLeast(0L)
            }
            is AutomaticCacheCandidate.Image -> detachImageIfCurrentLocked(candidate.metadata)?.let { detached ->
                detachedImages += detached
                committedBytes = (committedBytes - detached.byteSize).coerceAtLeast(0L)
            }
        }
    }

    private suspend fun relieveCandidate(candidate: AutomaticCacheCandidate): AutomaticCachePhysicalRelief = when (candidate) {
        is AutomaticCacheCandidate.Document -> publicationGate.withLock {
            detachDocumentIfCurrentLocked(candidate.entry)?.let { detached ->
                val deleted = blobMaintenance.deleteDocumentBestEffort(detached.key)
                committedBytes = (committedBytes - detached.sizeBytes).coerceAtLeast(0L)
                AutomaticCachePhysicalRelief(
                    madeProgress = true,
                    physicallyReclaimedBytes = detached.sizeBytes.takeIf { deleted } ?: 0L,
                )
            } ?: AutomaticCachePhysicalRelief.NONE
        }
        is AutomaticCacheCandidate.Image -> relieveImageCandidate(candidate.metadata)
    }

    private suspend fun relieveImageCandidate(expected: ReaderAssetMetadata): AutomaticCachePhysicalRelief {
        val detached = publicationGate.withLock {
            detachImageIfCurrentLocked(expected)?.also { metadata ->
                committedBytes = (committedBytes - metadata.byteSize).coerceAtLeast(0L)
            }
        } ?: return AutomaticCachePhysicalRelief.NONE
        val deletedNow = blobMaintenance.tryDeleteImageNowIfUnleased(detached)
        if (!deletedNow) {
            reconciliationScope.launch { blobMaintenance.deleteImageWhenUnleasedBestEffort(detached) }
        }
        return AutomaticCachePhysicalRelief(
            madeProgress = true,
            physicallyReclaimedBytes = detached.byteSize.takeIf { deletedNow } ?: 0L,
        )
    }

    private suspend fun relieveEmergencyImageCandidate(expected: ReaderAssetMetadata): AutomaticCachePhysicalRelief {
        if (!blobMaintenance.tryDeleteImageNowIfUnleased(expected)) return AutomaticCachePhysicalRelief.NONE
        val detached = publicationGate.withLock {
            detachImageIfCurrentLocked(expected)?.also { metadata ->
                committedBytes = (committedBytes - metadata.byteSize).coerceAtLeast(0L)
            }
        }
        return AutomaticCachePhysicalRelief(
            madeProgress = true,
            physicallyReclaimedBytes = expected.byteSize,
        ).also {
            if (detached == null) {
                // A newer generation won publication after the candidate snapshot. The deleted
                // generation is now an orphan, so only physical accounting changes.
            }
        }
    }

    private fun scheduleReconciliation() {
        if (!reconciliationScheduled.compareAndSet(false, true)) return
        reconciliationScope.launch {
            var needsAnotherPass = false
            try {
                reconcile(activeProtections)
                needsAnotherPass = shouldContinueReconciliation()
            } finally {
                reconciliationScheduled.set(false)
                if (needsAnotherPass) scheduleReconciliation()
            }
        }
    }

    private suspend fun shouldContinueReconciliation(): Boolean = publicationGate.withLock {
        committedBytes > highWatermarkBytesLocked() &&
            candidatesLocked().any { it.retention.isNormalQuotaVictim() }
    }

    companion object {
        internal fun documentsOnly(
            cacheRepository: CacheRepository,
            documentBlobStore: ChapterBlobStore,
            initialQuotaBytes: Long = DEFAULT_AUTOMATIC_CACHE_QUOTA_BYTES,
            reconciliationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ): AutomaticCacheBudgetCoordinator = AutomaticCacheBudgetCoordinator(
            cacheRepository = cacheRepository,
            documentBlobStore = documentBlobStore,
            readerAssetMetadataRepository = EmptyReaderAssetMetadataRepository,
            readerAssetBlobStore = EmptyReaderAssetBlobStore,
            initialQuotaBytes = initialQuotaBytes,
            reconciliationScope = reconciliationScope,
        )
    }
}

private object EmptyReaderAssetMetadataRepository : ReaderAssetMetadataRepository {
    override suspend fun upsert(metadata: ReaderAssetMetadata) = Unit
    override suspend fun find(keys: Set<ReaderAssetKeyHash>) = emptyMap<ReaderAssetKeyHash, ReaderAssetMetadata>()
    override suspend fun all() = emptyList<ReaderAssetMetadata>()
    override suspend fun usageBytes() = 0L
    override suspend fun detach(key: ReaderAssetKeyHash): ReaderAssetMetadata? = null
    override suspend fun detachAll() = emptyList<ReaderAssetMetadata>()
    override suspend fun detachSource(sourceNamespace: ReaderAssetSourceNamespace) = emptyList<ReaderAssetMetadata>()
    override suspend fun detachAccount(sourceNamespace: ReaderAssetSourceNamespace, securityScopeHash: String) =
        emptyList<ReaderAssetMetadata>()
    override suspend fun detachAllAccountsForSource(sourceNamespace: ReaderAssetSourceNamespace) =
        emptyList<ReaderAssetMetadata>()
    override suspend fun updateLastAccessed(key: ReaderAssetKeyHash, epochMillis: Long) = Unit
    override suspend fun updateLastConsumed(key: ReaderAssetKeyHash, epochMillis: Long) = Unit
}

private object EmptyReaderAssetBlobStore : ReaderAssetBlobStore {
    override suspend fun writeAtomic(id: ReaderAssetBlobId, bytes: ByteArray): ReaderAssetBlobWriteResult =
        error("Reader asset writes require the shared production coordinator dependencies.")
    override suspend fun open(id: ReaderAssetBlobId): ReaderAssetBlobReadLease? = null
    override suspend fun exists(id: ReaderAssetBlobId) = false
    override suspend fun hasActiveReadLease(id: ReaderAssetBlobId) = false
    override suspend fun tryDeleteNowIfUnleased(id: ReaderAssetBlobId) = false
    override suspend fun deleteWhenUnleased(id: ReaderAssetBlobId) = Unit
}

private const val MAX_NORMAL_RECONCILIATION_VICTIMS = 32
private const val DEFAULT_AUTOMATIC_CACHE_QUOTA_BYTES = 256L * 1024 * 1024
