package app.openstory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import app.openstory.designsystem.glass.HikariBackdropHost
import app.openstory.designsystem.navigation.HikariFloatingNavigation
import app.openstory.designsystem.navigation.HikariNavigationItem
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.navigation.AppRoute
import app.openstory.navigation.TopLevelDestination
import app.openstory.navigation.shouldShowFloatingNavigation
import app.openstory.navigation.topLevelDestinations

@Composable
fun HikariAppShell(
    currentRoute: AppRoute?,
    onTopLevelSelected: (TopLevelDestination) -> Unit,
    onUtilityRequested: () -> Unit,
    utilityFocusRequester: FocusRequester? = null,
    utilityNextFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    content: @Composable HikariAppShellScope.(PaddingValues) -> Unit,
) {
    val contentPadding = hikariAppContentPadding(currentRoute)
    val showFloatingNavigation = shouldShowFloatingNavigation(currentRoute)
    val shellScope = HikariAppShellScope(
        onUtilityRequested = onUtilityRequested,
        utilityFocusRequester = utilityFocusRequester,
        utilityNextFocusRequester = utilityNextFocusRequester,
    )
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HikariBackdropHost(
            modifier = Modifier.fillMaxSize(),
            captureBackdrop = showFloatingNavigation,
            background = { shellScope.content(contentPadding) },
        ) {
            if (showFloatingNavigation) {
                val selectedRoute = requireNotNull(currentRoute)
                Box(Modifier.fillMaxSize()) {
                    HikariFloatingNavigation(
                        items = navigationItems,
                        selectedKey = selectedRoute.key,
                        onSelected = { key ->
                            topLevelDestinations.firstOrNull { it.route.key == key }
                                ?.let(onTopLevelSelected)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(
                                horizontal = MaterialTheme.hikariSpacing.space20,
                                vertical = MaterialTheme.hikariSpacing.space12,
                            ),
                        backdropScope = this@HikariBackdropHost,
                    )
                }
            }
        }
    }
}

class HikariAppShellScope internal constructor(
    val onUtilityRequested: () -> Unit,
    val utilityFocusRequester: FocusRequester?,
    val utilityNextFocusRequester: FocusRequester?,
)

@Composable
private fun hikariAppContentPadding(route: AppRoute?): PaddingValues {
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val bottomChrome = if (shouldShowFloatingNavigation(route)) {
        MaterialTheme.hikariDimensions.floatingNavigationClearance
    } else {
        MaterialTheme.hikariDimensions.zero
    }
    return PaddingValues(
        start = safeDrawing.calculateStartPadding(layoutDirection),
        top = safeDrawing.calculateTopPadding(),
        end = safeDrawing.calculateEndPadding(layoutDirection),
        bottom = safeDrawing.calculateBottomPadding() + bottomChrome,
    )
}

private val AppRoute.key: String
    get() = when (this) {
        AppRoute.Discover -> "discover"
        AppRoute.Home -> "home"
        AppRoute.Library -> "library"
        else -> error("Focused routes do not have a floating navigation key")
    }

private val navigationItems = topLevelDestinations.map { destination ->
    HikariNavigationItem(
        key = destination.route.key,
        label = destination.label,
        icon = destination.navigationIcon,
    )
}
