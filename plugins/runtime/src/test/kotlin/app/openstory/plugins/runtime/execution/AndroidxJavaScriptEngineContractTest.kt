package app.openstory.plugins.runtime.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidxJavaScriptEngineContractTest {
    @Test
    fun invocationEnvelopePreservesDomainFailureCode() {
        val failure = assertFailsWith<JavaScriptExecutionFailure> {
            decodeInvocationResult("{\"errorCode\":\"plugin.http_domain_denied\"}")
        }

        assertEquals("plugin.http_domain_denied", failure.code)
        assertFalse(failure.retryable)
    }

    @Test
    fun invocationEnvelopePreservesRetryableFailure() {
        val failure = assertFailsWith<JavaScriptExecutionFailure> {
            decodeInvocationResult(
                "{\"errorCode\":\"plugin.http_request_failed\",\"retryable\":true}",
            )
        }

        assertEquals("plugin.http_request_failed", failure.code)
        assertTrue(failure.retryable)
    }

    @Test
    fun invocationEnvelopeReturnsRawProtocolPayload() {
        assertEquals(
            "{\"sections\":[]}",
            decodeInvocationResult("{\"payload\":\"{\\\"sections\\\":[]}\"}"),
        )
    }
}
