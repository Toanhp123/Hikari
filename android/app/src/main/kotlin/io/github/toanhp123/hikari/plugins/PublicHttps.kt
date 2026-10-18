package io.github.toanhp123.hikari.plugins

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Fail closed on special-use networks. Applied to literal hosts AND socket DNS. */
internal object PublicHttps {
    fun isPublic(bytes: ByteArray): Boolean {
        val b = bytes.map { it.toInt() and 255 }
        if (b.size == 4) {
            val a = b[0]; val c = b[1]
            return !(a == 0 || a == 10 || a == 127 || a >= 224 ||
                (a == 100 && c in 64..127) || (a == 169 && c == 254) ||
                (a == 172 && c in 16..31) || (a == 192 && (c == 0 || c == 168 || c == 88 && b[2] == 99)) ||
                (a == 198 && (c in 18..19 || c == 51 && b[2] == 100)) ||
                (a == 203 && c == 0 && b[2] == 113))
        }
        if (b.size != 16) return false
        // Only global unicast 2000::/3; reject transition/documentation/reserved blocks.
        if (b[0] and 0xe0 != 0x20) return false
        if (b[0] == 0x20 && b[1] == 0x02) return false // 6to4 embeds IPv4
        if (b[0] == 0x20 && b[1] == 0x01 && b[2] < 2) return false // Teredo, benchmarking, ORCHID
        if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == 0xb8) return false
        if (b[0] == 0x3f && b[1] == 0xff && b[2] < 0x10) return false // documentation 3fff::/20
        return true
    }
    fun url(raw: String): HttpUrl {
        require(raw.length in 1..4096 && raw.none { it <= ' ' || it == '\\' }) { "Invalid HTTPS URL" }
        var decoded = raw
        repeat(4) {
            require(!Regex("(^|/)\\.{1,2}(/|[?#]|$)").containsMatchIn(decoded) &&
                !Regex("%2f|%5c", RegexOption.IGNORE_CASE).containsMatchIn(decoded)) { "Unsafe URL path" }
            val next = Regex("%([0-9a-fA-F]{2})").replace(decoded) { it.groupValues[1].toInt(16).toChar().toString() }
            if (next == decoded) return@repeat
            decoded = next
        }
        require(!decoded.contains('%')) { "Nested or invalid URL encoding" }
        val url = raw.toHttpUrl()
        require(url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null)
        val host = url.host.lowercase().trimEnd('.')
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local"))
        if (host.contains(':') || host.matches(Regex("[0-9.]+"))) {
            require(isPublic(InetAddress.getByName(host).address)) { "Private literal address denied" }
        } else {
            require(host.contains('.') && host.split('.').all { it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) } &&
                !host.substringAfterLast('.').matches(Regex("[0-9]+|0x[0-9a-f]+")))
        }
        return url
    }
    fun redirect(from: HttpUrl, location: String, count: Int): HttpUrl {
        require(count <= 5) { "Too many redirects" }
        return url(requireNotNull(from.resolve(location)).toString())
    }
    val dns = Dns { hostname ->
        Dns.SYSTEM.lookup(hostname).also { addresses ->
            require(addresses.isNotEmpty() && addresses.all { isPublic(it.address) }) { "Private DNS address denied" }
        }
    }
    fun client(seconds: Long): OkHttpClient = OkHttpClient.Builder().dns(dns).proxy(java.net.Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(seconds, TimeUnit.SECONDS).build()
}
