package app.openstory.catalog.domain.asset

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CoverAssetContractsTest {
    @Test
    fun coverRevisionGoldenVectorsAreFrozen() {
        assertEquals(
            "cover:v1:3bc89e103d522e221a479fc1b64753d3e6c49d403aaff32abbc077fa0b82e4c7",
            CoverRevisionV1.local("seed:manga:001", "1").value,
        )
        assertEquals(
            "cover:v1:b55bfd978ff7aded1b47e8f862627af349a4d7d849692684b7903b25106c9049",
            CoverRevisionV1.remoteUri(
                RemoteHttpsUriV1.parseAndNormalize("https://cdn.example.com/covers/1.webp?sig=A%2F"),
            ).value,
        )
        assertEquals(
            "cover:v1:78c1febd35473618f886cb53dc532532a0c2a961cb52f781b688be471bb95be5",
            CoverRevisionV1.remoteStableToken("cover-123-v2").value,
        )
    }

    @Test
    fun localAndRemoteRevisionInputsRemainNarrowAndDomainSeparated() {
        assertNotEquals(
            CoverRevisionV1.local("asset", "1"),
            CoverRevisionV1.local("asset", "2"),
        )
        assertNotEquals(
            CoverRevisionV1.local("asset", "1"),
            CoverRevisionV1.local("other", "1"),
        )
        assertNotEquals(
            CoverRevisionV1.remoteStableToken("https://example.com/a"),
            CoverRevisionV1.remoteUri(RemoteHttpsUriV1.parseAndNormalize("https://example.com/a")),
        )
    }

    @Test
    fun coverRevisionInputsRejectMalformedUtf16BeforeHashing() {
        assertFailsWith<IllegalArgumentException> { CoverRevisionV1.local("bad\uD800", "1") }
        assertFailsWith<IllegalArgumentException> { CoverRevisionV1.remoteStableToken("bad\uDC00") }
    }

    @Test
    fun malformedCoverRevisionIsRejected() {
        listOf(
            "cover:v1:${"A".repeat(64)}",
            "cover:v1:${"0".repeat(63)}",
            "raw-plugin-revision",
        ).forEach { malformed ->
            assertFailsWith<IllegalArgumentException> { CoverRevision(malformed) }
        }
    }

    @Test
    fun stableCacheKeyDependsOnlyOnStoryAndRevisionValues() {
        val story = storyRef("story-1")
        val revision = CoverRevisionV1.local("asset", "1")
        val key = CoverAssetKey(story.storyId, revision)

        assertEquals(
            "hikari:v2:cover-asset:v1:${story.storyId.value}:${revision.value}",
            key.stableCacheKey,
        )
        assertEquals(key.stableCacheKey, CoverAssetKey(story.storyId, revision).stableCacheKey)
        assertNotEquals(key.stableCacheKey, CoverAssetKey(storyRef("story-2").storyId, revision).stableCacheKey)
        assertNotEquals(
            key.stableCacheKey,
            CoverAssetKey(story.storyId, CoverRevisionV1.local("asset", "2")).stableCacheKey,
        )
        assertNotEquals(key.toString(), key.stableCacheKey)
    }

    @Test
    fun remoteUriCanonicalizesOnlySchemeHostAndDefaultPort() {
        assertEquals(
            "https://xn--bcher-kva.example/a/../cover?sig=A%2F&b=2&a=1",
            RemoteHttpsUriV1.parseAndNormalize(
                "HTTPS://B\u00dcCHER.Example.:443/a/../cover?sig=A%2F&b=2&a=1",
            ).value,
        )
        assertEquals(
            "https://example.com/?",
            RemoteHttpsUriV1.parseAndNormalize("https://EXAMPLE.com?").value,
        )
    }

    @Test
    fun remoteUriUsesFrozenIdnaPunycodeVectors() {
        assertEquals(
            "https://xn--maana-pta.com/",
            RemoteHttpsUriV1.parseAndNormalize("https://ma\u00F1ana.com").value,
        )
        assertEquals(
            "https://xn--r8jz45g.xn--zckzah/",
            RemoteHttpsUriV1.parseAndNormalize("https://\u4F8B\u3048.\u30C6\u30B9\u30C8").value,
        )
        assertEquals(
            "https://xn--mnchen-city-thb.de/",
            RemoteHttpsUriV1.parseAndNormalize("https://m\u00FCnchen-city.de").value,
        )
        assertEquals(
            "https://fass.de/",
            RemoteHttpsUriV1.parseAndNormalize("https://fa\u00DF.de").value,
        )
    }

    @Test
    fun remoteUriPreservesSignedQueryIdentityAndPercentEscapeSpelling() {
        val first = RemoteHttpsUriV1.parseAndNormalize("https://example.com/c?b=2&a=%2f")
        val second = RemoteHttpsUriV1.parseAndNormalize("https://example.com/c?a=%2F&b=2")

        assertEquals("https://example.com/c?b=2&a=%2f", first.value)
        assertEquals("https://example.com/c?a=%2F&b=2", second.value)
        assertNotEquals(first, second)
    }

    @Test
    fun remoteRevisionUsesTheLocatorCharacterLimitRatherThanAByteLimit() {
        val prefix = "https://example.com/"
        val raw = prefix + "\u00E9".repeat(4_096 - prefix.length)
        val normalized = RemoteHttpsUriV1.parseAndNormalize(raw)

        assertEquals(4_096, normalized.value.length)
        assertEquals(73, CoverRevisionV1.remoteUri(normalized).value.length)
    }

    @Test
    fun relativeRedirectResolutionIsFullyRevalidated() {
        val base = RemoteHttpsUriV1.parseAndNormalize("https://cdn.example.com/a/b/current.webp?old=1")

        assertEquals(
            "https://cdn.example.com/a/next.webp?sig=B%2F",
            RemoteHttpsUriV1.resolveAndNormalize(base, "../next.webp?sig=B%2F").value,
        )
        assertFailsWith<IllegalArgumentException> {
            RemoteHttpsUriV1.resolveAndNormalize(base, "http://cdn.example.com/unsafe")
        }
        assertEquals(
            "https://cdn.example.com/a/b/current.webp?new=2",
            RemoteHttpsUriV1.resolveAndNormalize(base, "?new=2").value,
        )
        assertEquals(
            "https://cdn.example.com/root/cover.webp",
            RemoteHttpsUriV1.resolveAndNormalize(base, "/root/./old/../cover.webp").value,
        )
        assertEquals(
            "https://other.example.com/cover.webp",
            RemoteHttpsUriV1.resolveAndNormalize(base, "//other.example.com/cover.webp").value,
        )
    }

    @Test
    fun remoteUriRejectsUnsafeOrMalformedAuthority() {
        val invalid = listOf(
            "http://example.com/a",
            "https://user@example.com/a",
            "https://example.com/a#fragment",
            "https://127.0.0.1/a",
            "https://[::1]/a",
            "https://example.com:444/a",
            "https://example.com/%zz",
            "https://example.com/a\\b",
            "https://example.com/a\u0001b",
            "https://example.com/${"x".repeat(4_097)}",
            "https://bad\uD800.example/a",
            "https://\u0301example.com/a",
        )

        invalid.forEach { raw ->
            assertFailsWith<IllegalArgumentException>(raw) {
                RemoteHttpsUriV1.parseAndNormalize(raw)
            }
        }
    }

    @Test
    fun sourceAssetPolicyRequiresCanonicalHostsAndMatchingSource() {
        val source = CatalogSourceKey("source")
        val policy = SourceAssetPolicy(source, setOf("cdn.example.com", "xn--bcher-kva.example"))

        assertEquals(policy, policy.requireFor(source))
        assertFailsWith<IllegalArgumentException> {
            policy.requireFor(CatalogSourceKey("other"))
        }
        listOf("", "CDN.example.com", "https://cdn.example.com", "127.0.0.1").forEach { host ->
            assertFailsWith<IllegalArgumentException>(host) {
                SourceAssetPolicy(source, setOf(host))
            }
        }
    }

    private fun storyRef(sourceStoryId: String): StorySourceRef {
        val source = CatalogSourceKey("source")
        return StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(source, sourceStoryId)),
            catalogSourceKey = source,
            sourceStoryId = sourceStoryId,
        )
    }
}
