package app.openstory.common.navigation

data class RouteLifecycleChange(
    val entryId: RouteEntryId,
    val lifecycle: RouteLifecycle,
)
