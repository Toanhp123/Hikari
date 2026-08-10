package app.openstory.navigation

enum class TopLevelDestination(
    val route: AppRoute,
    val label: String,
) {
    Home(AppRoute.Home, "Home"),
    Library(AppRoute.Library, "Library"),
    Plugins(AppRoute.Plugins, "Plugins"),
}

val topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.entries
