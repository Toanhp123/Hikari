package app.openstory.catalog.runtime.concurrency

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogMutationGateTest {
    @Test
    fun mutationsCompleteInExclusiveEntryOrder() = runBlocking {
        val gate = CatalogMutationGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val first = async {
            gate.withMutation {
                order += "first-enter"
                firstEntered.complete(Unit)
                releaseFirst.await()
                order += "first-exit"
            }
        }
        firstEntered.await()
        val second = async { gate.withMutation { order += "second" } }

        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("first-enter", "first-exit", "second"), order)
    }
}
