package app.openstory.reader.assets

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReaderAssetLoader(
    private val store: ReaderAssetStorePort,
    private val delivery: ReaderAssetDeliveryPort,
    private val singleFlight: ReaderAssetSingleFlight,
    private val fetchArbiter: ContentFetchArbiter,
    private val persistenceScope: CoroutineScope,
    private val diagnostics: ReaderAssetDiagnosticsSink = ReaderAssetDiagnosticsSink.NO_OP,
) {
    fun invalidateSecurityScopedSource(sourceNamespace: ReaderAssetSourceNamespace) {
        singleFlight.invalidateSecurityScopedSource(sourceNamespace)
    }

    suspend fun load(
        facts: ReaderAssetCommitFacts,
        descriptor: ReaderPageAssetDescriptor,
        localPresence: ReaderAssetLocalPresence,
        priority: ContentFetchPriority,
        consumer: ReaderAssetConsumerToken,
    ): ReaderAssetLoadOutcome {
        require(facts.key == descriptor.key) { "Reader asset facts and descriptor key must match." }
        val local = resolveLocal(descriptor.key, localPresence, priority)
        return when (local) {
            is LocalResolution.Complete -> local.outcome
            is LocalResolution.Remote -> loadRemote(facts, descriptor, priority, consumer, local.allowPersistence)
        }
    }

    private suspend fun resolveLocal(
        key: ReaderPageAssetKey,
        presence: ReaderAssetLocalPresence,
        priority: ContentFetchPriority,
    ): LocalResolution = when (presence) {
        ReaderAssetLocalPresence.LOCAL_AVAILABLE -> openLocal(key, priority)
        ReaderAssetLocalPresence.LOCAL_MISSING -> LocalResolution.Remote(allowPersistence = true)
        ReaderAssetLocalPresence.LOCAL_UNAVAILABLE -> cacheUnavailable(priority)
        ReaderAssetLocalPresence.UNKNOWN -> inspectUnknown(key, priority)
    }

    private suspend fun inspectUnknown(
        key: ReaderPageAssetKey,
        priority: ContentFetchPriority,
    ): LocalResolution = if (!priority.isVisibleReaderDemand()) {
        LocalResolution.Complete(ReaderAssetLoadOutcome.Failure(ReaderAssetFailure.CacheStorageUnavailable))
    } else {
        val resolved = readerAssetStorageCall {
            store.inspect(setOf(key))[key] ?: ReaderAssetLocalPresence.LOCAL_UNAVAILABLE
        }.getOrElse { ReaderAssetLocalPresence.LOCAL_UNAVAILABLE }
        when (resolved) {
            ReaderAssetLocalPresence.LOCAL_AVAILABLE -> openLocal(key, priority)
            ReaderAssetLocalPresence.LOCAL_MISSING -> LocalResolution.Remote(allowPersistence = true)
            ReaderAssetLocalPresence.UNKNOWN,
            ReaderAssetLocalPresence.LOCAL_UNAVAILABLE,
            -> cacheUnavailable(priority)
        }
    }

    private suspend fun openLocal(
        key: ReaderPageAssetKey,
        priority: ContentFetchPriority,
    ): LocalResolution {
        val opened = readerAssetStorageCall { store.openLocal(key) }
            .getOrElse { ReaderAssetOpenResult.Unavailable }
        return when (opened) {
            is ReaderAssetOpenResult.Available -> {
                diagnostics.recordSafely(ReaderAssetDiagnosticEvent.DiskHit)
                LocalResolution.Complete(ReaderAssetLoadOutcome.Local(opened.lease))
            }
            ReaderAssetOpenResult.Missing -> LocalResolution.Remote(allowPersistence = true)
            ReaderAssetOpenResult.Corrupt -> {
                diagnostics.recordSafely(ReaderAssetDiagnosticEvent.Corruption)
                readerAssetStorageCall { store.invalidate(key, ReaderAssetInvalidationReason.CORRUPT) }
                LocalResolution.Remote(allowPersistence = true)
            }
            ReaderAssetOpenResult.Unavailable -> cacheUnavailable(priority)
        }
    }

    private fun cacheUnavailable(priority: ContentFetchPriority): LocalResolution =
        if (priority.isVisibleReaderDemand()) {
            LocalResolution.Remote(allowPersistence = false)
        } else {
            LocalResolution.Complete(ReaderAssetLoadOutcome.Failure(ReaderAssetFailure.CacheStorageUnavailable))
        }

    private suspend fun loadRemote(
        facts: ReaderAssetCommitFacts,
        descriptor: ReaderPageAssetDescriptor,
        priority: ContentFetchPriority,
        consumer: ReaderAssetConsumerToken,
        allowPersistence: Boolean,
    ): ReaderAssetLoadOutcome {
        val leader = RemoteLeaderState()
        val remote = singleFlight.acquireRemote(
            key = facts.key,
            priority = priority,
            consumer = consumer,
            producer = { demand ->
                leader.authority = if (allowPersistence) captureAuthority(facts) else null
                fetchArbiter.withAdmission(demand) { fetchWithRetry(facts.key, descriptor.deliveryLocator) }
            },
            afterSuccess = { payload -> launchCommit(facts, leader.authority, payload) },
        )
        return when (remote) {
            is ReaderAssetRemoteOutcome.Success -> ReaderAssetLoadOutcome.Remote(remote.payload)
            is ReaderAssetRemoteOutcome.Failure -> ReaderAssetLoadOutcome.Failure(remote.failure)
        }
    }

    private suspend fun captureAuthority(facts: ReaderAssetCommitFacts): ReaderAssetDurableWriteAuthority? =
        readerAssetStorageCall { store.captureDurableWriteAuthority(facts) }.getOrNull()

    private suspend fun fetchWithRetry(
        key: ReaderPageAssetKey,
        deliveryLocator: String,
    ): ReaderAssetRemoteOutcome {
        val request = ReaderAssetDeliveryRequest(key, deliveryLocator)
        val first = fetchOnce(request)
        return if (first.shouldRetry()) {
            delay(ReaderAssetRuntimePolicy.TRANSIENT_ASSET_RETRY_DELAY_MILLIS)
            fetchOnce(request)
        } else {
            first
        }
    }

    private suspend fun fetchOnce(request: ReaderAssetDeliveryRequest): ReaderAssetRemoteOutcome {
        diagnostics.recordSafely(ReaderAssetDiagnosticEvent.NetworkFetch)
        return when (val result = delivery.fetch(request)) {
            is ReaderAssetDeliveryResult.Success -> ReaderAssetRemoteOutcome.Success(result.payload)
            is ReaderAssetDeliveryResult.Failure -> ReaderAssetRemoteOutcome.Failure(result.failure)
        }
    }

    private fun launchCommit(
        facts: ReaderAssetCommitFacts,
        authority: ReaderAssetDurableWriteAuthority?,
        payload: ReaderAssetPayload,
    ) = authority?.let { captured ->
        persistenceScope.launch {
            try {
                store.commit(facts, captured, payload)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                // Remote bytes remain valid even when the one background commit degrades.
            }
        }
    }

    private sealed interface LocalResolution {
        data class Complete(val outcome: ReaderAssetLoadOutcome) : LocalResolution
        data class Remote(val allowPersistence: Boolean) : LocalResolution
    }

    private class RemoteLeaderState {
        var authority: ReaderAssetDurableWriteAuthority? = null
    }
}

