package app.openstory.reader.assets

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ReaderAssetSingleFlight(
    private val processScope: CoroutineScope,
) {
    private class Entry(
        val key: ReaderPageAssetKey,
        val demand: ContentFetchDemand,
    ) {
        val result = CompletableDeferred<ReaderAssetRemoteOutcome>()
        val consumers = mutableSetOf<ReaderAssetConsumerToken>()
        var producerJob: Job? = null
        var persistenceJob: Job? = null
        var producerFinished = false
        var securityInvalidated = false
    }

    private data class Registration(
        val entry: Entry,
        val leader: Boolean,
    )

    private val lock = Any()
    private val entries = mutableMapOf<ReaderPageAssetKey, Entry>()

    suspend fun acquireRemote(
        key: ReaderPageAssetKey,
        priority: ContentFetchPriority,
        consumer: ReaderAssetConsumerToken,
        producer: suspend (ContentFetchDemand) -> ReaderAssetRemoteOutcome,
        afterSuccess: (ReaderAssetPayload) -> Job?,
    ): ReaderAssetRemoteOutcome {
        val registration = register(key, priority, consumer)
        registration.entry.demand.promoteTo(priority)
        if (registration.leader) startProducer(registration.entry, producer, afterSuccess)
        return try {
            registration.entry.result.await()
        } finally {
            unregister(registration.entry, consumer)
        }
    }

    fun invalidateSecurityScopedSource(sourceNamespace: ReaderAssetSourceNamespace) {
        val jobs = synchronized(lock) {
            entries.values
                .filter { entry ->
                    entry.key.sourceNamespace == sourceNamespace &&
                        entry.key.securityScope != ReaderCacheSecurityScope.Public
                }
                .onEach { entry ->
                    entry.securityInvalidated = true
                    entry.result.complete(ReaderAssetRemoteOutcome.Failure(ReaderAssetFailure.RouteInvalidated))
                }
                .flatMap { entry -> listOfNotNull(entry.producerJob, entry.persistenceJob) }
        }
        jobs.forEach(Job::cancel)
    }

    private fun register(
        key: ReaderPageAssetKey,
        priority: ContentFetchPriority,
        consumer: ReaderAssetConsumerToken,
    ): Registration = synchronized(lock) {
        val current = entries[key]?.takeUnless(Entry::securityInvalidated)
        val entry = current ?: Entry(key, ContentFetchDemand(priority)).also { entries[key] = it }
        entry.consumers += consumer
        Registration(entry, leader = current == null)
    }

    private fun startProducer(
        entry: Entry,
        producer: suspend (ContentFetchDemand) -> ReaderAssetRemoteOutcome,
        afterSuccess: (ReaderAssetPayload) -> Job?,
    ) {
        val job = processScope.launch(start = CoroutineStart.LAZY) {
            runProducer(entry, producer, afterSuccess)
        }
        val invalidated = synchronized(lock) {
            entry.producerJob = job
            entry.securityInvalidated
        }
        if (invalidated) job.cancel() else job.start()
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private suspend fun runProducer(
        entry: Entry,
        producer: suspend (ContentFetchDemand) -> ReaderAssetRemoteOutcome,
        afterSuccess: (ReaderAssetPayload) -> Job?,
    ) {
        try {
            completeProducer(entry, producer(entry.demand), afterSuccess)
        } catch (cancelled: CancellationException) {
            completeCancellation(entry)
        } catch (failure: Exception) {
            completeProducer(
                entry,
                ReaderAssetRemoteOutcome.Failure(ReaderAssetFailure.TransportUnavailable(retryable = true)),
                afterSuccess,
            )
        } finally {
            producerFinished(entry)
        }
    }

    @Suppress("SwallowedException")
    private fun completeProducer(
        entry: Entry,
        outcome: ReaderAssetRemoteOutcome,
        afterSuccess: (ReaderAssetPayload) -> Job?,
    ) {
        val persistenceJob = synchronized(lock) {
            if (entry.securityInvalidated) {
                entry.result.complete(ReaderAssetRemoteOutcome.Failure(ReaderAssetFailure.RouteInvalidated))
                null
            } else {
                val persistence = (outcome as? ReaderAssetRemoteOutcome.Success)
                    ?.let { success -> runCatching { afterSuccess(success.payload) }.getOrNull() }
                entry.persistenceJob = persistence
                entry.result.complete(outcome)
                persistence
            }
        }
        persistenceJob?.invokeOnCompletion { persistenceFinished(entry) }
    }

    private fun completeCancellation(entry: Entry) {
        synchronized(lock) {
            val failure = if (entry.securityInvalidated) {
                ReaderAssetFailure.RouteInvalidated
            } else {
                ReaderAssetFailure.Cancelled
            }
            entry.result.complete(ReaderAssetRemoteOutcome.Failure(failure))
        }
    }

    private fun unregister(entry: Entry, consumer: ReaderAssetConsumerToken) {
        val producerToCancel = synchronized(lock) {
            entry.consumers -= consumer
            entry.producerJob.takeIf {
                entry.consumers.isEmpty() &&
                    !entry.result.isCompleted &&
                    entry.demand.priority == ContentFetchPriority.SPECULATIVE
            }
        }
        producerToCancel?.cancel()
    }

    private fun producerFinished(entry: Entry) {
        synchronized(lock) {
            entry.producerFinished = true
            removeIfFinishedLocked(entry)
        }
    }

    private fun persistenceFinished(entry: Entry) {
        synchronized(lock) { removeIfFinishedLocked(entry) }
    }

    private fun removeIfFinishedLocked(entry: Entry) {
        val persistenceFinished = entry.persistenceJob?.isCompleted != false
        if (entry.producerFinished && persistenceFinished && entries[entry.key] === entry) {
            entries.remove(entry.key)
        }
    }
}
