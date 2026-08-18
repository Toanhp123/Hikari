package app.openstory.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay

@Composable
internal fun PersistentTopLevelNavDisplay(
    navigationState: AppNavigationState,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeRoute = navigationState.topLevelRoute
    val visitedTopLevelRoutes = remember(navigationState) {
        mutableStateListOf(navigationState.startRoute)
    }
    val routesToCompose = topLevelDestinations
        .map { destination -> destination.route }
        .filter { route -> route == activeRoute || route in visitedTopLevelRoutes }

    SideEffect {
        if (activeRoute !in visitedTopLevelRoutes) {
            visitedTopLevelRoutes += activeRoute
        }
    }

    Layout(
        modifier = modifier,
        content = {
            routesToCompose.forEach { route ->
                key(route) {
                    val active = route == activeRoute
                    NavDisplay(
                        entries = navigationState.decoratedEntriesFor(route, entryProvider),
                        onBack = {
                            if (active) {
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .layoutId(route)
                            .then(
                                if (active) {
                                    Modifier
                                } else {
                                    Modifier.clearAndSetSemantics {}
                                },
                            ),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val activeMeasurable = checkNotNull(
            measurables.firstOrNull { measurable -> measurable.layoutId == activeRoute },
        ) { "Missing retained top-level layer for $activeRoute" }
        val activePlaceable = activeMeasurable.measure(constraints)

        layout(activePlaceable.width, activePlaceable.height) {
            activePlaceable.placeRelative(0, 0)
        }
    }
}
