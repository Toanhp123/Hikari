package app.openstory.ui

import androidx.compose.ui.graphics.vector.ImageVector
import app.openstory.designsystem.icon.HikariNavigationGlyphs
import app.openstory.navigation.TopLevelDestination

internal val TopLevelDestination.navigationIcon: ImageVector
    get() = when (this) {
        TopLevelDestination.Discover -> HikariNavigationGlyphs.discover
        TopLevelDestination.Home -> HikariNavigationGlyphs.home
        TopLevelDestination.Library -> HikariNavigationGlyphs.library
    }
