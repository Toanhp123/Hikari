package app.openstory.designsystem.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HikariArtworkFallbackTest {
    @Test
    fun sameStableKeyProducesSameFallback() {
        assertEquals(
            fallbackFor("story-42", "Moonlit Archive"),
            fallbackFor("story-42", "Moonlit Archive"),
        )
    }

    @Test
    fun fallbackMonogramUsesFirstLetterOrQuestionMark() {
        assertEquals("M", fallbackFor("42", " Moonlit Archive ").monogram)
        assertEquals("?", fallbackFor("42", " ").monogram)
    }

    @Test
    fun stableKeyOwnsFallbackColorsWhileTitleOwnsMonogram() {
        val moonlit = fallbackFor("story-42", "Moonlit Archive")
        val solar = fallbackFor("story-42", "Solar Archive")

        assertEquals(moonlit.startColor, solar.startColor)
        assertEquals(moonlit.endColor, solar.endColor)
        assertNotEquals(moonlit.monogram, solar.monogram)
    }

    @Test
    fun differentStableKeysCanProduceDifferentFallbacks() {
        assertNotEquals(
            fallbackFor("story-42", "Moonlit Archive").startColor,
            fallbackFor("story-43", "Moonlit Archive").startColor,
        )
    }
}
