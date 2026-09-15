package app.openstory.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object HikariBreakpoints {
    val Wide = 600.dp

    fun isWide(width: Dp): Boolean = width >= Wide

    fun screenHorizontalInset(width: Dp): Dp = if (isWide(width)) {
        HikariDimensions.WideScreenInset
    } else {
        HikariDimensions.CompactScreenInset
    }
}
