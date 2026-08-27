package app.openstory.catalog.ui.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal sealed interface ObservationState<out K, out T> {
    val key: K

    data class Pending<K>(
        override val key: K,
    ) : ObservationState<K, Nothing>

    data class Available<K, T>(
        override val key: K,
        val value: T,
        val issue: CatalogUiFailure? = null,
    ) : ObservationState<K, T>

    data class Unavailable<K>(
        override val key: K,
        val failure: CatalogUiFailure,
    ) : ObservationState<K, Nothing>
}

internal fun ObservationState<*, *>.hasRetainedIssue(): Boolean =
    this is ObservationState.Available<*, *> && issue != null

internal fun ObservationState<*, *>.hasIssueOrUnavailable(): Boolean =
    when (this) {
        is ObservationState.Available<*, *> -> issue != null
        is ObservationState.Unavailable<*> -> true
        is ObservationState.Pending<*> -> false
    }

internal fun <K, T> ObservationState<K, T>.forExpectedKey(
    expectedKey: K,
): ObservationState<K, T> =
    if (key == expectedKey) this else ObservationState.Pending(expectedKey)

private class ObservationCompletedWithoutValueException :
    IllegalStateException("Observation completed before emitting a value")

internal class RetainedObservation<K, T> internal constructor(
    val state: StateFlow<ObservationState<K, T>>,
    private val retryEpoch: MutableStateFlow<Long>,
) {
    fun retry() {
        retryEpoch.update { it + 1L }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <K, T> CoroutineScope.retainedObservation(
    key: Flow<K>,
    initialKey: K,
    started: SharingStarted = SharingStarted.WhileSubscribed(5_000L),
    observe: (K) -> Flow<T>,
    mapFailure: (K, Exception) -> CatalogUiFailure,
): RetainedObservation<K, T> {
    val retryEpoch = MutableStateFlow(0L)
    val retained = MutableStateFlow<ObservationState<K, T>>(ObservationState.Pending(initialKey))

    val state = combine(
        key.distinctUntilChanged(),
        retryEpoch,
    ) { currentKey, epoch -> currentKey to epoch }
        .flatMapLatest { (currentKey, _) ->
            flow {
                when (val current = retained.value.takeIf { it.key == currentKey }) {
                    is ObservationState.Available -> emit(current)
                    is ObservationState.Pending -> emit(current)
                    else -> {
                        val pending = ObservationState.Pending<K>(currentKey)
                        retained.value = pending
                        emit(pending)
                    }
                }

                var emittedThisAttempt = false
                try {
                    observe(currentKey).collect { value ->
                        emittedThisAttempt = true
                        val available = ObservationState.Available(currentKey, value)
                        retained.value = available
                        emit(available)
                    }
                    if (!emittedThisAttempt) {
                        throw ObservationCompletedWithoutValueException()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exception: Exception) {
                    val failure = mapFailure(currentKey, exception)
                    val failed = when (val current = retained.value.takeIf { it.key == currentKey }) {
                        is ObservationState.Available -> current.copy(issue = failure)
                        else -> ObservationState.Unavailable(currentKey, failure)
                    }
                    retained.value = failed
                    emit(failed)
                }
            }
        }
        .stateIn(
            scope = this,
            started = started,
            initialValue = ObservationState.Pending(initialKey),
        )

    return RetainedObservation(state, retryEpoch)
}
