package app.openstory.artwork.policy

import app.openstory.artwork.request.ArtworkAuthorityKey
import java.net.IDN
import java.util.Locale

class ArtworkPolicy(
    val authority: ArtworkAuthorityKey,
    allowedHttpsHosts: Set<String>,
) {
    val allowedHttpsHosts: Set<String> = allowedHttpsHosts.toSet()

    init {
        this.allowedHttpsHosts.forEach { host ->
            require(canonicalHost(host) == host)
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is ArtworkPolicy &&
            authority == other.authority &&
            allowedHttpsHosts == other.allowedHttpsHosts

    override fun hashCode(): Int = 31 * authority.hashCode() + allowedHttpsHosts.hashCode()

    override fun toString(): String =
        "ArtworkPolicy(authority=$authority, allowedHttpsHosts=$allowedHttpsHosts)"
}

internal fun canonicalHost(host: String): String {
    require(host.isNotBlank() && ':' !in host && '[' !in host && ']' !in host)
    val canonical = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
        .lowercase(Locale.ROOT)
        .removeSuffix(".")
    require(canonical.isNotBlank() && canonical.length <= MAX_DNS_HOST_CHARS)
    require(!IPV4_PATTERN.matches(canonical))
    canonical.split('.').forEach { label ->
        require(label.isNotEmpty() && label.length <= MAX_DNS_LABEL_CHARS)
    }
    return canonical
}

private val IPV4_PATTERN = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
private const val MAX_DNS_HOST_CHARS = 253
private const val MAX_DNS_LABEL_CHARS = 63
