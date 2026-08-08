package app.openstory.plugin.host.js

import app.openstory.common.AppError
import app.openstory.common.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JsBridgeProtocolTest {
    @Test
    fun oversizedBridgeMessageFailsWithoutIncludingRawInput() {
        val source = """
            {"id":"call-1","method":"http.execute","params":{"url":"https://private.example/?token=secret"}}
        """.trimIndent()

        val result = JsBridgeCodec(
            JsRuntimeLimits(maxBridgeMessageBytes = 32),
        ).decodeRequest(source)

        val failure = assertIs<AppResult.Failure>(result)
        val error = assertIs<AppError.Plugin>(failure.error)
        assertEquals("plugin.bridge_message_too_large", error.code)
        assertEquals(AppError.Diagnostic.empty(), error.diagnostic)
    }

    @Test
    fun unknownRequestFieldsFailClosed() {
        val source = """{"id":"call-1","method":"http.execute","params":{},"context":"leak"}"""

        val result = JsBridgeCodec().decodeRequest(source)

        val failure = assertIs<AppResult.Failure>(result)
        val error = assertIs<AppError.Plugin>(failure.error)
        assertEquals("plugin.bridge_message_invalid", error.code)
    }
}
