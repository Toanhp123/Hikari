package app.openstory.catalog.runtime.acquisition

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogOperation
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext

sealed interface CatalogAcquisitionResult {
    data object Success : CatalogAcquisitionResult
    data class Failed(val failure: CatalogFailure) : CatalogAcquisitionResult
}

sealed interface CatalogAcquisitionStatus {
    data object Idle : CatalogAcquisitionStatus
    data object Running : CatalogAcquisitionStatus
    data object Success : CatalogAcquisitionStatus
    data class Failed(val failure: CatalogFailure) : CatalogAcquisitionStatus
}

class CatalogAcquisitionExecutor(
    private val binding: CatalogSourceBinding,
    private val importer: CatalogImporter,
    private val wallClockEpochMs: () -> Long,
    private val dispatchers: CatalogExecutionDispatchers,
    private val parentScope: CoroutineScope,
) {
    private val activeLock = Any()
    private val active = mutableMapOf<WorkKey, Deferred<CatalogAcquisitionResult>>()

    suspend fun acquireDiscover(mediaType: CatalogMediaType): CatalogAcquisitionResult =
        executeSingleFlight(WorkKey.Discover(binding.catalogSourceKey.value, mediaType)) {
            val source = binding.acquisitionSource
                ?: return@executeSingleFlight CatalogAcquisitionResult.Failed(CatalogFailure.SourceUnavailable)
            val acquisition = when (val result = acquireFromSource(CatalogOperation.DISCOVER) {
                source.acquireDiscover(mediaType)
            }) {
                is SourceAcquisition.Value -> result.value
                is SourceAcquisition.Failed -> {
                    return@executeSingleFlight CatalogAcquisitionResult.Failed(result.failure)
                }
            }
            persist(CatalogStorageOperation.PUBLISH_DISCOVER) {
                importer.publishDiscover(binding, mediaType, acquisition, wallClockEpochMs())
            }
        }

    suspend fun acquireStoryDetail(ref: StorySourceRef): CatalogAcquisitionResult =
        executeSingleFlight(WorkKey.Story(ref)) {
            if (ref.catalogSourceKey != binding.catalogSourceKey) {
                return@executeSingleFlight CatalogAcquisitionResult.Failed(
                    CatalogFailure.Validation(
                        field = "ref.catalogSourceKey",
                        reason = app.openstory.catalog.domain.failure.CatalogValidationReason.AUTHORITY_MISMATCH,
                    ),
                )
            }
            val source = binding.acquisitionSource
                ?: return@executeSingleFlight CatalogAcquisitionResult.Failed(CatalogFailure.SourceUnavailable)
            val acquisition = when (val result = acquireFromSource(CatalogOperation.STORY_DETAIL) {
                source.acquireStoryDetail(ref)
            }) {
                is SourceAcquisition.Value -> result.value
                is SourceAcquisition.Failed -> {
                    return@executeSingleFlight CatalogAcquisitionResult.Failed(result.failure)
                }
            }
            persist(CatalogStorageOperation.PUBLISH_STORY) {
                importer.upsertStoryDetail(binding, ref, acquisition, wallClockEpochMs())
            }
        }

    fun activeWorkCount(): Int = synchronized(activeLock) { active.size }

    suspend fun cancelDiscover(mediaType: CatalogMediaType) {
        cancel(WorkKey.Discover(binding.catalogSourceKey.value, mediaType))
    }

    suspend fun cancelStory(ref: StorySourceRef) {
        cancel(WorkKey.Story(ref))
    }

    private suspend fun executeSingleFlight(
        key: WorkKey,
        operation: suspend () -> CatalogAcquisitionResult,
    ): CatalogAcquisitionResult {
        val work = synchronized(activeLock) {
            active[key] ?: parentScope.async(start = CoroutineStart.LAZY) { operation() }
                .also { created ->
                    active[key] = created
                    created.invokeOnCompletion {
                        synchronized(activeLock) {
                            if (active[key] === created) active.remove(key)
                        }
                    }
                    created.start()
                }
        }
        return work.await()
    }

    private suspend fun cancel(key: WorkKey) {
        val work = synchronized(activeLock) { active[key] } ?: return
        work.cancelAndJoin()
    }

    private suspend fun <T> acquireFromSource(
        operation: CatalogOperation,
        acquire: suspend () -> T,
    ): SourceAcquisition<T> = try {
        SourceAcquisition.Value(withContext(dispatchers.io) { acquire() })
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        SourceAcquisition.Failed(CatalogFailure.Acquisition(operation))
    }

    private suspend fun persist(
        operation: CatalogStorageOperation,
        block: suspend () -> Unit,
    ): CatalogAcquisitionResult = try {
        block()
        CatalogAcquisitionResult.Success
    } catch (error: CancellationException) {
        throw error
    } catch (error: CatalogFailureException) {
        CatalogAcquisitionResult.Failed(error.failure)
    } catch (_: Throwable) {
        CatalogAcquisitionResult.Failed(CatalogFailure.Storage(operation))
    }
}

private sealed interface SourceAcquisition<out T> {
    data class Value<T>(val value: T) : SourceAcquisition<T>
    data class Failed(val failure: CatalogFailure) : SourceAcquisition<Nothing>
}

private sealed interface WorkKey {
    data class Discover(
        val sourceKey: String,
        val mediaType: CatalogMediaType,
    ) : WorkKey

    data class Story(val ref: StorySourceRef) : WorkKey
}
