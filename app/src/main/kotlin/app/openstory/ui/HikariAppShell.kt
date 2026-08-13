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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
                HikariGlassSurface(
                    backdropScope = this@HikariBackdropHost,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(48.dp)
                        .then(utilityFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                        .then(
                            utilityNextFocusRequester?.let { nextRequester ->
                                Modifier.focusProperties {
                                    next = nextRequester
                                    down = nextRequester
                                }
                            } ?: Modifier,
                        )
                        .clickable(role = Role.Button, onClick = onUtilityRequested)
                        .semantics {
                            contentDescription = "Open quick access"
                            traversalIndex = 1f
                        },
                    shape = CircleShape,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "HK",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
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
        icon = navigationIcon(destination),
    )
}

private fun navigationIcon(destination: TopLevelDestination): ImageVector = ImageVector.Builder(
    name = destination.label,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.White)) {
        when (destination) {
            TopLevelDestination.Discover -> {
                moveTo(12f, 2f); lineTo(15f, 9f); lineTo(22f, 12f)
                lineTo(15f, 15f); lineTo(12f, 22f); lineTo(9f, 15f)
                lineTo(2f, 12f); lineTo(9f, 9f); close()
            }
            TopLevelDestination.Home -> {
                moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f)
                lineTo(19f, 11f); lineTo(19f, 21f); lineTo(5f, 21f)
                lineTo(5f, 11f); close()
            }
            TopLevelDestination.Library -> {
                moveTo(3f, 4f); lineTo(10f, 4f); lineTo(12f, 6f)
                lineTo(14f, 4f); lineTo(21f, 4f); lineTo(21f, 20f)
                lineTo(14f, 20f); lineTo(12f, 18f); lineTo(10f, 20f)
                lineTo(3f, 20f); close()
            }
        }
    }
}.build()
