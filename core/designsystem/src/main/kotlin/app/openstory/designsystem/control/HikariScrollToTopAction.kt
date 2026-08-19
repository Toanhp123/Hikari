package app.openstory.designsystem.control

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.designsystem.icon.HikariUpGlyph
import app.openstory.designsystem.theme.hikariDimensions

@Composable
fun HikariScrollToTopAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HikariIconAction(
        onClick = onClick,
        contentDescription = "Back to top",
        modifier = modifier.testTag("hikari-scroll-to-top"),
        style = HikariIconActionStyle.ACCENTED_SURFACE,
    ) {
        HikariUpGlyph(Modifier.size(MaterialTheme.hikariDimensions.iconStandard))
    }
}
