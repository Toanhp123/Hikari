package io.github.toanhp123.hikari.extensions.lnreader

import okhttp3.Request
import org.json.JSONObject

/** A transport policy, not a list of provider-specific header names.
 *
 * Source scripts may request standard and extension HTTP headers (including
 * Next.js RSC), while authority/framing/connection metadata stays owned by
 * OkHttp. Never turn this into a blanket 'allow all headers' rule.
 */
internal object LnReaderHttpHeaders {
    private val token = Regex("^[!#\\$%&'*+.^_`|~A-Za-z0-9-]{1,128}$")
    private val managed = setOf(
        "host", "content-length", "transfer-encoding", "connection", "keep-alive",
        "proxy-connection", "upgrade", "te", "trailer", "accept-encoding",
    )
    fun apply(builder: Request.Builder, headers: JSONObject?, multipart: Boolean) {
        if (headers == null) return
        require(headers.length() <= 64)
        for (name in headers.keys()) {
            val lower = name.lowercase(java.util.Locale.ROOT)
            require(token.matches(name) && lower !in managed && !lower.startsWith("proxy-"))
            require(!multipart || lower != "content-type")
            val value = headers.get(name) as? String ?: error("Invalid header value")
            require(value.length <= 4096 && value.none { it == '\r' || it == '\n' || it == '\u0000' })
            builder.header(name, value)
        }
    }
}
