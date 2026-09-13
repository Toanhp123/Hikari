package app.openstory.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey

class AppNavigationState(
    private val mangaBackStack: MutableList<NavKey>,
    private val homeBackStack: MutableList<NavKey>,
    private val lightNovelBackStack: MutableList<NavKey>,
    private val policy: AppNavigationPolicy = AppNavigationPolicy(),
    focusedDestinationState: MutableState<AppFocusedDestination> =
        mutableStateOf(AppFocusedDestination.HOME),
) {
    var focusedDestination: AppFocusedDestination by focusedDestinationState
        private set

    val focusedBackStack: List<AppRoute>
        get() = mutableStackFor(focusedDestination).map { it as AppRoute }

    val totalEntryCount: Int
        get() = mangaBackStack.size + homeBackStack.size + lightNovelBackStack.size

    init {
        requireValidStack(mangaBackStack, AppFocusedDestination.MANGA)
        requireValidStack(homeBackStack, AppFocusedDestination.HOME)
        requireValidStack(lightNovelBackStack, AppFocusedDestination.LIGHT_NOVEL)
        require(totalEntryCount <= policy.maxTotalEntries)
    }

    fun stackFor(destination: AppFocusedDestination): List<AppRoute> =
        mutableStackFor(destination).map { it as AppRoute }

    fun select(destination: AppFocusedDestination): AppRootSelection {
        if (focusedDestination != destination) {
            focusedDestination = destination
            return AppRootSelection.SELECTED
        }

        val stack = mutableStackFor(destination)
        return if (stack.size > 1) {
            stack.subList(1, stack.size).clear()
            AppRootSelection.RESELECTED_AND_POPPED
        } else {
            AppRootSelection.RESELECTED
        }
    }

    fun push(route: AppRoute): Boolean {
        if (!route.isRootFor(focusedDestination)) return false
        RouteEntryId.from(route.entryId)

        val stack = mutableStackFor(focusedDestination)
        if (policy.maxEntriesPerRoot == 1) return false
        val needsPerRootEviction = stack.size >= policy.maxEntriesPerRoot
        val projectedTotal = totalEntryCount + 1 - if (needsPerRootEviction) 1 else 0
        if (projectedTotal > policy.maxTotalEntries) return false

        if (needsPerRootEviction) stack.removeAt(1)
        stack += route
        return true
    }

    fun popFocusedRoute(): Boolean {
        val stack = mutableStackFor(focusedDestination)
        if (stack.size == 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    private fun mutableStackFor(destination: AppFocusedDestination): MutableList<NavKey> =
        when (destination) {
            AppFocusedDestination.MANGA -> mangaBackStack
            AppFocusedDestination.HOME -> homeBackStack
            AppFocusedDestination.LIGHT_NOVEL -> lightNovelBackStack
        }

    private fun requireValidStack(
        stack: List<NavKey>,
        destination: AppFocusedDestination,
    ) {
        require(stack.isNotEmpty())
        require(stack.first().isRootFor(destination))
        require(stack.all { it.isRootFor(destination) })
        require(stack.size <= policy.maxEntriesPerRoot)
        stack.forEach { route -> RouteEntryId.from((route as AppRoute).entryId) }
    }
}

enum class AppRootSelection {
    SELECTED,
    RESELECTED,
    RESELECTED_AND_POPPED,
}

private fun NavKey.isRootFor(destination: AppFocusedDestination): Boolean = when (destination) {
    AppFocusedDestination.MANGA -> this is AppRoute.Discover && media == AppMediaRoute.MANGA
    AppFocusedDestination.HOME -> this is AppRoute.Home
    AppFocusedDestination.LIGHT_NOVEL -> this is AppRoute.Discover && media == AppMediaRoute.LIGHT_NOVEL
}
