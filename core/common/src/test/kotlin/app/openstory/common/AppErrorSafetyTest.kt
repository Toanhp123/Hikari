package app.openstory.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppErrorSafetyTest {

    @Test
    fun errorCodesAcceptOnlySafeMachineReadableTokens() {
        val factories: List<(String) -> AppError> = listOf(
            { code ->
                AppError.Network(
                    code = code,
                    retryable = true,
                )
            },
            { code ->
                AppError.Validation(
                    code = code,
                )
            },
            { code ->
                AppError.Storage(
                    code = code,
                    retryable = true,
                )
            },
            { code ->
                AppError.Plugin(
                    code = code,
                    retryable = false,
                )
            },
        )

        factories.forEach { createError ->
            assertEquals(
                "network.http_401",
                createError("network.http_401").code,
            )
        }

        val unsafeCodes = listOf(
            "",
            " ",
            "network timeout",
            "Cookie: token=secret",
            "https://a.example/chapter?q=private",
            "database exploded with password",
            "<p>private chapter body</p>",
        )

        factories.forEach { createError ->
            unsafeCodes.forEach { unsafeCode ->
                assertFailsWith<IllegalArgumentException>(
                    message = "Unsafe error code was accepted: $unsafeCode",
                ) {
                    createError(unsafeCode)
                }
            }
        }
    }
}
