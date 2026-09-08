package app.openstory.startup

internal sealed interface AppLaunchState {
    data object Unknown : AppLaunchState
    data object FirstRun : AppLaunchState
    data object Ready : AppLaunchState
}
