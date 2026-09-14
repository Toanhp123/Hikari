package app.openstory.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

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
    Surface(
        modifier = modifier
            .widthIn(max = APP_NAV_MAX_WIDTH)
            .fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = APP_NAV_SHADOW_ELEVATION,
        tonalElevation = APP_NAV_TONAL_ELEVATION,
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(APP_NAV_INNER_PADDING),
        ) {
            AppFocusedDestination.entries.forEach { destination ->
                AppDestinationItem(
                    destination = destination,
                    selected = destination == selected,
                    onSelected = onSelected,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AppDestinationItem(
    destination: AppFocusedDestination,
    selected: Boolean,
    onSelected: (AppFocusedDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.selectable(
            selected = selected,
            role = Role.Tab,
            onClick = { onSelected(destination) },
        ),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = destination.label,
            modifier = Modifier.padding(
                horizontal = APP_NAV_ITEM_HORIZONTAL_PADDING,
                vertical = APP_NAV_ITEM_VERTICAL_PADDING,
            ),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
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
private val APP_NAV_MAX_WIDTH = 520.dp
private val APP_NAV_SHADOW_ELEVATION = 10.dp
private val APP_NAV_TONAL_ELEVATION = 3.dp
private val APP_NAV_INNER_PADDING = 6.dp
private val APP_NAV_ITEM_HORIZONTAL_PADDING = 12.dp
private val APP_NAV_ITEM_VERTICAL_PADDING = 14.dp
