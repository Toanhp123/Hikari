package app.openstory.navigation

enum class TopLevelDestination(
    val route: AppRoute,
    val label: String,
) {
    Discover(AppRoute.Discover, "Discover"),
    Home(AppRoute.Home, "Home"),
    Library(AppRoute.Library, "Library"),
}

val topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.entries

fun AppRoute?.isTopLevel(): Boolean = topLevelDestinations.any { it.route == this }

fun shouldShowFloatingNavigation(route: AppRoute?): Boolean = route.isTopLevel()
