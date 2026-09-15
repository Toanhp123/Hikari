package app.openstory.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import app.openstory.designsystem.navigation.HikariFloatingDestinationNav
import app.openstory.designsystem.navigation.HikariFloatingDestinationNavItem

@Composable
internal fun rememberAppNavigationState(): AppNavigationState {
    val mangaBackStack = rememberNavBackStack(MANGA_ROOT)
    val homeBackStack = rememberNavBackStack(HOME_ROOT)
    val lightNovelBackStack = rememberNavBackStack(LIGHT_NOVEL_ROOT)
    val focusedDestinationState = rememberSaveable {
        mutableStateOf(AppFocusedDestination.HOME)
    }
    return remember(
        mangaBackStack,
        homeBackStack,
        lightNovelBackStack,
        focusedDestinationState,
    ) {
        AppNavigationState(
            mangaBackStack = mangaBackStack,
            homeBackStack = homeBackStack,
            lightNovelBackStack = lightNovelBackStack,
            focusedDestinationState = focusedDestinationState,
        )
    }
}

@Composable
internal fun AppNavHost(
    navigationState: AppNavigationState,
    routeContent: @Composable (AppRoute) -> Unit,
) {
    val rootStateHolder = rememberSaveableStateHolder()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            rootStateHolder.SaveableStateProvider(navigationState.focusedDestination.name) {
                AppRootDisplay(
                    navigationState = navigationState,
                    routeContent = routeContent,
                )
            }
        }
        AppDestinationBar(
            selected = navigationState.focusedDestination,
            onSelected = navigationState::select,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(
                    horizontal = APP_NAV_HORIZONTAL_MARGIN,
                    vertical = APP_NAV_VERTICAL_MARGIN,
                ),
        )
    }
}

@Composable
private fun AppDestinationBar(
    selected: AppFocusedDestination,
    onSelected: (AppFocusedDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    HikariFloatingDestinationNav(modifier = modifier) {
        AppFocusedDestination.entries.forEach { destination ->
            HikariFloatingDestinationNavItem(
                label = destination.label,
                selected = destination == selected,
                onClick = { onSelected(destination) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val AppFocusedDestination.label: String
    get() = when (this) {
        AppFocusedDestination.MANGA -> "Manga"
        AppFocusedDestination.HOME -> "Home"
        AppFocusedDestination.LIGHT_NOVEL -> "Light Novel"
    }

private val MANGA_ROOT: NavKey = AppRoute.Discover("manga-root", AppMediaRoute.MANGA)
private val HOME_ROOT: NavKey = AppRoute.Home("home-root")
private val LIGHT_NOVEL_ROOT: NavKey =
    AppRoute.Discover("light-novel-root", AppMediaRoute.LIGHT_NOVEL)

private val APP_NAV_HORIZONTAL_MARGIN = 20.dp
private val APP_NAV_VERTICAL_MARGIN = 16.dp
