package app.openstory.reader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import app.openstory.designsystem.theme.hikariTypography

@Composable
internal fun rememberReaderTextStyles(fontScale: Float): ReaderTextStyles {
    val typography = MaterialTheme.typography
    val semanticTypography = MaterialTheme.hikariTypography
    return remember(fontScale, typography, semanticTypography) {
        ReaderTextStyles(
            title = typography.headlineMedium.copy(
                fontSize = typography.headlineMedium.fontSize * fontScale,
            ),
            paragraph = semanticTypography.readerBody.scaled(fontScale, scaleLineHeight = true),
            note = typography.bodyLarge.scaled(fontScale, scaleLineHeight = true),
            headingOne = typography.headlineLarge.scaled(fontScale),
            headingTwo = typography.headlineMedium.scaled(fontScale),
            headingThree = typography.headlineSmall.scaled(fontScale),
            headingOther = typography.titleLarge.scaled(fontScale),
        )
    }
}

internal data class ReaderTextStyles(
    val title: TextStyle,
    val paragraph: TextStyle,
    val note: TextStyle,
    val headingOne: TextStyle,
    val headingTwo: TextStyle,
    val headingThree: TextStyle,
    val headingOther: TextStyle,
) {
    fun heading(level: Int): TextStyle = when (level) {
        1 -> headingOne
        2 -> headingTwo
        HEADING_LEVEL_THREE -> headingThree
        else -> headingOther
    }
}

private fun TextStyle.scaled(fontScale: Float, scaleLineHeight: Boolean = false): TextStyle = copy(
    fontSize = fontSize * fontScale,
    lineHeight = if (scaleLineHeight) lineHeight * fontScale else lineHeight,
)

private const val HEADING_LEVEL_THREE = 3
