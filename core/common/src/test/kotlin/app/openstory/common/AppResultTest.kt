package app.openstory.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AppResultTest {
@Test
fun appErrorExposesOnlySafeDiagnosticTokens() {
    val diagnostic = AppError.Diagnostic.of(
        "operation" to "chapter.fetch",
        "component" to "plugin.fixture",
    )

    val error = AppError.Network(
        code = "network.http_401",
        retryable = false,
        diagnostic = diagnostic,
    )

    val rendered = error.toString()

    assertContains(rendered, "network.http_401")
    assertContains(rendered, "chapter.fetch")
    assertContains(rendered, "plugin.fixture")

    listOf(
        "Cookie: token=secret",
        "https://a.example/x?q=private",
        "database exploded with password",
        "<p>private chapter body</p>",
    ).forEach { unsafeDetail ->
        assertFalse(unsafeDetail in rendered)

        assertFailsWith<IllegalArgumentException> {
            AppError.Diagnostic.of(
                "detail" to unsafeDetail,
            )
        }
    }
}

@Test
fun fakeClockAdvancesWithoutSleeping() {
    val clock = FakeClock(
        initialEpochMillis = 1_000L,
    )

    assertEquals(
        1_000L,
        clock.nowEpochMillis(),
    )

    clock.advanceBy(250L)

    assertEquals(
        1_250L,
        clock.nowEpochMillis(),
    )
}

@Test
fun stableIdRejectsBlankAndWhitespaceContainingValues() {
    assertEquals(
        "story_123",
        StableId.requireValid("story_123"),
    )

    assertFailsWith<IllegalArgumentException> {
        StableId.requireValid("")
    }

    assertFailsWith<IllegalArgumentException> {
        StableId.requireValid(" ")
    }

    assertFailsWith<IllegalArgumentException> {
        StableId.requireValid("story 123")
    }
}

    @Test
    fun mapPreservesTypedFailure() {
        val error = AppError.Network(
            code = "network.timeout",
            retryable = true,
        )
        val result: AppResult<Int> = AppResult.Failure(error)

        assertEquals(
            AppResult.Failure(error),
            result.map { it * 2 },
        )
    }
}
