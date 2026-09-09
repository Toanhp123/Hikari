package app.openstory.catalog.runtime.story

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.domain.read.StoryDetailReadPort
import app.openstory.catalog.domain.write.CatalogWritePort
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionExecutor
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.retention.ActiveStoryPins
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class StoryDetailSessionState(
    val projection: StoryDetailProjection?,
    val acquisition: CatalogAcquisitionStatus,
)

class StoryDetailSession internal constructor(
    private val binding: CatalogSourceBinding,
    private val ref: StorySourceRef,
    readPort: StoryDetailReadPort,
    private val writePort: CatalogWritePort,
    private val activeStoryPins: ActiveStoryPins,
    private val executor: CatalogAcquisitionExecutor,
    private val wallClockEpochMs: () -> Long,
    private val scope: CoroutineScope,
    private val onReleased: (StoryDetailSession) -> Unit,
) {
    private val activationMutex = Mutex()
    private val acquisitionMutex = Mutex()
    private val acquisition = MutableStateFlow<CatalogAcquisitionStatus>(CatalogAcquisitionStatus.Idle)
    private var activated = false
    private var automaticAcquisitionStarted = false
    private var latestProjection: StoryDetailProjection? = null

    private val projectionEvents = flow {
        emitAll(readPort.observe(ref))
    }.map<StoryDetailProjection?, ProjectionEvent> { ProjectionEvent.Value(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(ProjectionEvent.ReadFailure(error.toStorageFailure(CatalogStorageOperation.READ_STORY)))
        }

    private val observedProjectionEvents = projectionEvents.onEach { event ->
        if (event is ProjectionEvent.Value) {
            latestProjection = event.value
            if (event.value?.detail == null) startAutomaticAcquisition()
        }
    }

    private val states = combine(observedProjectionEvents, acquisition) { event, status ->
        when (event) {
            is ProjectionEvent.Value -> StoryDetailSessionState(event.value, status)
            is ProjectionEvent.ReadFailure -> StoryDetailSessionState(
                projection = null,
                acquisition = CatalogAcquisitionStatus.Failed(event.failure),
            )
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    suspend fun activate(): Flow<StoryDetailSessionState> {
        activationMutex.withLock {
            if (activated) return@withLock
            requireMatchingBinding()
            activeStoryPins.register(ref)
            val accessFailure = runCatching { touchAccess() }.exceptionOrNull()
            if (accessFailure != null) {
                withContext(NonCancellable) { activeStoryPins.unregisterFailedActivation(ref) }
                throw accessFailure
            }
            activated = true
        }
        return states
    }

    suspend fun retry(): CatalogAcquisitionResult {
        require(activated)
        if (latestProjection?.detail != null) return CatalogAcquisitionResult.Success
        acquisition.value = CatalogAcquisitionStatus.Running
        return executor.acquireStoryDetail(ref).also { acquisition.value = it.toStatus() }
    }

    suspend fun release() {
        activationMutex.withLock {
            if (!activated) return
            activeStoryPins.release(ref, wallClockEpochMs())
            activated = false
            onReleased(this)
        }
    }

    private suspend fun startAutomaticAcquisition() {
        acquisitionMutex.withLock {
            if (automaticAcquisitionStarted) return
            automaticAcquisitionStarted = true
            if (binding.acquisitionSource == null) {
                acquisition.value = CatalogAcquisitionStatus.Failed(CatalogFailure.SourceUnavailable)
                return
            }
            acquisition.value = CatalogAcquisitionStatus.Running
            scope.launch {
                acquisition.value = executor.acquireStoryDetail(ref).toStatus()
            }
        }
    }

    private fun requireMatchingBinding() {
        if (ref.catalogSourceKey != binding.catalogSourceKey) {
            throw CatalogFailureException(
                CatalogFailure.Validation(
                    field = "ref.catalogSourceKey",
                    reason = CatalogValidationReason.AUTHORITY_MISMATCH,
                ),
            )
        }
    }

    private suspend fun touchAccess() {
        runCatching { writePort.touchStoryAccess(ref, wallClockEpochMs()) }
            .getOrElse { error ->
                throw error.toStorageException(CatalogStorageOperation.RETENTION)
            }
    }
}

private sealed interface ProjectionEvent {
    data class Value(val value: StoryDetailProjection?) : ProjectionEvent
    data class ReadFailure(val failure: CatalogFailure) : ProjectionEvent
}

private fun CatalogAcquisitionResult.toStatus(): CatalogAcquisitionStatus = when (this) {
    CatalogAcquisitionResult.Success -> CatalogAcquisitionStatus.Success
    is CatalogAcquisitionResult.Failed -> CatalogAcquisitionStatus.Failed(failure)
}

private fun Throwable.toStorageException(operation: CatalogStorageOperation): Throwable = when (this) {
    is CancellationException, is CatalogFailureException -> this
    else -> CatalogFailureException(CatalogFailure.Storage(operation), this)
}

private fun Throwable.toStorageFailure(operation: CatalogStorageOperation): CatalogFailure =
    (this as? CatalogFailureException)?.failure ?: CatalogFailure.Storage(operation)
