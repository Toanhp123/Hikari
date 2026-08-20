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
    fun flatMapTransformsSuccess() {
        val outcome: Outcome<Int, String> = Outcome.Success(2)
        assertEquals(Outcome.Success(6), outcome.flatMap { Outcome.Success(it * 3) })
    }

    @Test
    fun flatMapPreservesFailure() {
        val failure: Outcome<Int, String> = Outcome.Failure("boom")
        assertEquals(failure, failure.flatMap { Outcome.Success(it * 3) })
    }

    @Test
    fun mapErrorTransformsOnlyFailure() {
        val failure: Outcome<Int, String> = Outcome.Failure("boom")
        assertEquals(Outcome.Failure(4), failure.mapError(String::length))
    }

    @Test
    fun mapErrorPreservesSuccess() {
        val success: Outcome<Int, String> = Outcome.Success(2)
        val expected: Outcome<Int, Int> = Outcome.Success(2)
        assertEquals(expected, success.mapError(String::length))
    }

    @Test
    fun getOrNullReturnsSuccessValue() {
        val success: Outcome<Int, String> = Outcome.Success(2)
        assertEquals(2, success.getOrNull())
    }

    @Test
    fun getOrNullReturnsNullForFailure() {
        val failure: Outcome<Int, String> = Outcome.Failure("boom")
        assertEquals(null, failure.getOrNull())
    }

    @Test
    fun foldTransformsSuccessIntoTargetType() {
        val outcome: Outcome<Int, String> = Outcome.Success(2)
        assertEquals(
            "value=2",
            outcome.fold(
                onSuccess = { "value=$it" },
                onFailure = { "error=$it" },
            ),
        )
    }

    @Test
    fun foldTransformsFailureIntoTargetType() {
        val outcome: Outcome<Int, String> = Outcome.Failure("boom")
        assertEquals(
            "error=boom",
            outcome.fold(
                onSuccess = { "value=$it" },
                onFailure = { "error=$it" },
            ),
        )
    }
}