private fun ReaderAssetRemoteOutcome.shouldRetry(): Boolean =
    this is ReaderAssetRemoteOutcome.Failure &&
        failure is ReaderAssetFailure.TransportUnavailable &&
        failure.retryable

private fun ContentFetchPriority.isVisibleReaderDemand(): Boolean =
    this == ContentFetchPriority.CRITICAL || this == ContentFetchPriority.INTERACTIVE

private sealed interface ReaderAssetStorageCall<out T> {
    data class Value<T>(val value: T) : ReaderAssetStorageCall<T>
    data class Failed(val cause: Throwable) : ReaderAssetStorageCall<Nothing>
}

private fun <T> ReaderAssetStorageCall<T>.getOrNull(): T? =
    (this as? ReaderAssetStorageCall.Value)?.value

private fun <T> ReaderAssetStorageCall<T>.getOrElse(fallback: () -> T): T = when (this) {
    is ReaderAssetStorageCall.Value -> value
    is ReaderAssetStorageCall.Failed -> fallback()
}

@Suppress("TooGenericExceptionCaught")
private suspend inline fun <T> readerAssetStorageCall(
    block: suspend () -> T,
): ReaderAssetStorageCall<T> = try {
    ReaderAssetStorageCall.Value(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    ReaderAssetStorageCall.Failed(failure)
}
