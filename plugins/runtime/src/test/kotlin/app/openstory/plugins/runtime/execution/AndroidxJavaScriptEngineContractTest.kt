package app.openstory.plugins.runtime.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidxJavaScriptEngineContractTest {
    @Test
    fun invocationEnvelopePreservesDomainFailureCode() {
        val failure = assertFailsWith<JavaScriptExecutionFailure> {
            decodeInvocationResult("{\"errorCode\":\"plugin.http_domain_denied\"}")
        }

        assertEquals("plugin.http_domain_denied", failure.code)
    }

    @Test
    fun invocationEnvelopeReturnsRawProtocolPayload() {
        assertEquals(
            "{\"sections\":[]}",
            decodeInvocationResult("{\"payload\":\"{\\\"sections\\\":[]}\"}"),
        )
    }
}
