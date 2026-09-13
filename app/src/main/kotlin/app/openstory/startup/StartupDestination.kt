package app.openstory.startup

internal enum class StartupDestination {
    UNKNOWN,
    FIRST_RUN,
    APP_SHELL_HOME,
}

internal fun startupDestination(
    launchState: AppLaunchState,
    firstFrameReached: Boolean,
): StartupDestination = when (launchState) {
    AppLaunchState.Unknown -> StartupDestination.UNKNOWN
    AppLaunchState.FirstRun -> StartupDestination.FIRST_RUN
    AppLaunchState.Ready -> if (firstFrameReached) {
        StartupDestination.APP_SHELL_HOME
    } else {
        StartupDestination.UNKNOWN
    }
}
