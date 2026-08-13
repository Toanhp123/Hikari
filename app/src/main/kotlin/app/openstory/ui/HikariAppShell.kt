package app.openstory.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.glass.HikariBackdropHost
import app.openstory.designsystem.glass.HikariGlassSurface
import app.openstory.designsystem.navigation.HikariFloatingNavigation
import app.openstory.designsystem.navigation.HikariNavigationItem
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
    content: @Composable () -> Unit,
) {
    HikariBackdropHost(
        modifier = modifier.fillMaxSize(),
        background = { content() },
    ) {
        if (shouldShowFloatingNavigation(currentRoute)) {
            val selectedRoute = requireNotNull(currentRoute)
            Box(Modifier.fillMaxSize()) {
                UtilityButton(
                    this@HikariBackdropHost,
                    onUtilityRequested,
                    utilityFocusRequester,
                    utilityNextFocusRequester,
                    Modifier.align(Alignment.TopEnd),
                )
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
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    backdropScope = this@HikariBackdropHost,
                )
            }
        }
    }
}

@Composable
private fun UtilityButton(
    backdropScope: app.openstory.designsystem.glass.HikariBackdropScope,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    nextFocusRequester: FocusRequester?,
    modifier: Modifier,
) {
    HikariGlassSurface(
        backdropScope = backdropScope,
        modifier = modifier
            .statusBarsPadding()
            .padding(16.dp)
            .size(48.dp)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .then(
                nextFocusRequester?.let { nextRequester ->
                    Modifier.focusProperties { next = nextRequester; down = nextRequester }
                } ?: Modifier,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "Open quick access"
                traversalIndex = 1f
            },
        shape = CircleShape,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("HK", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

fun Modifier.hikariTopLevelContentPadding(): Modifier = this
    .statusBarsPadding()
    .navigationBarsPadding()
    .padding(top = 72.dp, bottom = 92.dp)

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
