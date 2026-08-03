package app.openstory.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay

@Composable
fun OpenStoryNavDisplay(
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(AppRoute.Home)
    val currentRoute = backStack.lastOrNull()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                topLevelRoutes.forEach { route ->
                    val label = route.topLevelLabel()

                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = {
                            if (currentRoute != route) {
                                backStack.clear()
                                backStack.add(route)
                            }
                        },
                        icon = {
                            Text(label.take(1))
                        },
                        label = {
                            Text(label)
                        },
                    )
                }
            }
        },
    ) { contentPadding ->
        NavDisplay(
            modifier = Modifier.padding(contentPadding),
            backStack = backStack,
            onBack = {
                backStack.removeLastOrNull()
            },
            entryProvider = entryProvider {
                entry<AppRoute.Home> {
                    PlaceholderDestination("Home")
                }
                entry<AppRoute.Library> {
                    PlaceholderDestination("Library")
                }
                entry<AppRoute.Plugins> {
                    PlaceholderDestination("Plugins")
                }
                entry<AppRoute.Settings> {
                    PlaceholderDestination("Settings")
                }
                entry<AppRoute.Story> {
                    PlaceholderDestination("Story")
                }
                entry<AppRoute.Reader> {
                    PlaceholderDestination("Reader")
                }
            },
        )
    }
}

private fun AppRoute.topLevelLabel(): String = when (this) {
    AppRoute.Home -> "Home"
    AppRoute.Library -> "Library"
    AppRoute.Plugins -> "Plugins"
    else -> error("Route $this is not a top-level destination")
}

@Composable
private fun PlaceholderDestination(
    title: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = title)
    }
}
