package app.openstory.catalog.ui.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RetainedObservationTest {
    @Test
    fun startsPendingWithoutSyntheticValue() = runTest {
        val source = MutableSharedFlow<List<String>>(extraBufferCapacity = 1)
        val holder = holder(observe = { source })

        val initial = assertIs<ObservationState.Pending<String>>(holder.state.value)

        assertEquals("key", initial.key)
    }

    @Test
    fun firstRealEmptyValueIsAvailableNotPending() = runTest {
        val holder = holder(observe = { flowOf(emptyList<String>()) })
        val collected = backgroundScope.launch { holder.state.collect() }

        runCurrent()

        val available = assertIs<ObservationState.Available<String, List<String>>>(holder.state.value)
        assertEquals("key", available.key)
        assertEquals(emptyList(), available.value)
        assertNull(available.issue)
        collected.cancel()
    }

    @Test
    fun failureBeforeFirstValueIsUnavailable() = runTest {
        val holder = holder<String>(observe = { flow { throw IllegalStateException("boom") } })
        val collected = backgroundScope.launch { holder.state.collect() }

        runCurrent()

        val unavailable = assertIs<ObservationState.Unavailable<String>>(holder.state.value)
        assertEquals("observe.failed", unavailable.failure.code)
        collected.cancel()
    }

    @Test
    fun valueThenFailureRetainsValueAndIssue() = runTest {
        val holder = holder<String>(
            observe = {
                flow {
                    emit("value")
                    throw IllegalStateException("boom")
                }
            },
        )
        val collected = backgroundScope.launch { holder.state.collect() }

        runCurrent()

        val available = assertIs<ObservationState.Available<String, String>>(holder.state.value)
        assertEquals("value", available.value)
        assertEquals("observe.failed", available.issue?.code)
        collected.cancel()
    }

    @Test
    fun retryAfterFirstFailureReturnsToPendingThenAvailable() = runTest {
        var attempt = 0
        val recovery = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val holder = holder<String>(
            observe = {
                attempt += 1
                if (attempt == 1) {
                    flow { throw IllegalStateException("boom") }
                } else {
                    recovery
                }
            },
        )
        val collected = backgroundScope.launch { holder.state.collect() }
        runCurrent()
        assertIs<ObservationState.Unavailable<String>>(holder.state.value)

        holder.retry()
        runCurrent()

        assertIs<ObservationState.Pending<String>>(holder.state.value)
        recovery.emit("recovered")
        runCurrent()

        val available = assertIs<ObservationState.Available<String, String>>(holder.state.value)
        assertEquals("recovered", available.value)
        assertNull(available.issue)
        collected.cancel()
    }

    @Test
    fun retryAfterRetainedFailureKeepsValueVisibleUntilSuccess() = runTest {
        var attempt = 0
        val recovery = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val holder = holder<String>(
            observe = {
                attempt += 1
                if (attempt == 1) {
                    flow {
                        emit("old")
                        throw IllegalStateException("boom")
                    }
                } else {
                    recovery
                }
            },
        )
        val collected = backgroundScope.launch { holder.state.collect() }
        runCurrent()
        assertEquals("old", assertIs<ObservationState.Available<String, String>>(holder.state.value).value)

        holder.retry()
        runCurrent()

        val retained = assertIs<ObservationState.Available<String, String>>(holder.state.value)
        assertEquals("old", retained.value)
        assertEquals("observe.failed", retained.issue?.code)

        recovery.emit("new")
        runCurrent()

        val recovered = assertIs<ObservationState.Available<String, String>>(holder.state.value)
        assertEquals("new", recovered.value)
        assertNull(recovered.issue)
        collected.cancel()
    }

    @Test
    fun successfulSameKeyValueClearsOnlyThatObservationIssue() = runTest {
        var firstAttempt = true
        val recovery = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val holder = holder<String>(
            observe = {
                if (firstAttempt) {
                    firstAttempt = false
                    flow {
                        emit("old")
                        throw IllegalStateException("boom")
                    }
                } else {
                    recovery
                }
            },
        )
        val collected = backgroundScope.launch { holder.state.collect() }
        runCurrent()
        assertTrue(holder.state.value.hasRetainedIssue())

        holder.retry()
        runCurrent()
        recovery.emit("new")
        runCurrent()

        assertFalse(holder.state.value.hasIssueOrUnavailable())
        collected.cancel()
    }

    @Test
    fun recoveringOneObservationDoesNotClearAnotherObservationIssue() = runTest {
        var firstAttempt = true
        val recovery = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val first = holder<String>(
            initialKey = "first",
            observe = {
                if (firstAttempt) {
                    firstAttempt = false
                    flow {
                        emit("first-old")
                        throw IllegalStateException("first failed")
                    }
                } else {
                    recovery
                }
            },
        )
        val second = holder<String>(
            initialKey = "second",
            observe = {
                flow {
                    emit("second-old")
                    throw IllegalStateException("second failed")
                }
            },
        )
        val firstCollection = backgroundScope.launch { first.state.collect() }
        val secondCollection = backgroundScope.launch { second.state.collect() }
        runCurrent()
        assertTrue(first.state.value.hasRetainedIssue())
        assertTrue(second.state.value.hasRetainedIssue())

        first.retry()
        runCurrent()
        recovery.emit("first-new")
        runCurrent()

        assertFalse(first.state.value.hasIssueOrUnavailable())
        assertTrue(second.state.value.hasRetainedIssue())
        firstCollection.cancel()
        secondCollection.cancel()
    }

    @Test
    fun normalCompletionBeforeFirstValueBecomesUnavailable() = runTest {
        val holder = holder<String>(observe = { emptyFlow() })
        val collected = backgroundScope.launch { holder.state.collect() }

        runCurrent()

        assertIs<ObservationState.Unavailable<String>>(holder.state.value)
        collected.cancel()
    }

    @Test
    fun normalCompletionAfterValueKeepsAvailableWithoutIssue() = runTest {
        val holder = holder<String>(observe = { flowOf("value") })
        val collected = backgroundScope.launch { holder.state.collect() }

        runCurrent()

        val available = assertIs<ObservationState.Available<String, String>>(holder.state.value)
        assertEquals("value", available.value)
        assertNull(available.issue)
        collected.cancel()
    }

    @Test
    fun retryThatCompletesWithoutValueRetainsSameKeyValueAndAddsIssue() = runTest {
        var attempt = 0
        val holder = holder<String>(
            observe = {
                attempt += 1
                if (attempt == 1) flowOf("value") else emptyFlow()
            },
        )
        val collected = backgroundScope.launch { holder.state.collect() }
        runCurrent()
        assertNull(assertIs<ObservationState.Available<String, String>>(holder.state.value).issue)

        holder.retry()
        runCurrent()

        val retained = assertIs<ObservationState.Available<String, String>>(holder.state.value)
        assertEquals("value", retained.value)
        assertEquals("observe.failed", retained.issue?.code)
        collected.cancel()
    }

    @Test
    fun keyChangeDropsOldValueAndStartsPendingForNewKey() = runTest {
        val key = MutableStateFlow("A")
        val sources = mapOf(
            "A" to MutableSharedFlow<String>(extraBufferCapacity = 1),
            "B" to MutableSharedFlow<String>(extraBufferCapacity = 1),
        )
        val holder = backgroundScope.retainedObservation(
            key = key,
            initialKey = "A",
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            observe = { currentKey -> sources.getValue(currentKey) },
            mapFailure = { _, _ -> failure() },
        )
        val states = mutableListOf<ObservationState<String, String>>()
        val collected = backgroundScope.launch { holder.state.collect { states += it } }
        runCurrent()

        sources.getValue("A").emit("A-value")
        runCurrent()
        assertEquals("A-value", assertIs<ObservationState.Available<String, String>>(holder.state.value).value)

        key.value = "B"
        runCurrent()

        val pending = assertIs<ObservationState.Pending<String>>(holder.state.value)
        assertEquals("B", pending.key)
        assertFalse(
            states.any { state ->
                state is ObservationState.Available && state.key == "B" && state.value == "A-value"
            },
        )
        collected.cancel()
    }

    @Test
    fun staleAvailableStateNormalizesToPendingForNewExpectedKey() {
        val stale: ObservationState<String, String> =
            ObservationState.Available(key = "A", value = "A-value")

        val normalized = stale.forExpectedKey("B")

        val pending = assertIs<ObservationState.Pending<String>>(normalized)
        assertEquals("B", pending.key)
    }

    @Test
    fun cancellationPropagates() = runTest {
        var observationCollectionCount = 0
        var mappedFailureCount = 0
        val holder = backgroundScope.retainedObservation<String, String>(
            key = flowOf("key"),
            initialKey = "key",
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            observe = {
                flow {
                    observationCollectionCount += 1
                    throw CancellationException("cancel")
                }
            },
            mapFailure = { _, _ ->
                mappedFailureCount += 1
                failure()
            },
        )
        val collected = backgroundScope.launch { holder.state.collect() }

        runCurrent()

        assertEquals(1, observationCollectionCount)
        assertEquals(0, mappedFailureCount)
        assertIs<ObservationState.Pending<String>>(holder.state.value)
        collected.cancel()
    }

    @Test
    fun upstreamRestartForSameKeyDoesNotEmitVisiblePendingAfterAvailable() = runTest {
        var collections = 0
        val secondCollectionGate = CompletableDeferred<Unit>()
        val holder = holder<String>(
            observe = {
                flow {
                    collections += 1
                    if (collections == 1) {
                        emit("value")
                    } else {
                        secondCollectionGate.await()
                        emit("new-value")
                    }
                    awaitCancellation()
                }
            },
        )
        val firstCollector = backgroundScope.launch { holder.state.collect() }
        runCurrent()
        assertEquals("value", assertIs<ObservationState.Available<String, String>>(holder.state.value).value)

        firstCollector.cancel()
        runCurrent()

        val restartedStates = mutableListOf<ObservationState<String, String>>()
        val secondCollector = backgroundScope.launch { holder.state.collect { restartedStates += it } }
        runCurrent()

        assertTrue(collections >= 2)
        assertEquals("value", assertIs<ObservationState.Available<String, String>>(holder.state.value).value)
        assertFalse(restartedStates.any { it is ObservationState.Pending })

        secondCollectionGate.complete(Unit)
        runCurrent()
        assertEquals("new-value", assertIs<ObservationState.Available<String, String>>(holder.state.value).value)
        secondCollector.cancel()
    }

    private fun failure(): CatalogUiFailure =
        CatalogUiFailure(code = "observe.failed", retryable = true)

    private fun <T> kotlinx.coroutines.test.TestScope.holder(
        initialKey: String = "key",
        observe: (String) -> Flow<T>,
    ): RetainedObservation<String, T> =
        backgroundScope.retainedObservation(
            key = flowOf(initialKey),
            initialKey = initialKey,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            observe = observe,
            mapFailure = { _, _ -> failure() },
        )
}
