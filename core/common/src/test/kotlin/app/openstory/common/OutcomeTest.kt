package app.openstory.common

import kotlin.test.Test
import kotlin.test.assertEquals

class OutcomeTest {
    @Test
    fun mapTransformsSuccess() {
        assertEquals(Outcome.Success(4), Outcome.Success(2).map { it * 2 })
    }

    @Test
    fun mapPreservesFailure() {
        val failure: Outcome<Int, String> = Outcome.Failure("boom")
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun mapErrorTransformsOnlyFailure() {
        val failure: Outcome<Int, String> = Outcome.Failure("boom")
        assertEquals(Outcome.Failure(4), failure.mapError(String::length))
    }
}
