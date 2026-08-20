package app.openstory.catalog.metadata

import app.openstory.catalog.details.CatalogDetailsLoadResult
import app.openstory.catalog.details.CatalogDetailsLoader
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.common.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class CatalogMetadataCoordinator @Inject constructor(
    private val repository: CatalogRepository,
    private val sources: CatalogSourceRegistry,
    private val loader: CatalogDetailsLoader,
    private val policy: CatalogMetadataPolicy,
    private val clock: Clock,
    @CatalogMetadataScope private val processScope: CoroutineScope,
) {
    private val suppressionMutex = Mutex()
    private val suppressions = mutableMapOf<CatalogMetadataKey, AutomaticSuppression>()
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<CatalogMetadataKey, Deferred<CatalogDetailsLoadResult>>()

    suspend fun require(
        key: CatalogMetadataKey,
        level: CatalogMetadataLevel,
    ): CatalogMetadataResult {
        val snapshot = repository.metadataSnapshot(key)
        val stamp = snapshot?.stamp(level)
        return when {
            level == CatalogMetadataLevel.Summary ->
                snapshot?.ready() ?: CatalogMetadataResult.Missing
            snapshot != null && stamp != null -> {
                scheduleVerification(key, level)
                snapshot.ready()
            }
            else -> {
                val preflight = automaticPreflight(key)
                preflight.failure?.let(CatalogMetadataResult::Failure)
                    ?: awaitSharedLoad(key, preflight.sourceHint)
            }
        }
    }

    suspend fun refresh(
        key: CatalogMetadataKey,
        level: CatalogMetadataLevel,
    ): CatalogMetadataResult {
        require(level != CatalogMetadataLevel.Summary) {
            "Summary cannot be explicitly refreshed through Details"
        }
        return awaitSharedLoad(key, sourceHint = null)
    }

    private fun scheduleVerification(
        key: CatalogMetadataKey,
        level: CatalogMetadataLevel,
    ) {
        processScope.launch {
            if (activeCooldownFailure(key) != null) return@launch

            val source = resolveSource(key)
            if (source == null) {
                if (!hasVersionSuppression(key)) {
                    recordSuppression(
                        key,
                        AutomaticSuppression.Cooldown(
                            CatalogMetadataFailure.SourceUnavailable(key.pluginId),
                            clock.nowEpochMillis(),
                        ),
                    )
                }
                return@launch
            }
            if (isVersionSuppressed(key, source.version)) return@launch
            val currentStamp = latestStamp(key, level)
            if (currentStamp != null && policy.isFresh(level, currentStamp, source.version)) return@launch

            awaitSharedLoad(key, source)
        }
    }

    private suspend fun automaticPreflight(key: CatalogMetadataKey): AutomaticPreflight {
        val cooldownFailure = activeCooldownFailure(key)
        val versionSuppression = if (cooldownFailure == null) {
            suppressionMutex.withLock {
                suppressions[key] as? AutomaticSuppression.PluginVersion
            }
        } else {
            null
        }
        return when {
            cooldownFailure != null -> AutomaticPreflight(failure = cooldownFailure)
            versionSuppression == null -> AutomaticPreflight()
            else -> {
                val source = resolveSource(key)
                when {
                    source == null -> AutomaticPreflight(failure = versionSuppression.failure)
                    source.version == versionSuppression.pluginVersion ->
                        AutomaticPreflight(failure = versionSuppression.failure)
                    else -> {
                        removeSuppressionIfSame(key, versionSuppression)
                        AutomaticPreflight(sourceHint = source)
                    }
                }
            }
        }
    }

    private suspend fun activeCooldownFailure(key: CatalogMetadataKey): CatalogMetadataFailure? {
        val cooldown = suppressionMutex.withLock {
            suppressions[key] as? AutomaticSuppression.Cooldown
        } ?: return null
        return if (policy.isRetryCooldownActive(cooldown.recordedAtEpochMillis)) {
            cooldown.failure
        } else {
            removeSuppressionIfSame(key, cooldown)
            null
        }
    }

    private suspend fun hasVersionSuppression(key: CatalogMetadataKey): Boolean =
        suppressionMutex.withLock { suppressions[key] is AutomaticSuppression.PluginVersion }

    private suspend fun isVersionSuppressed(
        key: CatalogMetadataKey,
        currentPluginVersion: String,
    ): Boolean {
        val record = suppressionMutex.withLock {
            suppressions[key] as? AutomaticSuppression.PluginVersion
        } ?: return false
        return if (record.pluginVersion == currentPluginVersion) {
            true
        } else {
            removeSuppressionIfSame(key, record)
            false
        }
    }

    private suspend fun awaitSharedLoad(
        key: CatalogMetadataKey,
        sourceHint: CatalogSource?,
    ): CatalogMetadataResult {
        val deferred = inFlightMutex.withLock {
            inFlight[key] ?: createSharedLoad(key, sourceHint).also { created ->
                inFlight[key] = created
                created.invokeOnCompletion {
                    processScope.launch {
                        removeInFlightIfSame(key, created)
                    }
                }
                created.start()
            }
        }

        return try {
            when (val result = deferred.await()) {
                is CatalogDetailsLoadResult.Success -> CatalogMetadataResult.Ready(result.storyId, result.entry)
                is CatalogDetailsLoadResult.Failure -> CatalogMetadataResult.Failure(result.failure)
            }
        } finally {
            if (deferred.isCompleted) {
                removeInFlightIfSame(key, deferred)
            }
        }
    }

    private fun createSharedLoad(
        key: CatalogMetadataKey,
        sourceHint: CatalogSource?,
    ): Deferred<CatalogDetailsLoadResult> = processScope.async(start = CoroutineStart.LAZY) {
        loader.load(key, sourceHint).also { result ->
            when (result) {
                is CatalogDetailsLoadResult.Success -> clearSuppression(key)
                is CatalogDetailsLoadResult.Failure -> recordFailure(key, result)
            }
        }
    }

    private suspend fun latestStamp(
        key: CatalogMetadataKey,
        level: CatalogMetadataLevel,
    ): CatalogMetadataStamp? = try {
        repository.metadataSnapshot(key)?.stamp(level)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private suspend fun removeInFlightIfSame(
        key: CatalogMetadataKey,
        expected: Deferred<CatalogDetailsLoadResult>,
    ) {
        inFlightMutex.withLock {
            if (inFlight[key] === expected) {
                inFlight.remove(key)
            }
        }
    }

    private suspend fun recordFailure(
        key: CatalogMetadataKey,
        result: CatalogDetailsLoadResult.Failure,
    ) {
        val suppression = when (val failure = result.failure) {
            is CatalogMetadataFailure.SourceUnavailable -> AutomaticSuppression.Cooldown(
                failure,
                clock.nowEpochMillis(),
            )
            is CatalogMetadataFailure.SourceFailure -> if (failure.retryable) {
                AutomaticSuppression.Cooldown(failure, clock.nowEpochMillis())
            } else {
                result.attemptedPluginVersion?.let { version ->
                    AutomaticSuppression.PluginVersion(failure, version)
                } ?: AutomaticSuppression.Cooldown(failure, clock.nowEpochMillis())
            }
            is CatalogMetadataFailure.SourceIdMismatch ->
                result.attemptedPluginVersion?.let { version ->
                    AutomaticSuppression.PluginVersion(failure, version)
                } ?: AutomaticSuppression.Cooldown(failure, clock.nowEpochMillis())
            is CatalogMetadataFailure.StoreFailure -> AutomaticSuppression.Cooldown(
                failure,
                clock.nowEpochMillis(),
            )
        }
        recordSuppression(key, suppression)
    }

    private suspend fun recordSuppression(
        key: CatalogMetadataKey,
        suppression: AutomaticSuppression,
    ) {
        suppressionMutex.withLock {
            suppressions[key] = suppression
        }
    }

    private suspend fun clearSuppression(key: CatalogMetadataKey) {
        suppressionMutex.withLock {
            suppressions.remove(key)
        }
    }

    private suspend fun removeSuppressionIfSame(
        key: CatalogMetadataKey,
        expected: AutomaticSuppression,
    ) {
        suppressionMutex.withLock {
            if (suppressions[key] === expected) {
                suppressions.remove(key)
            }
        }
    }

    private suspend fun resolveSource(key: CatalogMetadataKey): CatalogSource? = try {
        sources.source(key.pluginId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private data class AutomaticPreflight(
        val failure: CatalogMetadataFailure? = null,
        val sourceHint: CatalogSource? = null,
    )

    private sealed interface AutomaticSuppression {
        val failure: CatalogMetadataFailure

        data class Cooldown(
            override val failure: CatalogMetadataFailure,
            val recordedAtEpochMillis: Long,
        ) : AutomaticSuppression

        data class PluginVersion(
            override val failure: CatalogMetadataFailure,
            val pluginVersion: String,
        ) : AutomaticSuppression
    }
}

private fun CatalogMetadataSnapshot.ready() = CatalogMetadataResult.Ready(entry.storyId, entry)

private fun CatalogMetadataSnapshot.stamp(level: CatalogMetadataLevel): CatalogMetadataStamp? = when (level) {
    CatalogMetadataLevel.Summary -> summary
    CatalogMetadataLevel.Full -> full
}
