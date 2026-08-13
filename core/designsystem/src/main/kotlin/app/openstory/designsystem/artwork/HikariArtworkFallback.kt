package app.openstory.designsystem.artwork

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Immutable
data class HikariArtworkFallback(
    val startColor: Color,
    val endColor: Color,
    val monogram: String,
)

private const val UNSIGNED_BYTE_MASK = 0xFF

private val HikariArtworkPalette = listOf(
    Color(0xFF425B76),
    Color(0xFF6B536F),
    Color(0xFF386A73),
    Color(0xFF765A45),
    Color(0xFF59664A),
    Color(0xFF73515A),
    Color(0xFF4F5E87),
    Color(0xFF7A6040),
)

fun fallbackFor(stableKey: String, title: String): HikariArtworkFallback {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(stableKey.toByteArray(StandardCharsets.UTF_8))
    val startIndex = digest[0].toUnsignedInt() % HikariArtworkPalette.size
    val rawEndIndex = digest[1].toUnsignedInt() % HikariArtworkPalette.size
    val endIndex = if (rawEndIndex == startIndex) {
        (rawEndIndex + 1 + (digest[2].toUnsignedInt() % (HikariArtworkPalette.size - 1))) %
            HikariArtworkPalette.size
    } else {
        rawEndIndex
    }

    return HikariArtworkFallback(
        startColor = HikariArtworkPalette[startIndex],
        endColor = HikariArtworkPalette[endIndex],
        monogram = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
    )
}

private fun Byte.toUnsignedInt(): Int = toInt() and UNSIGNED_BYTE_MASK
