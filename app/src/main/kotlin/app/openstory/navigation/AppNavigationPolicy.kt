package app.openstory.navigation

data class AppNavigationPolicy(
    val maxTotalEntries: Int = 32,
    val maxEntriesPerRoot: Int = 12,
) {
    init {
        require(maxTotalEntries >= AppFocusedDestination.entries.size)
        require(maxEntriesPerRoot >= 1)
    }
}
