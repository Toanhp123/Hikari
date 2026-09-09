package app.openstory.catalog.domain.identity

import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SourceStoryIdV1Test {
    @Test
    fun exactCaseSensitiveUtf8InputsProduceFrozenId() {
        val vectors = listOf(
            Triple(
                "org.openstory.catalog.mangaupdates",
                "12345",
                "source-story:v1:fbae52bb502eba96312109575f89437f1c1c7a4fa07f7eb69f7ada51e41f999c",
            ),
            Triple(
                "source",
                "Story-01",
                "source-story:v1:b5296442f511e0e7a9a8699e1edb1c3d97340a8cb972c4182a886ef2de80fa55",
            ),
            Triple(
                "S",
                "\u4F5C\u54C1-\u00C4",
                "source-story:v1:87a4154a9ed813f805e3a04ac0f491aa71f44bb95e58449fcfdf43a83797bb50",
            ),
        )

        vectors.forEach { (sourceKey, sourceStoryId, expected) ->
            assertEquals(expected, derive(sourceKey, sourceStoryId).value)
        }
    }

    @Test
    fun mutableMetadataCannotChangeStoryId() {
        val source = SourceStoryKey(CatalogSourceKey("source"), "opaque-7")
        val beforeMetadataChange = SourceStoryIdV1.derive(source)
        val changedTitle = "A completely different title"
        val changedRating = 9.8

        assertEquals("A completely different title", changedTitle)
        assertEquals(9.8, changedRating)
        assertEquals(beforeMetadataChange, SourceStoryIdV1.derive(source))
    }

    @Test
    fun caseOrWhitespaceChangeInOpaqueSourceIdChangesIdentity() {
        val exact = derive("source", "Story-01")

        assertNotEquals(exact, derive("source", "story-01"))
        assertNotEquals(exact, derive("source", " Story-01"))
        assertNotEquals(exact, derive("source", "Story-01 "))
    }

    @Test
    fun overBoundIdentifiersAreRejectedBeforeHashing() {
        CatalogSourceKey("\u00E9".repeat(64))
        derive("source", "\u00E9".repeat(256))

        assertFailsWith<IllegalArgumentException> {
            CatalogSourceKey("\u00E9".repeat(65))
        }
        assertFailsWith<IllegalArgumentException> {
            derive("source", "\u00E9".repeat(257))
        }
    }

    @Test
    fun canonicallyEquivalentUnicodeRemainsOpaqueIdentity() {
        assertNotEquals(
            derive("source", "\u00E9"),
            derive("source", "e\u0301"),
        )
    }

    @Test
    fun unpairedHighSurrogateIsRejected() {
        assertFailsWith<IllegalArgumentException> { derive("source", "bad\uD800") }
    }

    @Test
    fun unpairedLowSurrogateIsRejected() {
        assertFailsWith<IllegalArgumentException> { derive("source", "bad\uDC00") }
    }

    @Test
    fun storySourceRefRejectsMismatchedDerivedStoryId() {
        assertFailsWith<IllegalArgumentException> {
            StorySourceRef(
                storyId = StoryId("source-story:v1:${"0".repeat(64)}"),
                catalogSourceKey = CatalogSourceKey("source"),
                sourceStoryId = "Story-01",
            )
        }
    }

    private fun derive(sourceKey: String, sourceStoryId: String): StoryId =
        SourceStoryIdV1.derive(SourceStoryKey(CatalogSourceKey(sourceKey), sourceStoryId))
}
