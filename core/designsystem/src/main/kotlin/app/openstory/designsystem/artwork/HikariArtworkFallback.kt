package app.openstory.designsystem.artwork

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.openstory.designsystem.theme.HikariArtworkFallbackPalette
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Immutable
data class HikariArtworkFallback(
    val startColor: Color,
    val endColor: Color,
    val monogram: String,
)

private const val UNSIGNED_BYTE_MASK = 0xFF

fun fallbackFor(stableKey: String, title: String): HikariArtworkFallback {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(stableKey.toByteArray(StandardCharsets.UTF_8))
    val startIndex = digest[0].toUnsignedInt() % HikariArtworkFallbackPalette.size
    val rawEndIndex = digest[1].toUnsignedInt() % HikariArtworkFallbackPalette.size
    val endIndex = if (rawEndIndex == startIndex) {
        (rawEndIndex + 1 + (digest[2].toUnsignedInt() % (HikariArtworkFallbackPalette.size - 1))) %
            HikariArtworkFallbackPalette.size
    } else {
        rawEndIndex
    }

    return HikariArtworkFallback(
        startColor = HikariArtworkFallbackPalette[startIndex],
        endColor = HikariArtworkFallbackPalette[endIndex],
        monogram = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
    )
}

private fun Byte.toUnsignedInt(): Int = toInt() and UNSIGNED_BYTE_MASK
