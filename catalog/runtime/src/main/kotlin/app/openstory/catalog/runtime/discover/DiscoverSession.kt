package app.openstory.catalog.runtime.discover

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.read.DiscoverReadPort
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionExecutor
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DiscoverSessionState(
    val persistence: DiscoverPersistenceState?,
    val acquisition: CatalogAcquisitionStatus,
)

class DiscoverSession internal constructor(
    private val binding: CatalogSourceBinding,
    private val mediaType: CatalogMediaType,
    readPort: DiscoverReadPort,
    private val executor: CatalogAcquisitionExecutor,
    private val scope: CoroutineScope,
    private val onFirstSnapshot: () -> Unit,
) {
    private val bootstrapMutex = Mutex()
    private val acquisition = MutableStateFlow<CatalogAcquisitionStatus>(CatalogAcquisitionStatus.Idle)
    private var automaticBootstrapStarted = false
    private var automaticBootstrapJob: Job? = null

    private val persistenceEvents = flow {
        emitAll(readPort.observe(binding.catalogSourceKey, mediaType))
    }.map<DiscoverPersistenceState, PersistenceEvent> { PersistenceEvent.Value(it) }
        .onEach { event ->
            if (event is PersistenceEvent.Value) onFirstSnapshot()
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(PersistenceEvent.ReadFailure(error.toStorageFailure(CatalogStorageOperation.READ_DISCOVER)))
        }

    val states: Flow<DiscoverSessionState> = combine(persistenceEvents, acquisition) { event, status ->
        when (event) {
            is PersistenceEvent.Value -> DiscoverSessionState(event.value, status)
            is PersistenceEvent.ReadFailure -> DiscoverSessionState(
                persistence = null,
                acquisition = CatalogAcquisitionStatus.Failed(event.failure),
            )
        }
    }.onEach { state ->
        if (state.persistence == DiscoverPersistenceState.Absent) startAutomaticBootstrap()
    }.shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    suspend fun refresh(): CatalogAcquisitionResult {
        acquisition.value = CatalogAcquisitionStatus.Running
        return try {
            executor.acquireDiscover(mediaType).also { result -> acquisition.value = result.toStatus() }
        } catch (error: CancellationException) {
            acquisition.value = CatalogAcquisitionStatus.Idle
            throw error
        }
    }

    suspend fun quiesce() {
        val interruptedAutomaticBootstrap = automaticBootstrapJob?.isActive == true
        executor.cancelDiscover(mediaType)
        automaticBootstrapJob?.cancel()
        automaticBootstrapJob = null
        if (interruptedAutomaticBootstrap) {
            bootstrapMutex.withLock { automaticBootstrapStarted = false }
        }
        if (acquisition.value == CatalogAcquisitionStatus.Running) {
            acquisition.value = CatalogAcquisitionStatus.Idle
        }
    }

    private suspend fun startAutomaticBootstrap() {
        bootstrapMutex.withLock {
            if (automaticBootstrapStarted) return
            automaticBootstrapStarted = true
            if (binding.acquisitionSource == null) {
                acquisition.value = CatalogAcquisitionStatus.Failed(CatalogFailure.SourceUnavailable)
                return
            }
            automaticBootstrapJob = scope.launch {
                try {
                    refresh()
                } finally {
                    bootstrapMutex.withLock {
                        automaticBootstrapJob = null
                    }
                }
            }
        }
    }
}

private sealed interface PersistenceEvent {
    data class Value(val value: DiscoverPersistenceState) : PersistenceEvent
    data class ReadFailure(val failure: CatalogFailure) : PersistenceEvent
}

private fun CatalogAcquisitionResult.toStatus(): CatalogAcquisitionStatus = when (this) {
    CatalogAcquisitionResult.Success -> CatalogAcquisitionStatus.Success
    is CatalogAcquisitionResult.Failed -> CatalogAcquisitionStatus.Failed(failure)
}

private fun Throwable.toStorageFailure(operation: CatalogStorageOperation): CatalogFailure =
    (this as? CatalogFailureException)?.failure ?: CatalogFailure.Storage(operation)
