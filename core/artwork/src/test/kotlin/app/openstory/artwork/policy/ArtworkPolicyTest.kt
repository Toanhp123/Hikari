package app.openstory.artwork.policy

import app.openstory.artwork.request.ArtworkAuthorityKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArtworkPolicyTest {
    @Test
    fun artworkPolicyRequiresCanonicalDnsHosts() {
        listOf("images.example.", "127.0.0.1", "bad_host.example").forEach { host ->
            assertThrows(IllegalArgumentException::class.java) {
                ArtworkPolicy(AUTHORITY, setOf(host))
            }
        }
    }

    @Test
    fun artworkPolicySnapshotsCallerOwnedHostSet() {
        val hosts = mutableSetOf("images.example")
        val policy = ArtworkPolicy(AUTHORITY, hosts)

        hosts += "later.example"

        assertEquals(setOf("images.example"), policy.allowedHttpsHosts)
        assertEquals(ArtworkPolicy(AUTHORITY, setOf("images.example")), policy)
    }

    private companion object {
        val AUTHORITY = ArtworkAuthorityKey("catalog:mangaupdates")
    }
}
