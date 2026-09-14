package app.openstory.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import app.openstory.common.navigation.RouteEntryId
import app.openstory.common.navigation.RouteLifecycle
import app.openstory.common.navigation.RouteLifecycleChange
import app.openstory.common.navigation.RouteLifecycleSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AppNavigationState(
    private val mangaBackStack: MutableList<NavKey>,
    private val homeBackStack: MutableList<NavKey>,
    private val lightNovelBackStack: MutableList<NavKey>,
    private val policy: AppNavigationPolicy = AppNavigationPolicy(),
    focusedDestinationState: MutableState<AppFocusedDestination> =
        mutableStateOf(AppFocusedDestination.HOME),
) : RouteLifecycleSource {
    private val _changes = MutableSharedFlow<RouteLifecycleChange>(
        extraBufferCapacity = policy.maxTotalEntries,
    )
    override val changes: Flow<RouteLifecycleChange> = _changes.asSharedFlow()

    var focusedDestination: AppFocusedDestination by focusedDestinationState
        private set

    val focusedBackStack: List<AppRoute>
        get() = mutableStackFor(focusedDestination).map { it as AppRoute }

    val totalEntryCount: Int
        get() = allStacks().sumOf(List<NavKey>::size)

    init {
        requireValidStackShape(mangaBackStack, AppFocusedDestination.MANGA)
        requireValidStackShape(homeBackStack, AppFocusedDestination.HOME)
        requireValidStackShape(lightNovelBackStack, AppFocusedDestination.LIGHT_NOVEL)
        sanitizeRestoredChildEntryIds()
        require(allStacks().all { it.size <= policy.maxEntriesPerRoot })
        require(totalEntryCount <= policy.maxTotalEntries)
    }

    fun stackFor(destination: AppFocusedDestination): List<AppRoute> =
        mutableStackFor(destination).map { it as AppRoute }

    fun select(destination: AppFocusedDestination): AppRootSelection {
        if (focusedDestination != destination) {
            val oldTop = mutableStackFor(focusedDestination).last() as AppRoute
            focusedDestination = destination
            val newTop = mutableStackFor(destination).last() as AppRoute
            emitLifecycle(oldTop.entryId, RouteLifecycle.RETAINED)
            emitLifecycle(newTop.entryId, RouteLifecycle.ACTIVE)
            return AppRootSelection.SELECTED
        }

        val stack = mutableStackFor(destination)
        return if (stack.size > 1) {
            val popped = stack.subList(1, stack.size).toList().map { it as AppRoute }
            stack.subList(1, stack.size).clear()
            popped.asReversed().forEach { route ->
                emitLifecycle(route.entryId, RouteLifecycle.RELEASED)
            }
            val root = stack.first() as AppRoute
            emitLifecycle(root.entryId, RouteLifecycle.ACTIVE)
            AppRootSelection.RESELECTED_AND_POPPED
        } else {
            AppRootSelection.RESELECTED
        }
    }

    fun push(route: AppRoute): Boolean {
        val routeEntryId = RouteEntryId.from(route.entryId)
        val stack = mutableStackFor(focusedDestination)
        val perRootEvictions = (stack.size + 1 - policy.maxEntriesPerRoot).coerceAtLeast(0)
        val focusedEligibleBeforePush = (stack.size - PRESERVED_BACK_CHAIN_ENTRY_COUNT).coerceAtLeast(0)
        val projectedTotal = totalEntryCount + 1 - perRootEvictions
        val globalEvictions = (projectedTotal - policy.maxTotalEntries).coerceAtLeast(0)
        val focusedSizeAfterPerRootTrim = stack.size - perRootEvictions
        val globalEligibleCount = AppFocusedDestination.entries.sumOf { destination ->
            val size = if (destination == focusedDestination) {
                focusedSizeAfterPerRootTrim
            } else {
                mutableStackFor(destination).size
            }
            (size - PRESERVED_BACK_CHAIN_ENTRY_COUNT).coerceAtLeast(0)
        }
        val pushRejected = !route.isChildRoute() ||
            routeEntryId in allEntryIds() ||
            policy.maxEntriesPerRoot <= 1 ||
            perRootEvictions > focusedEligibleBeforePush ||
            globalEvictions > globalEligibleCount
        if (pushRejected) return false

        val previousTop = stack.last() as AppRoute
        repeat(perRootEvictions) {
            releaseOldestIntermediate(stack)
        }

        var remainingGlobalEvictions = globalEvictions
        AppFocusedDestination.entries
            .filter { it != focusedDestination }
            .forEach { destination ->
                val otherStack = mutableStackFor(destination)
                while (remainingGlobalEvictions > 0 && otherStack.size > PRESERVED_BACK_CHAIN_ENTRY_COUNT) {
                    releaseOldestIntermediate(otherStack)
                    remainingGlobalEvictions--
                }
            }
        while (remainingGlobalEvictions > 0 && stack.size > PRESERVED_BACK_CHAIN_ENTRY_COUNT) {
            releaseOldestIntermediate(stack)
            remainingGlobalEvictions--
        }

        check(remainingGlobalEvictions == 0)
        stack += route
        emitLifecycle(previousTop.entryId, RouteLifecycle.RETAINED)
        emitLifecycle(route.entryId, RouteLifecycle.ACTIVE)
        return true
    }

    fun popFocusedRoute(): Boolean {
        val stack = mutableStackFor(focusedDestination)
        if (stack.size == 1) return false
        val popped = stack.removeAt(stack.lastIndex) as AppRoute
        val newTop = stack.last() as AppRoute
        emitLifecycle(popped.entryId, RouteLifecycle.RELEASED)
        emitLifecycle(newTop.entryId, RouteLifecycle.ACTIVE)
        return true
    }

    private fun releaseOldestIntermediate(stack: MutableList<NavKey>) {
        check(stack.size > PRESERVED_BACK_CHAIN_ENTRY_COUNT)
        val evicted = stack.removeAt(1) as AppRoute
        emitLifecycle(evicted.entryId, RouteLifecycle.RELEASED)
    }

    private fun emitLifecycle(entryId: String, lifecycle: RouteLifecycle) {
        check(_changes.tryEmit(RouteLifecycleChange(RouteEntryId.from(entryId), lifecycle))) {
            "Route lifecycle buffer exhausted while publishing $entryId -> $lifecycle"
        }
    }

    private fun allEntryIds(): Set<RouteEntryId> = buildSet {
        allStacks().forEach { stack ->
            stack.forEach { route -> add(RouteEntryId.from((route as AppRoute).entryId)) }
        }
    }

    private fun allStacks(): List<MutableList<NavKey>> =
        listOf(mangaBackStack, homeBackStack, lightNovelBackStack)

    private fun mutableStackFor(destination: AppFocusedDestination): MutableList<NavKey> =
        when (destination) {
            AppFocusedDestination.MANGA -> mangaBackStack
            AppFocusedDestination.HOME -> homeBackStack
            AppFocusedDestination.LIGHT_NOVEL -> lightNovelBackStack
        }

    private fun sanitizeRestoredChildEntryIds() {
        val rootIds = allStacks().map { stack ->
            RouteEntryId.from((stack.first() as AppRoute).entryId)
        }
        require(rootIds.toSet().size == rootIds.size) {
            "Root route entry ids must be unique"
        }

        val seen = rootIds.toMutableSet()
        val orderedDestinations = listOf(focusedDestination) +
            AppFocusedDestination.entries.filter { it != focusedDestination }
        orderedDestinations.forEach { destination ->
            val stack = mutableStackFor(destination)
            val iterator = stack.listIterator(1)
            while (iterator.hasNext()) {
                val route = iterator.next() as AppRoute
                val entryId = runCatching { RouteEntryId.from(route.entryId) }.getOrNull()
                if (entryId == null || !seen.add(entryId)) {
                    iterator.remove()
                }
            }
        }
    }

    private fun requireValidStackShape(
        stack: List<NavKey>,
        destination: AppFocusedDestination,
    ) {
        require(stack.isNotEmpty())
        require(stack.first().isRootFor(destination))
        require(stack.drop(1).all { (it as AppRoute).isChildRoute() })
        RouteEntryId.from((stack.first() as AppRoute).entryId)
    }
}

private const val PRESERVED_BACK_CHAIN_ENTRY_COUNT = 2

enum class AppRootSelection {
    SELECTED,
    RESELECTED,
    RESELECTED_AND_POPPED,
}

private fun AppRoute.isChildRoute(): Boolean = this is AppRoute.Story

private fun NavKey.isRootFor(destination: AppFocusedDestination): Boolean = when (destination) {
    AppFocusedDestination.MANGA -> this is AppRoute.Discover && media == AppMediaRoute.MANGA
    AppFocusedDestination.HOME -> this is AppRoute.Home
    AppFocusedDestination.LIGHT_NOVEL -> this is AppRoute.Discover && media == AppMediaRoute.LIGHT_NOVEL
}
